package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.dto.cockpit.CockpitFilter;
import com.dawnbread.attendance.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sales cockpit queries — PLAIN SQL (native queries), on purpose.
 *
 * WHY NOT JPQL: the first version used dynamically built JPQL with long,
 * repeated SUM(CASE WHEN ... COALESCE ...) expressions. Hibernate 6 parses
 * JPQL with ANTLR, and for these statements the parser's prediction cache
 * needed 100-200 MB of heap per query (measured: countVisitedAssignedShops
 * 203 MB, shopsAboveReturnPercent 116 MB) before any SQL even ran. On
 * Render's 512 MB container (128 MB default heap) that caused
 * OutOfMemoryError on the first cockpit page load — the 2026-10-27
 * production incident. Native SQL is handed straight to Postgres: no
 * parser, no prediction cache.
 *
 * TENANT SAFETY: Hibernate's tenantFilter does NOT apply to native SQL, so
 * every table read here carries an explicit "tenant_id = :tenantId", taken
 * from TenantContext (set for every request by SecurityInterceptor). There
 * is no way to call these without a tenant: tenantId() throws.
 *
 * Every query is an aggregate (SUM / COUNT / GROUP BY / EXISTS) or a small
 * bounded list; Java only receives grouped rows. Optional filters are added
 * to the SQL text only when set (no null parameters reach Postgres). Every
 * query has a 20-second timeout so a slow query can never hold a pool
 * connection for minutes.
 */
@Repository
public class SalesCockpitQueries {

    static final int QUERY_TIMEOUT_MS = 20_000;

    public static final String SALE_VALUE =
            "COALESCE(SUM(CASE WHEN si.transaction_type = 'SALE' THEN si.total_price ELSE 0 END), 0)";
    public static final String RETURN_VALUE =
            "COALESCE(SUM(CASE WHEN si.transaction_type = 'RETURN' THEN si.total_price ELSE 0 END), 0)";
    public static final String SOLD_UNITS =
            "COALESCE(SUM(CASE WHEN si.transaction_type = 'SALE' THEN si.quantity ELSE 0 END), 0)";
    public static final String RETURN_UNITS =
            "COALESCE(SUM(CASE WHEN si.transaction_type = 'RETURN' THEN si.quantity ELSE 0 END), 0)";
    public static final String RBE_UNITS =
            "COALESCE(SUM(CASE WHEN si.transaction_type = 'RETURN_BEFORE_EXPIRY' THEN si.quantity ELSE 0 END), 0)";

    private static final String ROLE_EXPR = "COALESCE(sr.agent_role_at_sale, a.role)";

    private static final String SALES_FROM =
            " FROM sale_items si JOIN sales_records sr ON sr.id = si.sales_record_id JOIN agent a ON a.id = sr.agent_id "
                    + "LEFT JOIN customer_shops cs ON cs.id = sr.customer_shop_id LEFT JOIN areas ar ON ar.id = cs.area_id ";

    @PersistenceContext
    private EntityManager em;

    // ------------------------------------------------------------------
    // Tiny SQL builder: appends a clause + its parameter only when used.
    // ------------------------------------------------------------------
    static final class Sql {
        private final StringBuilder sb = new StringBuilder();
        private final Map<String, Object> params = new LinkedHashMap<>();

        Sql(String start) {
            sb.append(start);
        }

        Sql add(String text) {
            sb.append(' ').append(text);
            return this;
        }

        Sql and(String clause) {
            sb.append(" AND ").append(clause);
            return this;
        }

        Sql and(String clause, String name, Object value) {
            sb.append(" AND ").append(clause);
            params.put(name, value);
            return this;
        }

        Sql param(String name, Object value) {
            params.put(name, value);
            return this;
        }

        Query build(EntityManager em) {
            Query q = em.createNativeQuery(sb.toString());
            params.forEach(q::setParameter);
            q.setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT_MS);
            return q;
        }
    }

    /** The caller's tenant — mandatory for every query here. */
    static Long tenantId() {
        Long t = TenantContext.getTenantId();
        if (t == null) {
            throw new IllegalStateException("Cockpit queries need a tenant context");
        }
        return t;
    }

    /** Roles a salesman roster covers for this segment (ALL = both salesman roles). */
    public static List<String> salesmanRoles(CockpitFilter f) {
        return f.role() != null ? List.of(f.role()) : List.of(CockpitFilter.ROLE_LOCAL, CockpitFilter.ROLE_LMT);
    }

    /** Native results come back as java.sql.Date/Time; the service works with java.time. */
    @SuppressWarnings("unchecked")
    static List<Object[]> rows(Query q) {
        List<Object> raw = q.getResultList();
        List<Object[]> out = new ArrayList<>(raw.size());
        for (Object r : raw) {
            Object[] row = r instanceof Object[] arr ? arr : new Object[] { r };
            for (int i = 0; i < row.length; i++) {
                if (row[i] instanceof java.sql.Date d) row[i] = d.toLocalDate();
                else if (row[i] instanceof java.sql.Time t) row[i] = t.toLocalTime();
                else if (row[i] instanceof java.sql.Timestamp ts) row[i] = ts.toLocalDateTime();
            }
            out.add(row);
        }
        return out;
    }

    static long single(Query q) {
        return lng(q.getSingleResult());
    }

    // ------------------------------------------------------------------
    // Sales money / units
    // ------------------------------------------------------------------

    /** The sales base WHERE: tenant + date range + segment + area + salesman. */
    private Sql sales(String select, String extraJoins, CockpitFilter f, LocalDate from, LocalDate to) {
        Sql q = new Sql(select + SALES_FROM + (extraJoins == null ? "" : extraJoins)
                + " WHERE si.tenant_id = :tenantId AND sr.tenant_id = :tenantId AND sr.sale_date BETWEEN :from AND :to");
        q.param("tenantId", tenantId()).param("from", from).param("to", to);
        if (f.role() != null) q.and(ROLE_EXPR + " = :role", "role", f.role());
        if (f.areaId() != null) q.and("ar.id = :areaId", "areaId", f.areaId());
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return q;
    }

    /** Per-day: date, saleValue, returnValue, soldUnits, returnUnits, rbeUnits. */
    public List<Object[]> dailySales(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(sales("SELECT sr.sale_date, " + SALE_VALUE + ", " + RETURN_VALUE + ", " + SOLD_UNITS + ", "
                + RETURN_UNITS + ", " + RBE_UNITS, null, f, from, to)
                .add("GROUP BY sr.sale_date ORDER BY sr.sale_date").build(em));
    }

    /** Per-segment (role at sale time): role, saleValue, returnValue, soldUnits. */
    public List<Object[]> salesBySegment(CockpitFilter f) {
        return rows(sales("SELECT " + ROLE_EXPR + ", " + SALE_VALUE + ", " + RETURN_VALUE + ", " + SOLD_UNITS,
                null, f, f.startDate(), f.endDate())
                .add("GROUP BY " + ROLE_EXPR).build(em));
    }

    /** Per-salesman: id, name, role, saleValue, returnValue, soldUnits, vouchers. */
    public List<Object[]> salesByAgent(CockpitFilter f) {
        return rows(sales("SELECT a.id, a.name, a.role, " + SALE_VALUE + ", " + RETURN_VALUE + ", " + SOLD_UNITS
                + ", COUNT(DISTINCT sr.id)", null, f, f.startDate(), f.endDate())
                .add("GROUP BY a.id, a.name, a.role").build(em));
    }

    /** Per-area (null area = vouchers without a shop): id, name, tse, srTse, asm, saleValue, returnValue, soldUnits. */
    public List<Object[]> salesByArea(CockpitFilter f) {
        return rows(sales("SELECT ar.id, ar.name, t.name, st.name, am.name, " + SALE_VALUE + ", " + RETURN_VALUE
                        + ", " + SOLD_UNITS,
                "LEFT JOIN hierarchy_persons t ON t.id = ar.tse_id LEFT JOIN hierarchy_persons st ON st.id = ar.sr_tse_id "
                        + "LEFT JOIN hierarchy_persons am ON am.id = ar.asm_id", f, f.startDate(), f.endDate())
                .add("GROUP BY ar.id, ar.name, t.name, st.name, am.name").build(em));
    }

    /** Top products by sold value: id, name, soldUnits, saleValue, returnValue. */
    public List<Object[]> topProducts(CockpitFilter f, int limit) {
        Query q = sales("SELECT p.id, p.name, " + SOLD_UNITS + ", " + SALE_VALUE + ", " + RETURN_VALUE,
                "JOIN products p ON p.id = si.product_id", f, f.startDate(), f.endDate())
                .add("GROUP BY p.id, p.name ORDER BY 4 DESC, 2").build(em);
        q.setMaxResults(limit);
        return rows(q);
    }

    /** Shops whose return value / sale value is above pct: id, name, saleValue, returnValue (worst first). */
    public List<Object[]> shopsAboveReturnPercent(CockpitFilter f, double pct) {
        return rows(sales("SELECT cs.id, cs.shop_name, " + SALE_VALUE + ", " + RETURN_VALUE, null, f,
                f.startDate(), f.endDate())
                .and("cs.id IS NOT NULL")
                .add("GROUP BY cs.id, cs.shop_name HAVING " + SALE_VALUE + " > 0 AND " + RETURN_VALUE
                        + " * 100.0 > :pct * " + SALE_VALUE)
                .param("pct", pct)
                .add("ORDER BY " + RETURN_VALUE + " / " + SALE_VALUE + " DESC").build(em));
    }

    /** Products whose return value / sale value is above pct: id, name, saleValue, returnValue (worst first). */
    public List<Object[]> productsAboveReturnPercent(CockpitFilter f, double pct) {
        return rows(sales("SELECT p.id, p.name, " + SALE_VALUE + ", " + RETURN_VALUE,
                "JOIN products p ON p.id = si.product_id", f, f.startDate(), f.endDate())
                .add("GROUP BY p.id, p.name HAVING " + SALE_VALUE + " > 0 AND " + RETURN_VALUE
                        + " * 100.0 > :pct * " + SALE_VALUE)
                .param("pct", pct)
                .add("ORDER BY " + RETURN_VALUE + " / " + SALE_VALUE + " DESC").build(em));
    }

    // ------------------------------------------------------------------
    // Drill-down: vouchers with totals, paginated in the database
    // ------------------------------------------------------------------

    private Sql voucherFilter(String select, CockpitFilter f, Long productId, boolean noArea) {
        Sql q = sales(select, null, f, f.startDate(), f.endDate());
        if (noArea) q.and("ar.id IS NULL");
        if (productId != null) {
            q.and("EXISTS (SELECT 1 FROM sale_items x WHERE x.sales_record_id = sr.id AND x.tenant_id = :tenantId "
                    + "AND x.product_id = :productId)", "productId", productId);
        }
        return q;
    }

    /** id, date, time, agent, role, shop, area, saleValue, returnValue, soldUnits, rbeUnits. */
    public List<Object[]> vouchers(CockpitFilter f, Long productId, boolean noArea, int page, int size) {
        Query q = voucherFilter("SELECT sr.id, sr.sale_date, sr.sale_time, a.name, " + ROLE_EXPR
                + ", COALESCE(cs.shop_name, sr.store_name), ar.name, " + SALE_VALUE + ", " + RETURN_VALUE + ", "
                + SOLD_UNITS + ", " + RBE_UNITS, f, productId, noArea)
                .add("GROUP BY sr.id, sr.sale_date, sr.sale_time, a.name, sr.agent_role_at_sale, a.role, cs.shop_name, "
                        + "sr.store_name, ar.name ORDER BY sr.sale_date DESC, sr.sale_time DESC, sr.id DESC")
                .build(em);
        q.setFirstResult(page * size);
        q.setMaxResults(size);
        return rows(q);
    }

    public long countVouchers(CockpitFilter f, Long productId, boolean noArea) {
        return single(voucherFilter("SELECT COUNT(DISTINCT sr.id)", f, productId, noArea).build(em));
    }

    // ------------------------------------------------------------------
    // Location / scan flags
    // ------------------------------------------------------------------

    private Sql scans(String select, CockpitFilter f, LocalDate from, LocalDate to) {
        Sql q = new Sql(select + " FROM shop_visit_scan s JOIN agent a ON a.id = s.agent_id "
                + "LEFT JOIN customer_shops cs ON cs.id = s.customer_shop_id LEFT JOIN areas ar ON ar.id = cs.area_id "
                + "WHERE s.tenant_id = :tenantId AND s.scan_date BETWEEN :from AND :to");
        q.param("tenantId", tenantId()).param("from", from).param("to", to);
        q.and("a.role IN (:roles)", "roles", salesmanRoles(f));
        if (f.areaId() != null) q.and("ar.id = :areaId", "areaId", f.areaId());
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return q;
    }

    /** Per-day count of failed QR scans because the salesman was outside the shop's area. */
    public List<Object[]> dailyOffLocationAttempts(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(scans("SELECT s.scan_date, COUNT(*)", f, from, to)
                .and("s.visit_status = 'OUTSIDE_GEOFENCE'")
                .add("GROUP BY s.scan_date").build(em));
    }

    /** Off-location attempts per salesman in a range: name, count (most first). */
    public List<Object[]> offLocationAttemptsByAgent(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(scans("SELECT a.name, COUNT(*)", f, from, to)
                .and("s.visit_status = 'OUTSIDE_GEOFENCE'")
                .add("GROUP BY a.name ORDER BY 2 DESC").build(em));
    }

    private Sql shopVouchers(String select, CockpitFilter f, LocalDate from, LocalDate to) {
        Sql q = new Sql(select + " FROM sales_records sr JOIN agent a ON a.id = sr.agent_id "
                + "JOIN customer_shops cs ON cs.id = sr.customer_shop_id LEFT JOIN areas ar ON ar.id = cs.area_id "
                + "WHERE sr.tenant_id = :tenantId AND sr.sale_date BETWEEN :from AND :to");
        q.param("tenantId", tenantId()).param("from", from).param("to", to);
        if (f.role() != null) q.and(ROLE_EXPR + " = :role", "role", f.role());
        if (f.areaId() != null) q.and("ar.id = :areaId", "areaId", f.areaId());
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return q;
    }

    /** Per-day count of shop vouchers saved with no distance check (shop has no location set up — LMT only). */
    public List<Object[]> dailyLocationNotChecked(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(shopVouchers("SELECT sr.sale_date, COUNT(*)", f, from, to)
                .and("sr.distance_from_shop_meters IS NULL")
                .add("GROUP BY sr.sale_date").build(em));
    }

    private static final String NO_SCAN = "NOT EXISTS (SELECT 1 FROM shop_visit_scan s WHERE s.tenant_id = :tenantId "
            + "AND s.agent_id = a.id AND s.customer_shop_id = cs.id AND s.scan_date = sr.sale_date "
            + "AND s.visit_status = 'SUCCESS')";

    /** Per-day count of vouchers with no successful QR scan by that salesman at that shop that day (same rule as QR Scanned Shops). */
    public List<Object[]> dailyVouchersWithoutScan(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(shopVouchers("SELECT sr.sale_date, COUNT(*)", f, from, to).and(NO_SCAN)
                .add("GROUP BY sr.sale_date").build(em));
    }

    /** Vouchers without scan per salesman in a range: name, count. */
    public List<Object[]> vouchersWithoutScanByAgent(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(shopVouchers("SELECT a.name, COUNT(*)", f, from, to).and(NO_SCAN)
                .add("GROUP BY a.name ORDER BY 2 DESC").build(em));
    }

    /** Per-day count of shop feedback entries (visit without a sale). */
    public List<Object[]> dailyFeedback(CockpitFilter f, LocalDate from, LocalDate to) {
        Sql q = new Sql("SELECT fb.visit_date, COUNT(*) FROM shop_feedback fb JOIN agent a ON a.id = fb.agent_id "
                + "JOIN customer_shops cs ON cs.id = fb.customer_shop_id LEFT JOIN areas ar ON ar.id = cs.area_id "
                + "WHERE fb.tenant_id = :tenantId AND fb.visit_date BETWEEN :from AND :to");
        q.param("tenantId", tenantId()).param("from", from).param("to", to);
        q.and("a.role IN (:roles)", "roles", salesmanRoles(f));
        if (f.areaId() != null) q.and("ar.id = :areaId", "areaId", f.areaId());
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return rows(q.add("GROUP BY fb.visit_date").build(em));
    }

    // ------------------------------------------------------------------
    // LMT stock (per salesman, so the area filter does not apply)
    // ------------------------------------------------------------------

    private static final String MISSING_UNITS = "COALESCE(SUM(CASE WHEN i.missing_qty > 0 THEN i.missing_qty ELSE 0 END), 0)";

    private Sql lmtStock(String select, CockpitFilter f, LocalDate from, LocalDate to) {
        Sql q = new Sql(select + " FROM lmt_daily_stock_item i JOIN lmt_daily_stock d ON d.id = i.lmt_daily_stock_id "
                + "JOIN agent a ON a.id = d.agent_id WHERE i.tenant_id = :tenantId AND d.tenant_id = :tenantId "
                + "AND d.stock_date BETWEEN :from AND :to AND d.status = 'RECONCILED'");
        q.param("tenantId", tenantId()).param("from", from).param("to", to);
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return q;
    }

    /** Per-day missing units on reconciled LMT stock (only shortages, never negative over-counts). */
    public List<Object[]> dailyLmtMissing(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(lmtStock("SELECT d.stock_date, " + MISSING_UNITS, f, from, to)
                .add("GROUP BY d.stock_date").build(em));
    }

    /** Missing units per LMT salesman in a range: name, missing (most first, only > 0). */
    public List<Object[]> lmtMissingByAgent(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(lmtStock("SELECT a.name, " + MISSING_UNITS, f, from, to)
                .add("GROUP BY a.name HAVING " + MISSING_UNITS + " > 0 ORDER BY 2 DESC").build(em));
    }

    /** Stock days still OPEN (not reconciled) per LMT salesman, for days strictly before :before: name, days. */
    public List<Object[]> unreconciledByAgent(CockpitFilter f, LocalDate from, LocalDate before) {
        Sql q = new Sql("SELECT a.name, COUNT(*) FROM lmt_daily_stock d JOIN agent a ON a.id = d.agent_id "
                + "WHERE d.tenant_id = :tenantId AND d.stock_date >= :from AND d.stock_date < :before AND d.status = 'OPEN'");
        q.param("tenantId", tenantId()).param("from", from).param("before", before);
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return rows(q.add("GROUP BY a.name ORDER BY 2 DESC").build(em));
    }

    // ------------------------------------------------------------------
    // Shop coverage & salesman activity (same "visited" rule as QR Scanned
    // Shops: a SUCCESS scan or a feedback entry by the ASSIGNED salesman).
    // ------------------------------------------------------------------

    /** Visits (successful scan or feedback) per (date, shop, salesman) in a date range. */
    private static final String VISITS =
            "SELECT s.scan_date AS d, s.customer_shop_id AS shop_id, s.agent_id AS agent_id FROM shop_visit_scan s "
                    + "WHERE s.tenant_id = :tenantId AND s.visit_status = 'SUCCESS' AND s.scan_date BETWEEN :from AND :to "
                    + "UNION "
                    + "SELECT fb.visit_date, fb.customer_shop_id, fb.agent_id FROM shop_feedback fb "
                    + "WHERE fb.tenant_id = :tenantId AND fb.visit_date BETWEEN :from AND :to";

    private void assignedFilters(Sql q, CockpitFilter f) {
        q.and("cs.tenant_id = :tenantId AND cs.is_active = true AND ag.role IN (:roles)", "roles", salesmanRoles(f));
        if (f.areaId() != null) q.and("cs.area_id = :areaId", "areaId", f.areaId());
        if (f.agentId() != null) q.and("ag.id = :agentId", "agentId", f.agentId());
    }

    public long countAssignedShops(CockpitFilter f) {
        Sql q = new Sql("SELECT COUNT(*) FROM customer_shops cs JOIN agent ag ON ag.id = cs.assigned_agent_id WHERE 1 = 1");
        q.param("tenantId", tenantId());
        assignedFilters(q, f);
        return single(q.build(em));
    }

    /** Per day: how many assigned shops were visited by their assigned salesman. One query for the whole range. */
    public List<Object[]> visitedAssignedShopsByDay(CockpitFilter f, LocalDate from, LocalDate to) {
        Sql q = new Sql("SELECT v.d, COUNT(DISTINCT cs.id) FROM (" + VISITS + ") v "
                + "JOIN customer_shops cs ON cs.id = v.shop_id AND cs.assigned_agent_id = v.agent_id "
                + "JOIN agent ag ON ag.id = cs.assigned_agent_id WHERE 1 = 1");
        q.param("tenantId", tenantId()).param("from", from).param("to", to);
        assignedFilters(q, f);
        return rows(q.add("GROUP BY v.d").build(em));
    }

    /** Assigned shops whose assigned salesman has no visit AFTER cutoff (last visit on/before cutoff, or never). */
    private static final String NOT_VISITED_SINCE =
            "NOT EXISTS (SELECT 1 FROM shop_visit_scan s WHERE s.tenant_id = :tenantId AND s.customer_shop_id = cs.id "
                    + "AND s.agent_id = ag.id AND s.scan_date > :cutoff AND s.visit_status = 'SUCCESS') "
                    + "AND NOT EXISTS (SELECT 1 FROM shop_feedback fb WHERE fb.tenant_id = :tenantId "
                    + "AND fb.customer_shop_id = cs.id AND fb.agent_id = ag.id AND fb.visit_date > :cutoff)";

    public long countShopsNotVisitedSince(CockpitFilter f, LocalDate cutoff) {
        Sql q = new Sql("SELECT COUNT(*) FROM customer_shops cs JOIN agent ag ON ag.id = cs.assigned_agent_id WHERE 1 = 1");
        q.param("tenantId", tenantId()).param("cutoff", cutoff);
        assignedFilters(q, f);
        return single(q.and(NOT_VISITED_SINCE).build(em));
    }

    /** Same shops, longest-unvisited first: shopName, salesman, lastVisit (null = never). */
    public List<Object[]> shopsNotVisitedSince(CockpitFilter f, LocalDate cutoff, int limit) {
        Sql q = new Sql("SELECT cs.shop_name, ag.name, lv.last_d FROM customer_shops cs "
                + "JOIN agent ag ON ag.id = cs.assigned_agent_id "
                + "LEFT JOIN (SELECT x.shop_id, x.agent_id, MAX(x.d) AS last_d FROM ("
                + "  SELECT s.customer_shop_id AS shop_id, s.agent_id AS agent_id, s.scan_date AS d FROM shop_visit_scan s "
                + "  WHERE s.tenant_id = :tenantId AND s.visit_status = 'SUCCESS' AND s.scan_date <= :cutoff "
                + "  UNION ALL "
                + "  SELECT fb.customer_shop_id, fb.agent_id, fb.visit_date FROM shop_feedback fb "
                + "  WHERE fb.tenant_id = :tenantId AND fb.visit_date <= :cutoff"
                + ") x GROUP BY x.shop_id, x.agent_id) lv ON lv.shop_id = cs.id AND lv.agent_id = ag.id WHERE 1 = 1");
        q.param("tenantId", tenantId()).param("cutoff", cutoff);
        assignedFilters(q, f);
        Query query = q.and(NOT_VISITED_SINCE).add("ORDER BY lv.last_d ASC NULLS FIRST, cs.shop_name").build(em);
        query.setMaxResults(limit);
        return rows(query);
    }

    private void salesmenFilters(Sql q, CockpitFilter f) {
        q.and("a.tenant_id = :tenantId AND a.is_active = true AND a.role IN (:roles)", "roles", salesmanRoles(f));
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        if (f.areaId() != null) {
            q.and("EXISTS (SELECT 1 FROM customer_shops c2 WHERE c2.tenant_id = :tenantId AND c2.assigned_agent_id = a.id "
                    + "AND c2.area_id = :areaId)", "areaId", f.areaId());
        }
    }

    /** Any activity (successful scan, feedback or voucher) per (date, salesman). */
    private static final String ACTIVITY =
            "SELECT s.scan_date AS d, s.agent_id AS agent_id FROM shop_visit_scan s WHERE s.tenant_id = :tenantId "
                    + "AND s.visit_status = 'SUCCESS' AND s.scan_date BETWEEN :from AND :to "
                    + "UNION SELECT fb.visit_date, fb.agent_id FROM shop_feedback fb WHERE fb.tenant_id = :tenantId "
                    + "AND fb.visit_date BETWEEN :from AND :to "
                    + "UNION SELECT v.sale_date, v.agent_id FROM sales_records v WHERE v.tenant_id = :tenantId "
                    + "AND v.sale_date BETWEEN :from AND :to AND v.agent_id IS NOT NULL";

    public long countSalesmen(CockpitFilter f) {
        Sql q = new Sql("SELECT COUNT(*) FROM agent a WHERE 1 = 1");
        q.param("tenantId", tenantId());
        salesmenFilters(q, f);
        return single(q.build(em));
    }

    /** Per day: salesmen with at least one visit (scan, feedback or voucher). One query for the whole range. */
    public List<Object[]> activeSalesmenByDay(CockpitFilter f, LocalDate from, LocalDate to) {
        Sql q = new Sql("SELECT act.d, COUNT(DISTINCT a.id) FROM (" + ACTIVITY + ") act JOIN agent a ON a.id = act.agent_id WHERE 1 = 1");
        q.param("tenantId", tenantId()).param("from", from).param("to", to);
        salesmenFilters(q, f);
        return rows(q.add("GROUP BY act.d").build(em));
    }

    /** Salesmen with NO visit that day: id, name, role. Small roster; working days are checked by the caller. */
    public List<Object[]> salesmenWithoutVisit(CockpitFilter f, LocalDate day) {
        Sql q = new Sql("SELECT a.id, a.name, a.role FROM agent a WHERE NOT EXISTS (SELECT 1 FROM (" + ACTIVITY
                + ") act WHERE act.agent_id = a.id)");
        q.param("tenantId", tenantId()).param("from", day).param("to", day);
        salesmenFilters(q, f);
        return rows(q.add("ORDER BY a.name").build(em));
    }

    /** Working days for a handful of agents (JSON column, so read through JPA's mapping; tiny query). */
    @SuppressWarnings("unchecked")
    public Map<Long, List<String>> workingDays(List<Long> agentIds) {
        Map<Long, List<String>> out = new LinkedHashMap<>();
        if (agentIds.isEmpty()) return out;
        List<Object[]> rs = em.createQuery("SELECT a.id, a.workingDays FROM Agent a WHERE a.id IN :ids")
                .setParameter("ids", agentIds).getResultList();
        for (Object[] r : rs) out.put((Long) r[0], (List<String>) r[1]);
        return out;
    }

    // ------------------------------------------------------------------
    // Filter dropdowns
    // ------------------------------------------------------------------

    public List<Object[]> activeAreas() {
        Sql q = new Sql("SELECT ar.id, ar.name FROM areas ar WHERE ar.tenant_id = :tenantId AND ar.is_active = true ORDER BY ar.name");
        return rows(q.param("tenantId", tenantId()).build(em));
    }

    public List<Object[]> activeSalesmen() {
        Sql q = new Sql("SELECT a.id, a.name, a.role FROM agent a WHERE a.tenant_id = :tenantId AND a.is_active = true "
                + "AND a.role IN (:roles) ORDER BY a.name");
        q.param("tenantId", tenantId()).param("roles", List.of(CockpitFilter.ROLE_LOCAL, CockpitFilter.ROLE_LMT));
        return rows(q.build(em));
    }

    /** Helper for callers turning (date, number) rows into a map. */
    public static Map<LocalDate, Double> byDate(List<Object[]> rows, int valueIndex) {
        Map<LocalDate, Double> m = new LinkedHashMap<>();
        for (Object[] r : rows) {
            m.merge((LocalDate) r[0], num(r[valueIndex]), Double::sum);
        }
        return m;
    }

    public static double num(Object o) {
        return o == null ? 0.0 : ((Number) o).doubleValue();
    }

    public static long lng(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    /** Ids from native queries can be Long, Integer or BigInteger depending on the driver. */
    public static Long id(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }
}
