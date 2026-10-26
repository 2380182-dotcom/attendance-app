package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.dto.cockpit.CockpitFilter;
import com.dawnbread.attendance.entity.Agent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sales cockpit queries. Every method is a single aggregate query
 * (SUM / COUNT / GROUP BY / EXISTS) — the database does the counting and
 * Java only ever receives already-grouped rows (one per day, per salesman,
 * per area, ...), never raw sale lines.
 *
 * Optional filters are added to the JPQL text only when they are set, so a
 * null parameter is never sent to Postgres (the untyped-null-as-bytea
 * problem behind the earlier lower(bytea) hotfix cannot happen here).
 *
 * Tenant scoping comes from the Hibernate tenantFilter that
 * SecurityInterceptor enables for every request — it applies to every
 * entity referenced below, exactly like the Spring Data repositories.
 */
@Repository
public class SalesCockpitQueries {

    private static final String TT = "com.dawnbread.attendance.entity.TransactionType.";
    private static final String VS = "com.dawnbread.attendance.entity.ShopVisitStatus.";
    private static final String LS = "com.dawnbread.attendance.entity.LmtStockStatus.";

    public static final String SALE_VALUE =
            "COALESCE(SUM(CASE WHEN si.transactionType = " + TT + "SALE THEN COALESCE(si.totalPrice, 0.0) ELSE 0.0 END), 0.0)";
    public static final String RETURN_VALUE =
            "COALESCE(SUM(CASE WHEN si.transactionType = " + TT + "RETURN THEN COALESCE(si.totalPrice, 0.0) ELSE 0.0 END), 0.0)";
    public static final String SOLD_UNITS =
            "COALESCE(SUM(CASE WHEN si.transactionType = " + TT + "SALE THEN si.quantity ELSE 0 END), 0)";
    public static final String RETURN_UNITS =
            "COALESCE(SUM(CASE WHEN si.transactionType = " + TT + "RETURN THEN si.quantity ELSE 0 END), 0)";
    public static final String RBE_UNITS =
            "COALESCE(SUM(CASE WHEN si.transactionType = " + TT + "RETURN_BEFORE_EXPIRY THEN si.quantity ELSE 0 END), 0)";

    private static final String SALES_FROM =
            " FROM SaleItem si JOIN si.salesRecord sr JOIN sr.agent a LEFT JOIN sr.customerShop cs LEFT JOIN cs.area ar ";
    private static final String ROLE_EXPR = "COALESCE(sr.agentRoleAtSale, a.role)";

    @PersistenceContext
    private EntityManager em;

    // ------------------------------------------------------------------
    // Tiny JPQL builder: appends a clause + its parameter only when used.
    // ------------------------------------------------------------------
    static final class Jpql {
        private final StringBuilder sb = new StringBuilder();
        private final Map<String, Object> params = new LinkedHashMap<>();

        Jpql(String start) {
            sb.append(start);
        }

        Jpql add(String text) {
            sb.append(' ').append(text);
            return this;
        }

        Jpql and(String clause) {
            sb.append(" AND ").append(clause);
            return this;
        }

        Jpql and(String clause, String name, Object value) {
            sb.append(" AND ").append(clause);
            params.put(name, value);
            return this;
        }

        Jpql param(String name, Object value) {
            params.put(name, value);
            return this;
        }

        String text() {
            return sb.toString();
        }

        Query build(EntityManager em) {
            Query q = em.createQuery(sb.toString());
            params.forEach(q::setParameter);
            return q;
        }

        <T> TypedQuery<T> build(EntityManager em, Class<T> type) {
            TypedQuery<T> q = em.createQuery(sb.toString(), type);
            params.forEach(q::setParameter);
            return q;
        }
    }

    /** Roles a salesman roster covers for this segment (ALL = both salesman roles). */
    public static List<String> salesmanRoles(CockpitFilter f) {
        return f.role() != null ? List.of(f.role()) : List.of(CockpitFilter.ROLE_LOCAL, CockpitFilter.ROLE_LMT);
    }

    /** The sales base WHERE: date range + segment + area + salesman. */
    private Jpql sales(String select, String extraJoins, CockpitFilter f, LocalDate from, LocalDate to) {
        Jpql q = new Jpql(select + SALES_FROM + (extraJoins == null ? "" : extraJoins)
                + " WHERE sr.saleDate BETWEEN :from AND :to");
        q.param("from", from).param("to", to);
        if (f.role() != null) q.and(ROLE_EXPR + " = :role", "role", f.role());
        if (f.areaId() != null) q.and("ar.id = :areaId", "areaId", f.areaId());
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return q;
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(Query q) {
        return (List<Object[]>) q.getResultList();
    }

    // ------------------------------------------------------------------
    // Sales money / units
    // ------------------------------------------------------------------

    /** Per-day: date, saleValue, returnValue, soldUnits, returnUnits, rbeUnits. */
    public List<Object[]> dailySales(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(sales("SELECT sr.saleDate, " + SALE_VALUE + ", " + RETURN_VALUE + ", " + SOLD_UNITS + ", "
                + RETURN_UNITS + ", " + RBE_UNITS, null, f, from, to)
                .add("GROUP BY sr.saleDate ORDER BY sr.saleDate").build(em));
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
                "LEFT JOIN ar.tse t LEFT JOIN ar.srTse st LEFT JOIN ar.asm am", f, f.startDate(), f.endDate())
                .add("GROUP BY ar.id, ar.name, t.name, st.name, am.name").build(em));
    }

    /** Top products by sold value: id, name, soldUnits, saleValue, returnValue. */
    public List<Object[]> topProducts(CockpitFilter f, int limit) {
        Query q = sales("SELECT p.id, p.name, " + SOLD_UNITS + ", " + SALE_VALUE + ", " + RETURN_VALUE,
                "JOIN si.product p", f, f.startDate(), f.endDate())
                .add("GROUP BY p.id, p.name ORDER BY " + SALE_VALUE + " DESC, p.name").build(em);
        q.setMaxResults(limit);
        return rows(q);
    }

    /** Shops whose return value / sale value is above pct: id, name, saleValue, returnValue (worst first). */
    public List<Object[]> shopsAboveReturnPercent(CockpitFilter f, double pct) {
        return rows(sales("SELECT cs.id, cs.shopName, " + SALE_VALUE + ", " + RETURN_VALUE, null, f,
                f.startDate(), f.endDate())
                .and("cs.id IS NOT NULL")
                .add("GROUP BY cs.id, cs.shopName HAVING " + SALE_VALUE + " > 0 AND " + RETURN_VALUE
                        + " * 100.0 > :pct * " + SALE_VALUE)
                .param("pct", pct)
                .add("ORDER BY " + RETURN_VALUE + " / " + SALE_VALUE + " DESC").build(em));
    }

    /** Products whose return value / sale value is above pct: id, name, saleValue, returnValue (worst first). */
    public List<Object[]> productsAboveReturnPercent(CockpitFilter f, double pct) {
        return rows(sales("SELECT p.id, p.name, " + SALE_VALUE + ", " + RETURN_VALUE, "JOIN si.product p", f,
                f.startDate(), f.endDate())
                .add("GROUP BY p.id, p.name HAVING " + SALE_VALUE + " > 0 AND " + RETURN_VALUE
                        + " * 100.0 > :pct * " + SALE_VALUE)
                .param("pct", pct)
                .add("ORDER BY " + RETURN_VALUE + " / " + SALE_VALUE + " DESC").build(em));
    }

    // ------------------------------------------------------------------
    // Drill-down: vouchers with totals, paginated in the database
    // ------------------------------------------------------------------

    private Jpql voucherFilter(String select, CockpitFilter f, Long productId, boolean noArea) {
        Jpql q = sales(select, null, f, f.startDate(), f.endDate());
        if (noArea) q.and("ar.id IS NULL");
        if (productId != null) {
            q.and("EXISTS (SELECT 1 FROM SaleItem x WHERE x.salesRecord = sr AND x.product.id = :productId)",
                    "productId", productId);
        }
        return q;
    }

    /** id, date, time, agent, role, shop, area, saleValue, returnValue, soldUnits, rbeUnits. */
    public List<Object[]> vouchers(CockpitFilter f, Long productId, boolean noArea, int page, int size) {
        Query q = voucherFilter("SELECT sr.id, sr.saleDate, sr.saleTime, a.name, " + ROLE_EXPR
                + ", COALESCE(cs.shopName, sr.storeName), ar.name, " + SALE_VALUE + ", " + RETURN_VALUE + ", "
                + SOLD_UNITS + ", " + RBE_UNITS, f, productId, noArea)
                .add("GROUP BY sr.id, sr.saleDate, sr.saleTime, a.name, sr.agentRoleAtSale, a.role, cs.shopName, "
                        + "sr.storeName, ar.name ORDER BY sr.saleDate DESC, sr.saleTime DESC, sr.id DESC")
                .build(em);
        q.setFirstResult(page * size);
        q.setMaxResults(size);
        return rows(q);
    }

    public long countVouchers(CockpitFilter f, Long productId, boolean noArea) {
        return voucherFilter("SELECT COUNT(DISTINCT sr.id)", f, productId, noArea).build(em, Long.class)
                .getSingleResult();
    }

    // ------------------------------------------------------------------
    // Location / scan flags
    // ------------------------------------------------------------------

    private Jpql scans(String select, CockpitFilter f, LocalDate from, LocalDate to) {
        Jpql q = new Jpql(select + " FROM ShopVisitScan s JOIN Agent a ON a.id = s.agentId "
                + "LEFT JOIN s.customerShop cs LEFT JOIN cs.area ar WHERE s.scanDate BETWEEN :from AND :to");
        q.param("from", from).param("to", to);
        q.and("a.role IN :roles", "roles", salesmanRoles(f));
        if (f.areaId() != null) q.and("ar.id = :areaId", "areaId", f.areaId());
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return q;
    }

    /** Per-day count of failed QR scans because the salesman was outside the shop's area. */
    public List<Object[]> dailyOffLocationAttempts(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(scans("SELECT s.scanDate, COUNT(s)", f, from, to)
                .and("s.visitStatus = " + VS + "OUTSIDE_GEOFENCE")
                .add("GROUP BY s.scanDate").build(em));
    }

    /** Off-location attempts per salesman in a range: name, count (most first). */
    public List<Object[]> offLocationAttemptsByAgent(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(scans("SELECT a.name, COUNT(s)", f, from, to)
                .and("s.visitStatus = " + VS + "OUTSIDE_GEOFENCE")
                .add("GROUP BY a.name ORDER BY COUNT(s) DESC").build(em));
    }

    private Jpql shopVouchers(String select, CockpitFilter f, LocalDate from, LocalDate to) {
        Jpql q = new Jpql(select + " FROM SalesRecord sr JOIN sr.agent a JOIN sr.customerShop cs "
                + "LEFT JOIN cs.area ar WHERE sr.saleDate BETWEEN :from AND :to");
        q.param("from", from).param("to", to);
        if (f.role() != null) q.and(ROLE_EXPR + " = :role", "role", f.role());
        if (f.areaId() != null) q.and("ar.id = :areaId", "areaId", f.areaId());
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return q;
    }

    /** Per-day count of shop vouchers saved with no distance check (shop has no location set up — LMT only). */
    public List<Object[]> dailyLocationNotChecked(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(shopVouchers("SELECT sr.saleDate, COUNT(sr)", f, from, to)
                .and("sr.distanceFromShopMeters IS NULL")
                .add("GROUP BY sr.saleDate").build(em));
    }

    private static final String NO_SCAN = "NOT EXISTS (SELECT 1 FROM ShopVisitScan s WHERE s.agentId = a.id "
            + "AND s.customerShop.id = cs.id AND s.scanDate = sr.saleDate AND s.visitStatus = " + VS + "SUCCESS)";

    /** Per-day count of vouchers with no successful QR scan by that salesman at that shop that day (same rule as QR Scanned Shops). */
    public List<Object[]> dailyVouchersWithoutScan(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(shopVouchers("SELECT sr.saleDate, COUNT(sr)", f, from, to).and(NO_SCAN)
                .add("GROUP BY sr.saleDate").build(em));
    }

    /** Vouchers without scan per salesman in a range: name, count. */
    public List<Object[]> vouchersWithoutScanByAgent(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(shopVouchers("SELECT a.name, COUNT(sr)", f, from, to).and(NO_SCAN)
                .add("GROUP BY a.name ORDER BY COUNT(sr) DESC").build(em));
    }

    /** Per-day count of shop feedback entries (visit without a sale). */
    public List<Object[]> dailyFeedback(CockpitFilter f, LocalDate from, LocalDate to) {
        Jpql q = new Jpql("SELECT fb.visitDate, COUNT(fb) FROM ShopFeedback fb JOIN Agent a ON a.id = fb.agentId "
                + "JOIN fb.customerShop cs LEFT JOIN cs.area ar WHERE fb.visitDate BETWEEN :from AND :to");
        q.param("from", from).param("to", to);
        q.and("a.role IN :roles", "roles", salesmanRoles(f));
        if (f.areaId() != null) q.and("ar.id = :areaId", "areaId", f.areaId());
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return rows(q.add("GROUP BY fb.visitDate").build(em));
    }

    // ------------------------------------------------------------------
    // LMT stock (per salesman, so the area filter does not apply)
    // ------------------------------------------------------------------

    private Jpql lmtStock(String select, CockpitFilter f, LocalDate from, LocalDate to) {
        Jpql q = new Jpql(select + " FROM LmtDailyStockItem i JOIN i.lmtDailyStock d JOIN Agent a ON a.id = d.agentId "
                + "WHERE d.stockDate BETWEEN :from AND :to AND d.status = " + LS + "RECONCILED");
        q.param("from", from).param("to", to);
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return q;
    }

    private static final String MISSING_UNITS = "COALESCE(SUM(CASE WHEN i.missingQty > 0 THEN i.missingQty ELSE 0 END), 0)";

    /** Per-day missing units on reconciled LMT stock (only shortages, never negative over-counts). */
    public List<Object[]> dailyLmtMissing(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(lmtStock("SELECT d.stockDate, " + MISSING_UNITS, f, from, to)
                .add("GROUP BY d.stockDate").build(em));
    }

    /** Missing units per LMT salesman in a range: name, missing (most first, only > 0). */
    public List<Object[]> lmtMissingByAgent(CockpitFilter f, LocalDate from, LocalDate to) {
        return rows(lmtStock("SELECT a.name, " + MISSING_UNITS, f, from, to)
                .add("GROUP BY a.name HAVING " + MISSING_UNITS + " > 0 ORDER BY " + MISSING_UNITS + " DESC")
                .build(em));
    }

    /** Stock days still OPEN (not reconciled) per LMT salesman, for days strictly before :before: name, days. */
    public List<Object[]> unreconciledByAgent(CockpitFilter f, LocalDate from, LocalDate before) {
        Jpql q = new Jpql("SELECT a.name, COUNT(d) FROM LmtDailyStock d JOIN Agent a ON a.id = d.agentId "
                + "WHERE d.stockDate >= :from AND d.stockDate < :before AND d.status = " + LS + "OPEN");
        q.param("from", from).param("before", before);
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return rows(q.add("GROUP BY a.name ORDER BY COUNT(d) DESC").build(em));
    }

    // ------------------------------------------------------------------
    // Shop coverage & salesman activity (same "visited" rule as QR Scanned
    // Shops: a SUCCESS scan or a feedback entry by the ASSIGNED salesman).
    // ------------------------------------------------------------------

    private static final String VISITED_ON = "(EXISTS (SELECT 1 FROM ShopVisitScan s WHERE s.customerShop = cs "
            + "AND s.agentId = ag.id AND s.scanDate = :day AND s.visitStatus = " + VS + "SUCCESS) "
            + "OR EXISTS (SELECT 1 FROM ShopFeedback fb WHERE fb.customerShop = cs AND fb.agentId = ag.id "
            + "AND fb.visitDate = :day))";

    private Jpql assignedShops(String select, CockpitFilter f) {
        Jpql q = new Jpql(select + " FROM CustomerShop cs JOIN cs.assignedAgent ag LEFT JOIN cs.area ar "
                + "WHERE cs.isActive = true AND ag.role IN :roles");
        q.param("roles", salesmanRoles(f));
        if (f.areaId() != null) q.and("ar.id = :areaId", "areaId", f.areaId());
        if (f.agentId() != null) q.and("ag.id = :agentId", "agentId", f.agentId());
        return q;
    }

    public long countAssignedShops(CockpitFilter f) {
        return assignedShops("SELECT COUNT(cs)", f).build(em, Long.class).getSingleResult();
    }

    public long countVisitedAssignedShops(CockpitFilter f, LocalDate day) {
        return assignedShops("SELECT COUNT(cs)", f).and(VISITED_ON, "day", day).build(em, Long.class)
                .getSingleResult();
    }

    private static final String VISITED_SINCE = "(EXISTS (SELECT 1 FROM ShopVisitScan s WHERE s.customerShop = cs "
            + "AND s.agentId = ag.id AND s.scanDate > :cutoff AND s.visitStatus = " + VS + "SUCCESS) "
            + "OR EXISTS (SELECT 1 FROM ShopFeedback fb WHERE fb.customerShop = cs AND fb.agentId = ag.id "
            + "AND fb.visitDate > :cutoff))";

    /** Assigned shops with no visit AFTER cutoff (i.e. last visit on/before cutoff, or never). */
    public long countShopsNotVisitedSince(CockpitFilter f, LocalDate cutoff) {
        return assignedShops("SELECT COUNT(cs)", f).and("NOT " + VISITED_SINCE, "cutoff", cutoff)
                .build(em, Long.class).getSingleResult();
    }

    /** Same list, longest-unvisited first: shopName, salesman, lastScan, lastFeedback. */
    public List<Object[]> shopsNotVisitedSince(CockpitFilter f, LocalDate cutoff, int limit) {
        String lastScan = "(SELECT MAX(s.scanDate) FROM ShopVisitScan s WHERE s.customerShop = cs AND s.agentId = ag.id "
                + "AND s.visitStatus = " + VS + "SUCCESS)";
        String lastFeedback = "(SELECT MAX(fb.visitDate) FROM ShopFeedback fb WHERE fb.customerShop = cs AND fb.agentId = ag.id)";
        Query q = assignedShops("SELECT cs.shopName, ag.name, " + lastScan + ", " + lastFeedback, f)
                .and("NOT " + VISITED_SINCE, "cutoff", cutoff)
                .add("ORDER BY " + lastScan + " ASC NULLS FIRST, cs.shopName").build(em);
        q.setMaxResults(limit);
        return rows(q);
    }

    private static final String AGENT_ACTIVE_ON = "(EXISTS (SELECT 1 FROM ShopVisitScan s WHERE s.agentId = a.id "
            + "AND s.scanDate = :day AND s.visitStatus = " + VS + "SUCCESS) "
            + "OR EXISTS (SELECT 1 FROM ShopFeedback fb WHERE fb.agentId = a.id AND fb.visitDate = :day) "
            + "OR EXISTS (SELECT 1 FROM SalesRecord v WHERE v.agent = a AND v.saleDate = :day))";

    private Jpql salesmen(String select, CockpitFilter f) {
        Jpql q = new Jpql(select + " FROM Agent a WHERE a.isActive = true AND a.role IN :roles");
        q.param("roles", salesmanRoles(f));
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        if (f.areaId() != null) {
            q.and("EXISTS (SELECT 1 FROM CustomerShop c2 WHERE c2.assignedAgent = a AND c2.area.id = :areaId)",
                    "areaId", f.areaId());
        }
        return q;
    }

    public long countSalesmen(CockpitFilter f) {
        return salesmen("SELECT COUNT(a)", f).build(em, Long.class).getSingleResult();
    }

    /** Salesmen with at least one visit (scan, feedback or voucher) that day. */
    public long countActiveSalesmen(CockpitFilter f, LocalDate day) {
        return salesmen("SELECT COUNT(a)", f).and(AGENT_ACTIVE_ON, "day", day).build(em, Long.class)
                .getSingleResult();
    }

    /** Salesmen with NO visit that day — the roster is small, and workingDays (JSON) is checked in Java. */
    public List<Agent> salesmenWithoutVisit(CockpitFilter f, LocalDate day) {
        return salesmen("SELECT a", f).and("NOT " + AGENT_ACTIVE_ON, "day", day).add("ORDER BY a.name")
                .build(em, Agent.class).getResultList();
    }

    // ------------------------------------------------------------------
    // Filter dropdowns
    // ------------------------------------------------------------------

    public List<Object[]> activeAreas() {
        return rows(em.createQuery("SELECT ar.id, ar.name FROM Area ar WHERE ar.isActive = true ORDER BY ar.name"));
    }

    public List<Object[]> activeSalesmen() {
        return rows(em.createQuery("SELECT a.id, a.name, a.role FROM Agent a WHERE a.isActive = true "
                + "AND a.role IN :roles ORDER BY a.name")
                .setParameter("roles", List.of(CockpitFilter.ROLE_LOCAL, CockpitFilter.ROLE_LMT)));
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

    static List<Object[]> empty() {
        return new ArrayList<>();
    }
}
