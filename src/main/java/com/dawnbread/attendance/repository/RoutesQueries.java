package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.dto.cockpit.CockpitFilter;
import com.dawnbread.attendance.repository.SalesCockpitQueries.Sql;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static com.dawnbread.attendance.repository.SalesCockpitQueries.rows;
import static com.dawnbread.attendance.repository.SalesCockpitQueries.tenantId;

/**
 * Sales > Routes queries. PLAIN SQL for the same reasons as
 * SalesCockpitQueries (no JPQL parser memory, explicit tenant_id on every
 * table because Hibernate's tenant filter does not reach native SQL, and
 * the same 20-second timeout via Sql.build).
 *
 * "Visited" is the cockpit's rule: a SUCCESS scan or a feedback entry by
 * the shop's ASSIGNED salesman on that Pakistan date. Every query returns
 * at most one row per active shop (about 3,000), so the result size is
 * bounded by the shop list, not by how much visit history builds up: the
 * per-shop lookups walk the (agent, shop, date) scan index and the
 * (shop, date) feedback index.
 */
@Repository
public class RoutesQueries {

    @PersistenceContext
    private EntityManager em;

    private static final String SALESMAN_ROLES = "('" + CockpitFilter.ROLE_LOCAL + "', '" + CockpitFilter.ROLE_LMT + "')";

    /** Latest SUCCESS scan by the assigned salesman (ag) at the shop (cs), on or before :to. */
    private static final String LAST_SCAN = "(SELECT MAX(s.scan_date) FROM shop_visit_scan s WHERE s.tenant_id = :tenantId "
            + "AND s.agent_id = ag.id AND s.customer_shop_id = cs.id AND s.visit_status = 'SUCCESS' AND s.scan_date <= :to)";
    private static final String LAST_FEEDBACK = "(SELECT MAX(fb.visit_date) FROM shop_feedback fb WHERE fb.tenant_id = :tenantId "
            + "AND fb.customer_shop_id = cs.id AND fb.agent_id = ag.id AND fb.visit_date <= :to)";

    /**
     * Distinct due dates on which the assigned salesman visited the shop: days with a SUCCESS scan, plus
     * days with a feedback and no such scan (so a day with both counts once). Two correlated counts rather
     * than a UNION in a derived table, which H2 (the test database) cannot correlate.
     */
    private static final String DAYS_VISITED = "((SELECT COUNT(DISTINCT s.scan_date) FROM shop_visit_scan s "
            + "WHERE s.tenant_id = :tenantId AND s.agent_id = ag.id AND s.customer_shop_id = cs.id "
            + "AND s.visit_status = 'SUCCESS' AND s.scan_date IN (:dueDates)) + "
            + "(SELECT COUNT(DISTINCT fb.visit_date) FROM shop_feedback fb WHERE fb.tenant_id = :tenantId "
            + "AND fb.customer_shop_id = cs.id AND fb.agent_id = ag.id AND fb.visit_date IN (:dueDates) "
            + "AND NOT EXISTS (SELECT 1 FROM shop_visit_scan s2 WHERE s2.tenant_id = :tenantId AND s2.agent_id = ag.id "
            + "AND s2.customer_shop_id = cs.id AND s2.visit_status = 'SUCCESS' AND s2.scan_date = fb.visit_date)))";

    /**
     * Active shops of the given active salesmen: shopId, shopName, shopCode, area, agentId, salesman, role,
     * lastScan, lastFeedback, daysVisited. dueDates are the salesmen's due days in the range (their working
     * days minus company holidays); empty = none due, daysVisited is then 0.
     */
    public List<Object[]> assignedShops(List<Long> agentIds, List<LocalDate> dueDates, LocalDate to) {
        if (agentIds.isEmpty()) return List.of();
        Sql q = new Sql("SELECT cs.id, cs.shop_name, cs.shop_code, ar.name, ag.id, ag.name, ag.role, "
                + LAST_SCAN + ", " + LAST_FEEDBACK + ", " + (dueDates.isEmpty() ? "0" : DAYS_VISITED)
                + " FROM customer_shops cs "
                + "JOIN agent ag ON ag.id = cs.assigned_agent_id AND ag.tenant_id = :tenantId AND ag.is_active = true "
                + "AND ag.role IN " + SALESMAN_ROLES + " "
                + "LEFT JOIN areas ar ON ar.id = cs.area_id AND ar.tenant_id = :tenantId "
                + "WHERE cs.tenant_id = :tenantId AND cs.is_active = true AND ag.id IN (:agentIds)");
        q.param("tenantId", tenantId()).param("to", to).param("agentIds", agentIds);
        if (!dueDates.isEmpty()) q.param("dueDates", dueDates);
        return rows(q.build(em));
    }

    /** Latest SUCCESS scan / feedback at the shop by ANY salesman, on or before :to (the QR Scanned Shops rule). */
    private static final String ANY_LAST_SCAN = "(SELECT MAX(s.scan_date) FROM shop_visit_scan s WHERE s.tenant_id = :tenantId "
            + "AND s.customer_shop_id = cs.id AND s.visit_status = 'SUCCESS' AND s.scan_date <= :to)";
    private static final String ANY_LAST_FEEDBACK = "(SELECT MAX(fb.visit_date) FROM shop_feedback fb WHERE fb.tenant_id = :tenantId "
            + "AND fb.customer_shop_id = cs.id AND fb.visit_date <= :to)";
    private static final String ANY_DAYS_VISITED = "((SELECT COUNT(DISTINCT s.scan_date) FROM shop_visit_scan s "
            + "WHERE s.tenant_id = :tenantId AND s.customer_shop_id = cs.id AND s.visit_status = 'SUCCESS' "
            + "AND s.scan_date IN (:dueDates)) + "
            + "(SELECT COUNT(DISTINCT fb.visit_date) FROM shop_feedback fb WHERE fb.tenant_id = :tenantId "
            + "AND fb.customer_shop_id = cs.id AND fb.visit_date IN (:dueDates) "
            + "AND NOT EXISTS (SELECT 1 FROM shop_visit_scan s2 WHERE s2.tenant_id = :tenantId "
            + "AND s2.customer_shop_id = cs.id AND s2.visit_status = 'SUCCESS' AND s2.scan_date = fb.visit_date)))";

    /**
     * Active shops with no usable salesman: none assigned, or the assigned account is inactive or not a
     * salesman. shopId, shopName, shopCode, area, assignedName (null = none), assignedActive, assignedRole,
     * lastScan, lastFeedback, daysVisited (by any salesman, on the given dates).
     */
    public List<Object[]> unassignedShops(List<LocalDate> dueDates, LocalDate to) {
        Sql q = new Sql("SELECT cs.id, cs.shop_name, cs.shop_code, ar.name, ag.name, ag.is_active, ag.role, "
                + ANY_LAST_SCAN + ", " + ANY_LAST_FEEDBACK + ", " + (dueDates.isEmpty() ? "0" : ANY_DAYS_VISITED)
                + " FROM customer_shops cs "
                + "LEFT JOIN agent ag ON ag.id = cs.assigned_agent_id AND ag.tenant_id = :tenantId "
                + "LEFT JOIN areas ar ON ar.id = cs.area_id AND ar.tenant_id = :tenantId "
                + "WHERE cs.tenant_id = :tenantId AND cs.is_active = true "
                + "AND (ag.id IS NULL OR ag.is_active = false OR ag.role NOT IN " + SALESMAN_ROLES + ")");
        q.param("tenantId", tenantId()).param("to", to);
        if (!dueDates.isEmpty()) q.param("dueDates", dueDates);
        return rows(q.build(em));
    }

    /** Active salesmen who have at least one active shop assigned: id, name, role. */
    public List<Object[]> salesmenWithShops() {
        Sql q = new Sql("SELECT ag.id, ag.name, ag.role FROM agent ag WHERE ag.tenant_id = :tenantId AND ag.is_active = true "
                + "AND ag.role IN " + SALESMAN_ROLES + " AND EXISTS (SELECT 1 FROM customer_shops cs "
                + "WHERE cs.tenant_id = :tenantId AND cs.is_active = true AND cs.assigned_agent_id = ag.id) ORDER BY ag.name");
        return rows(q.param("tenantId", tenantId()).build(em));
    }

    /** Company holidays in the range: date, name. */
    public List<Object[]> holidays(LocalDate from, LocalDate to) {
        Sql q = new Sql("SELECT h.holiday_date, h.name FROM company_holiday h WHERE h.tenant_id = :tenantId "
                + "AND h.holiday_date BETWEEN :from AND :to ORDER BY h.holiday_date");
        return rows(q.param("tenantId", tenantId()).param("from", from).param("to", to).build(em));
    }
}
