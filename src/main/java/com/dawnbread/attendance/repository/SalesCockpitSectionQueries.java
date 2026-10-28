package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.dto.cockpit.CockpitFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static com.dawnbread.attendance.repository.SalesCockpitQueries.*;

/**
 * Queries for the Sales cockpit's lower sections (Returns, Shop coverage,
 * Salesman scorecard, LMT stock, Products).
 *
 * Same rules as SalesCockpitQueries, for the same reason (the 2026-10-27
 * OutOfMemoryError): PLAIN SQL only (no JPQL parsing), an explicit
 * tenant_id = :tenantId on every table read (Hibernate's tenant filter
 * does not apply to native SQL), a 20 s timeout, and only aggregated or
 * bounded rows ever reach Java.
 */
@Repository
public class SalesCockpitSectionQueries {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private SalesCockpitQueries base;

    /** Seconds since midnight of a TIME column — works on PostgreSQL and H2. */
    private static String secs(String col) {
        return "(EXTRACT(HOUR FROM " + col + ") * 3600 + EXTRACT(MINUTE FROM " + col + ") * 60 + FLOOR(EXTRACT(SECOND FROM " + col + ")))";
    }

    // ================================================================ Returns

    /** Shops by return rate: id, name, saleValue, returnValue, returnedUnits (worst first). */
    public List<Object[]> returnsByShop(CockpitFilter f, int limit) {
        Query q = base.sales("SELECT cs.id, cs.shop_name, " + SALE_VALUE + ", " + RETURN_VALUE + ", " + RETURN_UNITS,
                        null, f, f.startDate(), f.endDate())
                .and("cs.id IS NOT NULL")
                .add("GROUP BY cs.id, cs.shop_name HAVING " + RETURN_VALUE + " > 0")
                .add("ORDER BY CASE WHEN " + SALE_VALUE + " > 0 THEN " + RETURN_VALUE + " / " + SALE_VALUE
                        + " ELSE 1 END DESC, " + RETURN_VALUE + " DESC").build(em);
        q.setMaxResults(limit);
        return rows(q);
    }

    /** Salesmen by return rate: id, name, saleValue, returnValue, returnedUnits. */
    public List<Object[]> returnsBySalesman(CockpitFilter f) {
        return rows(base.sales("SELECT a.id, a.name, " + SALE_VALUE + ", " + RETURN_VALUE + ", " + RETURN_UNITS,
                        null, f, f.startDate(), f.endDate())
                .add("GROUP BY a.id, a.name").build(em));
    }

    /** Products by return rate: id, name, saleValue, returnValue, returnedUnits. */
    public List<Object[]> returnsByProduct(CockpitFilter f) {
        return rows(base.sales("SELECT p.id, p.name, " + SALE_VALUE + ", " + RETURN_VALUE + ", " + RETURN_UNITS,
                        "JOIN products p ON p.id = si.product_id", f, f.startDate(), f.endDate())
                .add("GROUP BY p.id, p.name").build(em));
    }

    // ================================================================ Shop coverage

    /** Per salesman: id, name, role, assigned shops, shops visited at least once in the period. */
    public List<Object[]> coverageBySalesman(CockpitFilter f) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql(
                "SELECT ag.id, ag.name, ag.role, COUNT(*), COUNT(v.shop_id) FROM customer_shops cs "
                        + "JOIN agent ag ON ag.id = cs.assigned_agent_id "
                        + "LEFT JOIN (SELECT DISTINCT x.shop_id, x.agent_id FROM (" + VISITS + ") x) v "
                        + "ON v.shop_id = cs.id AND v.agent_id = ag.id WHERE 1 = 1");
        q.param("tenantId", tenantId()).param("from", f.startDate()).param("to", f.endDate());
        base.assignedFilters(q, f);
        return rows(q.add("GROUP BY ag.id, ag.name, ag.role").build(em));
    }

    /** Feedback entries by reason: reason, count. */
    public List<Object[]> feedbackReasons(CockpitFilter f) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql(
                "SELECT fb.reason, COUNT(*) FROM shop_feedback fb JOIN agent a ON a.id = fb.agent_id "
                        + "JOIN customer_shops cs ON cs.id = fb.customer_shop_id LEFT JOIN areas ar ON ar.id = cs.area_id "
                        + "WHERE fb.tenant_id = :tenantId AND fb.visit_date BETWEEN :from AND :to");
        q.param("tenantId", tenantId()).param("from", f.startDate()).param("to", f.endDate());
        q.and("a.role IN (:roles)", "roles", salesmanRoles(f));
        if (f.areaId() != null) q.and("ar.id = :areaId", "areaId", f.areaId());
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return rows(q.add("GROUP BY fb.reason ORDER BY 2 DESC").build(em));
    }

    /** One page of assigned shops for the heatmap, least-visited first: id, name, salesman, visited days. */
    public List<Object[]> heatmapShops(CockpitFilter f, LocalDate from, LocalDate to, int page, int size) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql(
                "SELECT cs.id, cs.shop_name, ag.name, COUNT(DISTINCT v.d) FROM customer_shops cs "
                        + "JOIN agent ag ON ag.id = cs.assigned_agent_id "
                        + "LEFT JOIN (" + VISITS + ") v ON v.shop_id = cs.id AND v.agent_id = ag.id WHERE 1 = 1");
        q.param("tenantId", tenantId()).param("from", from).param("to", to);
        base.assignedFilters(q, f);
        Query query = q.add("GROUP BY cs.id, cs.shop_name, ag.name ORDER BY 4 ASC, cs.shop_name, cs.id").build(em);
        query.setFirstResult(page * size);
        query.setMaxResults(size);
        return rows(query);
    }

    /** Heatmap cells for a page of shops: shopId, date, kind ('SCAN' or 'FEEDBACK'), by the assigned salesman only. */
    public List<Object[]> heatmapCells(List<Long> shopIds, LocalDate from, LocalDate to) {
        if (shopIds.isEmpty()) return List.of();
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql(
                "SELECT s.customer_shop_id, s.scan_date, 'SCAN' FROM shop_visit_scan s "
                        + "JOIN customer_shops cs ON cs.id = s.customer_shop_id AND cs.assigned_agent_id = s.agent_id "
                        + "WHERE s.tenant_id = :tenantId AND s.visit_status = 'SUCCESS' AND s.scan_date BETWEEN :from AND :to "
                        + "AND s.customer_shop_id IN (:ids) "
                        + "UNION SELECT fb.customer_shop_id, fb.visit_date, 'FEEDBACK' FROM shop_feedback fb "
                        + "JOIN customer_shops cs ON cs.id = fb.customer_shop_id AND cs.assigned_agent_id = fb.agent_id "
                        + "WHERE fb.tenant_id = :tenantId AND fb.visit_date BETWEEN :from AND :to AND fb.customer_shop_id IN (:ids)");
        q.param("tenantId", tenantId()).param("from", from).param("to", to).param("ids", shopIds);
        return rows(q.build(em));
    }

    // ================================================================ Salesman scorecard

    /** Roster for the scorecard: id, name, role. */
    public List<Object[]> roster(CockpitFilter f) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql("SELECT a.id, a.name, a.role FROM agent a WHERE 1 = 1");
        q.param("tenantId", tenantId());
        base.salesmenFilters(q, f);
        return rows(q.add("ORDER BY a.name").build(em));
    }

    /** Per salesman: agentId, visits (distinct shop-days with a scan or feedback), distinct shops. */
    public List<Object[]> visitsBySalesman(CockpitFilter f) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql(
                "SELECT v.agent_id, COUNT(*), COUNT(DISTINCT v.shop_id) FROM (" + VISITS + ") v GROUP BY v.agent_id");
        q.param("tenantId", tenantId()).param("from", f.startDate()).param("to", f.endDate());
        return rows(q.build(em));
    }

    /** Per salesman: agentId, vouchers, saleValue, returnValue. */
    public List<Object[]> moneyBySalesman(CockpitFilter f) {
        return rows(base.sales("SELECT a.id, COUNT(DISTINCT sr.id), " + SALE_VALUE + ", " + RETURN_VALUE,
                null, f, f.startDate(), f.endDate()).add("GROUP BY a.id").build(em));
    }

    /** Per salesman: agentId, vouchers without a successful QR scan. */
    public List<Object[]> noScanBySalesman(CockpitFilter f) {
        return rows(base.shopVouchers("SELECT a.id, COUNT(*)", f, f.startDate(), f.endDate()).and(NO_SCAN)
                .add("GROUP BY a.id").build(em));
    }

    /** Per salesman: agentId, failed scans because they were outside the shop area. */
    public List<Object[]> offLocationBySalesman(CockpitFilter f) {
        return rows(base.scans("SELECT a.id, COUNT(*)", f, f.startDate(), f.endDate())
                .and("s.visit_status = 'OUTSIDE_GEOFENCE'").add("GROUP BY a.id").build(em));
    }

    /** First/last activity (scan, feedback or voucher) per salesman per day, in seconds since midnight. */
    private String dayTimes(boolean oneAgent) {
        String agentClause = oneAgent ? " AND %s.agent_id = :agentId" : "";
        return "SELECT x.agent_id, x.d, MIN(x.t) AS first_s, MAX(x.t) AS last_s FROM ("
                + "SELECT s.agent_id, s.scan_date AS d, " + secs("s.scan_time") + " AS t FROM shop_visit_scan s "
                + "WHERE s.tenant_id = :tenantId AND s.visit_status = 'SUCCESS' AND s.scan_date BETWEEN :from AND :to"
                + agentClause.formatted("s")
                + " UNION ALL SELECT fb.agent_id, fb.visit_date, " + secs("fb.visit_time") + " FROM shop_feedback fb "
                + "WHERE fb.tenant_id = :tenantId AND fb.visit_date BETWEEN :from AND :to" + agentClause.formatted("fb")
                + " UNION ALL SELECT v.agent_id, v.sale_date, " + secs("v.sale_time") + " FROM sales_records v "
                + "WHERE v.tenant_id = :tenantId AND v.sale_date BETWEEN :from AND :to AND v.agent_id IS NOT NULL"
                + agentClause.formatted("v")
                + ") x GROUP BY x.agent_id, x.d";
    }

    /** Per salesman: agentId, average first-activity second, average last-activity second, active days. */
    public List<Object[]> timesBySalesman(CockpitFilter f) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql("SELECT t.agent_id, AVG(t.first_s), AVG(t.last_s), COUNT(*) FROM ("
                + dayTimes(false) + ") t GROUP BY t.agent_id");
        q.param("tenantId", tenantId()).param("from", f.startDate()).param("to", f.endDate());
        return rows(q.build(em));
    }

    /** One salesman, per day: date, first second, last second. */
    public List<Object[]> dayTimesForSalesman(CockpitFilter f, Long agentId) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql("SELECT t.d, t.first_s, t.last_s FROM (" + dayTimes(true) + ") t");
        q.param("tenantId", tenantId()).param("from", f.startDate()).param("to", f.endDate()).param("agentId", agentId);
        return rows(q.build(em));
    }

    /** One salesman, per day: date, visits (distinct shops with a scan or feedback). */
    public List<Object[]> dayVisitsForSalesman(CockpitFilter f, Long agentId) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql(
                "SELECT v.d, COUNT(*) FROM (" + VISITS + ") v WHERE v.agent_id = :agentId GROUP BY v.d");
        q.param("tenantId", tenantId()).param("from", f.startDate()).param("to", f.endDate()).param("agentId", agentId);
        return rows(q.build(em));
    }

    /** One salesman, per day: date, vouchers, saleValue, returnValue (segment/area filters still apply). */
    public List<Object[]> dayMoneyForSalesman(CockpitFilter f, Long agentId) {
        CockpitFilter one = new CockpitFilter(f.startDate(), f.endDate(), f.segment(), f.areaId(), agentId);
        return rows(base.sales("SELECT sr.sale_date, COUNT(DISTINCT sr.id), " + SALE_VALUE + ", " + RETURN_VALUE,
                null, one, f.startDate(), f.endDate()).add("GROUP BY sr.sale_date").build(em));
    }

    // ================================================================ LMT stock

    /** Per LMT salesman over reconciled days: id, name, opening, sold, returned, unsold, replaced, missing, stock days. */
    public List<Object[]> lmtStockBySalesman(CockpitFilter f) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql(
                "SELECT a.id, a.name, COALESCE(SUM(i.opening_stock), 0), COALESCE(SUM(i.sold_qty), 0), "
                        + "COALESCE(SUM(i.returned_qty), 0), COALESCE(SUM(i.unsold_qty), 0), COALESCE(SUM(i.rbe_qty), 0), "
                        + "COALESCE(SUM(CASE WHEN i.missing_qty > 0 THEN i.missing_qty ELSE 0 END), 0), COUNT(DISTINCT d.id) "
                        + "FROM lmt_daily_stock_item i JOIN lmt_daily_stock d ON d.id = i.lmt_daily_stock_id "
                        + "JOIN agent a ON a.id = d.agent_id "
                        + "WHERE i.tenant_id = :tenantId AND d.tenant_id = :tenantId AND d.status = 'RECONCILED' "
                        + "AND d.stock_date BETWEEN :from AND :to");
        q.param("tenantId", tenantId()).param("from", f.startDate()).param("to", f.endDate());
        if (f.agentId() != null) q.and("a.id = :agentId", "agentId", f.agentId());
        return rows(q.add("GROUP BY a.id, a.name ORDER BY a.name").build(em));
    }

    /** Stock-day health: ok days, shortage days, not-reconciled days (open days before :today only). */
    public Object[] lmtHealth(CockpitFilter f, LocalDate today) {
        SalesCockpitQueries.Sql inner = new SalesCockpitQueries.Sql(
                "SELECT d.id, d.status, COALESCE(SUM(CASE WHEN i.missing_qty > 0 THEN 1 ELSE 0 END), 0) AS short_items "
                        + "FROM lmt_daily_stock d LEFT JOIN lmt_daily_stock_item i ON i.lmt_daily_stock_id = d.id "
                        + "AND i.tenant_id = :tenantId "
                        + "WHERE d.tenant_id = :tenantId AND d.stock_date BETWEEN :from AND :to "
                        + "AND (d.status = 'RECONCILED' OR d.stock_date < :today)");
        inner.param("tenantId", tenantId()).param("from", f.startDate()).param("to", f.endDate()).param("today", today);
        if (f.agentId() != null) inner.and("d.agent_id = :agentId", "agentId", f.agentId());
        inner.add("GROUP BY d.id, d.status");
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql(
                "SELECT COALESCE(SUM(CASE WHEN z.status = 'RECONCILED' AND z.short_items = 0 THEN 1 ELSE 0 END), 0), "
                        + "COALESCE(SUM(CASE WHEN z.status = 'RECONCILED' AND z.short_items > 0 THEN 1 ELSE 0 END), 0), "
                        + "COALESCE(SUM(CASE WHEN z.status = 'OPEN' THEN 1 ELSE 0 END), 0) FROM (" + inner.text() + ") z")
                .merge(inner);
        return rows(q.build(em)).get(0);
    }

    // ================================================================ Products

    /** Every product sold in the period: id, name, soldUnits, saleValue (highest value first). */
    public List<Object[]> productSales(CockpitFilter f) {
        return rows(base.sales("SELECT p.id, p.name, " + SOLD_UNITS + ", " + SALE_VALUE,
                        "JOIN products p ON p.id = si.product_id", f, f.startDate(), f.endDate())
                .add("GROUP BY p.id, p.name ORDER BY 4 DESC, 2").build(em));
    }

    /** Active products with the fewest sold units in the period (incl. none): id, name, soldUnits, saleValue, lastSold. */
    public List<Object[]> slowProducts(CockpitFilter f, int limit) {
        SalesCockpitQueries.Sql period = base.sales("SELECT si.product_id AS pid, " + SOLD_UNITS + " AS units, "
                + SALE_VALUE + " AS val", null, f, f.startDate(), f.endDate()).add("GROUP BY si.product_id");
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql(
                "SELECT p.id, p.name, COALESCE(x.units, 0), COALESCE(x.val, 0), lst.last_d FROM products p "
                        + "LEFT JOIN (" + period.text() + ") x ON x.pid = p.id "
                        + "LEFT JOIN (SELECT si2.product_id AS pid, MAX(si2.sale_date) AS last_d FROM sale_items si2 "
                        + "WHERE si2.tenant_id = :tenantId AND si2.transaction_type = 'SALE' GROUP BY si2.product_id) lst "
                        + "ON lst.pid = p.id "
                        + "WHERE p.tenant_id = :tenantId AND p.is_active = true "
                        + "ORDER BY COALESCE(x.units, 0) ASC, lst.last_d ASC NULLS FIRST, p.name")
                .merge(period);
        Query query = q.build(em);
        query.setMaxResults(limit);
        return rows(query);
    }
}
