package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.dto.cockpit.HrFilter;
import com.dawnbread.attendance.util.KarachiTime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static com.dawnbread.attendance.repository.SalesCockpitQueries.rows;
import static com.dawnbread.attendance.repository.SalesCockpitQueries.tenantId;

/**
 * HR cockpit queries. Same rules as the Sales cockpit (see the 2026-10-27
 * OutOfMemoryError): PLAIN SQL only, an explicit tenant_id = :tenantId on
 * every table read (Hibernate's tenant filter does not apply to native SQL),
 * a 20 s timeout, and only aggregated rows or small rosters reach Java.
 *
 * Time: attendance / face-log timestamps are stored in UTC (the JVM is
 * pinned to UTC). Pakistan has no daylight saving, so the Pakistan day of a
 * timestamp is CAST(ts + 5 hours AS DATE) — written the same way on
 * PostgreSQL and H2. Range filters use the UTC bounds of the Pakistan days.
 *
 * Roster: active mart agents (role AGENT) — salesmen no longer check in.
 * Mart filter: attendance rows at that mart; for roster-based numbers
 * (absent, off) the agents who checked in at that mart in the last 30 days.
 */
@Repository
public class HrCockpitQueries {

    @PersistenceContext
    private EntityManager em;

    static String pkt(String col) {
        return "(" + col + " + INTERVAL '5' HOUR)";
    }

    static String pktDate(String col) {
        return "CAST(" + pkt(col) + " AS DATE)";
    }

    /** Seconds since midnight, Pakistan time, of a UTC timestamp column. */
    static String pktSecs(String col) {
        String t = pkt(col);
        return "(EXTRACT(HOUR FROM " + t + ") * 3600 + EXTRACT(MINUTE FROM " + t + ") * 60 + FLOOR(EXTRACT(SECOND FROM " + t + ")))";
    }

    /**
     * Minutes after shift start + grace of a check-in (alias t, agent a), in
     * Pakistan time; negative or zero = on time. The cockpit does NOT trust
     * the saved status / late_minutes: check-in computes them from the UTC
     * clock (JVM pinned to UTC), so a 09:40 Pakistan check-in is saved as
     * "IN", and the 11:59 PM job overwrites LATE with MISSED_END_DUTY. The
     * shift start saved on the row wins over the agent's current one.
     */
    static final String LATE_MINUTES = "((" + pktSecs("t.check_in_time")
            + " - (EXTRACT(HOUR FROM COALESCE(t.shift_start_time, a.shift_start_time, TIME '09:00:00')) * 3600"
            + " + EXTRACT(MINUTE FROM COALESCE(t.shift_start_time, a.shift_start_time, TIME '09:00:00')) * 60)"
            + " - COALESCE(a.grace_period_minutes, 15) * 60) / 60)";

    /** A late check-in: after shift start + grace on a working day. */
    static final String IS_LATE = "(t.status <> 'NON_WORKING_DAY' AND " + LATE_MINUTES + " > 0)";

    /** Agent filters (alias a): tenant, active mart agents, department, mart (seen there in the 30 days before :martSince). */
    private void agentFilters(SalesCockpitQueries.Sql q, HrFilter f, LocalDate today) {
        q.param("tenantId", tenantId());
        q.and("a.tenant_id = :tenantId AND a.is_active = true AND a.role = 'AGENT'");
        if (!f.department().isEmpty()) q.and("a.department = :department", "department", f.department());
        if (f.martId() != null) {
            q.and("EXISTS (SELECT 1 FROM attendance x WHERE x.tenant_id = :tenantId AND x.agent_id = a.id "
                    + "AND x.mart_id = :martId AND x.check_in_time >= :martSince)", "martId", f.martId());
            q.param("martSince", KarachiTime.startOfDayUtc(today.minusDays(30)));
        }
    }

    /** Attendance rows (alias t) joined to their agent (a) and mart (m), within the UTC bounds of the Pakistan days. */
    private SalesCockpitQueries.Sql attendance(String select, HrFilter f, LocalDate from, LocalDate to) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql(select
                + " FROM attendance t JOIN agent a ON a.id = t.agent_id JOIN mart m ON m.id = t.mart_id"
                + " WHERE t.tenant_id = :tenantId AND t.check_in_time >= :fromUtc AND t.check_in_time <= :toUtc");
        q.param("tenantId", tenantId())
                .param("fromUtc", KarachiTime.startOfDayUtc(from))
                .param("toUtc", KarachiTime.endOfDayUtc(to));
        q.and("a.role = 'AGENT'");
        if (!f.department().isEmpty()) q.and("a.department = :department", "department", f.department());
        if (f.martId() != null) q.and("t.mart_id = :martId", "martId", f.martId());
        return q;
    }

    // ------------------------------------------------------------------ roster

    /** Active mart agents: id, name, department, shift start (TIME), grace minutes, created at. Small; working days read separately. */
    public List<Object[]> roster(HrFilter f, LocalDate today) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql(
                "SELECT a.id, a.name, a.department, a.shift_start_time, a.grace_period_minutes, a.created_at FROM agent a WHERE 1 = 1");
        agentFilters(q, f, today);
        return rows(q.add("ORDER BY a.name").build(em));
    }

    // ------------------------------------------------------------------ per day

    /**
     * Per Pakistan day: date, agents checked in, agents late, missed-end-duty
     * shifts, average check-in second (Pakistan time), check-ins farther from
     * the mart than its radius.
     */
    public List<Object[]> dailyAttendance(HrFilter f, LocalDate from, LocalDate to) {
        String day = pktDate("t.check_in_time");
        return rows(attendance("SELECT " + day + ", COUNT(DISTINCT t.agent_id), "
                + "COUNT(DISTINCT CASE WHEN " + IS_LATE + " THEN t.agent_id END), "
                + "COALESCE(SUM(CASE WHEN t.status = 'MISSED_END_DUTY' THEN 1 ELSE 0 END), 0), "
                + "AVG(" + pktSecs("t.check_in_time") + "), "
                + "COALESCE(SUM(CASE WHEN m.radius IS NOT NULL AND t.distance_from_mart > m.radius THEN 1 ELSE 0 END), 0)",
                f, from, to).add("GROUP BY " + day).build(em));
    }

    /** Per Pakistan day: failed face checks of mart agents. */
    public List<Object[]> dailyFaceFailures(HrFilter f, LocalDate from, LocalDate to, LocalDate today) {
        String day = pktDate("fv.verification_time");
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql("SELECT " + day + ", COUNT(*) FROM face_verification_log fv "
                + "JOIN agent a ON a.id = fv.agent_id WHERE fv.tenant_id = :tenantId AND fv.success = false "
                + "AND fv.verification_time >= :fromUtc AND fv.verification_time <= :toUtc");
        q.param("fromUtc", KarachiTime.startOfDayUtc(from)).param("toUtc", KarachiTime.endOfDayUtc(to));
        agentFilters(q, f, today);
        return rows(q.add("GROUP BY " + day).build(em));
    }

    /** Per day: saved absences (V34, written by the 10 PM job). */
    public List<Object[]> dailyAbsences(HrFilter f, LocalDate from, LocalDate to, LocalDate today) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql("SELECT ab.absence_date, COUNT(*) FROM agent_absence ab "
                + "JOIN agent a ON a.id = ab.agent_id WHERE ab.tenant_id = :tenantId AND ab.absence_date BETWEEN :from AND :to");
        q.param("from", from).param("to", to);
        agentFilters(q, f, today);
        return rows(q.add("GROUP BY ab.absence_date").build(em));
    }

    /** Company holidays in a range: date, name. */
    public List<Object[]> holidays(LocalDate from, LocalDate to) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql("SELECT h.holiday_date, h.name FROM company_holiday h "
                + "WHERE h.tenant_id = :tenantId AND h.holiday_date BETWEEN :from AND :to ORDER BY h.holiday_date");
        q.param("tenantId", tenantId()).param("from", from).param("to", to);
        return rows(q.build(em));
    }

    /** Agent ids with a check-in on a Pakistan day. */
    public List<Object[]> checkedInAgentIds(HrFilter f, LocalDate day) {
        return rows(attendance("SELECT DISTINCT t.agent_id", f, day, day).build(em));
    }

    // ------------------------------------------------------------------ needs attention

    /** Saved absences since a date, per agent: agentId, absenceDate (bounded: agents x days). */
    public List<Object[]> absencesSince(HrFilter f, LocalDate since, LocalDate today) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql("SELECT ab.agent_id, ab.absence_date FROM agent_absence ab "
                + "JOIN agent a ON a.id = ab.agent_id WHERE ab.tenant_id = :tenantId AND ab.absence_date >= :since");
        q.param("since", since);
        agentFilters(q, f, today);
        return rows(q.build(em));
    }

    /** Agents late at least minTimes in a range: name, late count (most first). */
    public List<Object[]> lateAtLeast(HrFilter f, LocalDate from, LocalDate to, int minTimes) {
        return rows(attendance("SELECT a.name, COUNT(*)", f, from, to).and(IS_LATE)
                .add("GROUP BY a.id, a.name HAVING COUNT(*) >= :minTimes ORDER BY 2 DESC, a.name")
                .param("minTimes", minTimes).build(em));
    }

    /** Missed end duty per agent in a range: name, count (most first). */
    public List<Object[]> missedEndDuty(HrFilter f, LocalDate from, LocalDate to) {
        return rows(attendance("SELECT a.name, COUNT(*)", f, from, to).and("t.status = 'MISSED_END_DUTY'")
                .add("GROUP BY a.id, a.name ORDER BY 2 DESC, a.name").build(em));
    }

    /** Check-ins farther from the mart than its radius, per agent: name, count, farthest distance (m). */
    public List<Object[]> farFromMart(HrFilter f, LocalDate from, LocalDate to) {
        return rows(attendance("SELECT a.name, COUNT(*), MAX(t.distance_from_mart)", f, from, to)
                .and("m.radius IS NOT NULL AND t.distance_from_mart > m.radius")
                .add("GROUP BY a.id, a.name ORDER BY 2 DESC, a.name").build(em));
    }

    // ------------------------------------------------------------------ today

    /**
     * Today's check-ins per agent: agentId, mart name, first check-in (UTC),
     * last check-out (UTC), still on duty (0/1), minutes late of the first
     * check-in. One row per agent and mart (an agent rarely works two).
     */
    public List<Object[]> todayByAgent(HrFilter f, LocalDate today) {
        return rows(attendance("SELECT t.agent_id, m.name, MIN(t.check_in_time), MAX(t.check_out_time), "
                + "MAX(CASE WHEN t.check_out_time IS NULL THEN 1 ELSE 0 END), "
                + "MAX(CASE WHEN " + IS_LATE + " THEN " + LATE_MINUTES + " ELSE 0 END)", f, today, today)
                .add("GROUP BY t.agent_id, m.name ORDER BY 3").build(em));
    }

    /** Where each roster agent checked in during the last 30 days: agentId, mart name, check-ins. */
    public List<Object[]> usualMarts(HrFilter f, LocalDate today) {
        return rows(attendance("SELECT t.agent_id, m.name, COUNT(*)", f, today.minusDays(30), today)
                .add("GROUP BY t.agent_id, m.name").build(em));
    }

    // ------------------------------------------------------------------ per agent and day

    /**
     * One row per agent and Pakistan day with a check-in: agentId, day, late
     * that day (0/1). Bounded by agents x days (366-day maximum range).
     */
    public List<Object[]> agentDays(HrFilter f, LocalDate from, LocalDate to) {
        String day = pktDate("t.check_in_time");
        return rows(attendance("SELECT t.agent_id, " + day + ", MAX(CASE WHEN " + IS_LATE + " THEN 1 ELSE 0 END)", f, from, to)
                .add("GROUP BY t.agent_id, " + day).build(em));
    }

    /**
     * Per agent: agentId, missed End Duty shifts, seconds worked in closed
     * shifts, days with a closed shift, average check-in second (Pakistan).
     */
    public List<Object[]> agentTotals(HrFilter f, LocalDate from, LocalDate to) {
        String closed = "t.check_out_time IS NOT NULL AND t.status <> 'MISSED_END_DUTY'";
        return rows(attendance("SELECT t.agent_id, "
                + "COALESCE(SUM(CASE WHEN t.status = 'MISSED_END_DUTY' THEN 1 ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN " + closed + " THEN EXTRACT(EPOCH FROM t.check_out_time) - EXTRACT(EPOCH FROM t.check_in_time) END), 0), "
                + "COUNT(DISTINCT CASE WHEN " + closed + " THEN " + pktDate("t.check_in_time") + " END), "
                + "AVG(" + pktSecs("t.check_in_time") + ")", f, from, to)
                .add("GROUP BY t.agent_id").build(em));
    }

    /**
     * One agent's shifts in a range, oldest first: check-in (UTC), check-out
     * (UTC), status, mart name, minutes late, beyond the radius (0/1).
     */
    public List<Object[]> agentShifts(HrFilter f, long agentId, LocalDate from, LocalDate to) {
        return rows(attendance("SELECT t.check_in_time, t.check_out_time, t.status, m.name, "
                + "CASE WHEN " + IS_LATE + " THEN " + LATE_MINUTES + " ELSE 0 END, "
                + "CASE WHEN m.radius IS NOT NULL AND t.distance_from_mart > m.radius THEN 1 ELSE 0 END", f, from, to)
                .and("t.agent_id = :agentId", "agentId", agentId).add("ORDER BY t.check_in_time").build(em));
    }

    /** One active roster agent (same filters as the roster): id, name, department, shift start, grace, created at. */
    public List<Object[]> rosterAgent(HrFilter f, long agentId, LocalDate today) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql("SELECT a.id, a.name, a.department, a.shift_start_time, "
                + "a.grace_period_minutes, a.created_at FROM agent a WHERE a.id = :agentId");
        q.param("agentId", agentId);
        agentFilters(q, f, today);
        return rows(q.build(em));
    }

    // ------------------------------------------------------------------ punctuality

    /** Per agent: agentId, name, department, late check-ins, days present, average minutes late (late ones). */
    public List<Object[]> lateByAgent(HrFilter f, LocalDate from, LocalDate to) {
        return rows(attendance("SELECT a.id, a.name, a.department, "
                + "COALESCE(SUM(CASE WHEN " + IS_LATE + " THEN 1 ELSE 0 END), 0), "
                + "COUNT(DISTINCT " + pktDate("t.check_in_time") + "), "
                + "AVG(CASE WHEN " + IS_LATE + " THEN " + LATE_MINUTES + " END)", f, from, to)
                .add("GROUP BY a.id, a.name, a.department").build(em));
    }

    /** Check-ins per 15-minute slot (Pakistan time): slot index (0-95), on time, late. */
    public List<Object[]> checkInSlots(HrFilter f, LocalDate from, LocalDate to) {
        String slot = "FLOOR(" + pktSecs("t.check_in_time") + " / 900)";
        return rows(attendance("SELECT " + slot + ", "
                + "COALESCE(SUM(CASE WHEN " + IS_LATE + " THEN 0 ELSE 1 END), 0), "
                + "COALESCE(SUM(CASE WHEN " + IS_LATE + " THEN 1 ELSE 0 END), 0)", f, from, to)
                .add("GROUP BY " + slot + " ORDER BY 1").build(em));
    }

    // ------------------------------------------------------------------ locations

    /** Per mart: id, name, geofencing, radius, check-ins, agents, late check-ins, far check-ins, max distance. */
    public List<Object[]> byMart(HrFilter f, LocalDate from, LocalDate to) {
        return rows(attendance("SELECT m.id, m.name, m.geo_fencing_enabled, m.radius, COUNT(*), COUNT(DISTINCT t.agent_id), "
                + "COALESCE(SUM(CASE WHEN " + IS_LATE + " THEN 1 ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN m.radius IS NOT NULL AND t.distance_from_mart > m.radius THEN 1 ELSE 0 END), 0), "
                + "MAX(t.distance_from_mart)", f, from, to)
                .add("GROUP BY m.id, m.name, m.geo_fencing_enabled, m.radius ORDER BY 5 DESC, m.name").build(em));
    }

    /** The farthest check-ins beyond the mart's radius: check-in (UTC), agent, mart, distance, radius, geofencing. */
    public List<Object[]> distanceOutliers(HrFilter f, LocalDate from, LocalDate to, int limit) {
        return rows(attendance("SELECT t.check_in_time, a.name, m.name, t.distance_from_mart, m.radius, m.geo_fencing_enabled", f, from, to)
                .and("m.radius IS NOT NULL AND t.distance_from_mart > m.radius")
                .add("ORDER BY t.distance_from_mart DESC, t.check_in_time DESC").build(em).setMaxResults(limit));
    }

    // ------------------------------------------------------------------ filter options

    public List<Object[]> activeMarts() {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql("SELECT m.id, m.name FROM mart m "
                + "WHERE m.tenant_id = :tenantId AND m.is_active = true ORDER BY m.name");
        return rows(q.param("tenantId", tenantId()).build(em));
    }

    public List<Object[]> departments() {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql("SELECT DISTINCT a.department FROM agent a "
                + "WHERE a.tenant_id = :tenantId AND a.is_active = true AND a.role = 'AGENT' "
                + "AND a.department IS NOT NULL AND a.department <> '' ORDER BY 1");
        return rows(q.param("tenantId", tenantId()).build(em));
    }
}
