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

    /** Active mart agents: id, name, department, shift start (TIME), grace minutes. Small; working days read separately. */
    public List<Object[]> roster(HrFilter f, LocalDate today) {
        SalesCockpitQueries.Sql q = new SalesCockpitQueries.Sql(
                "SELECT a.id, a.name, a.department, a.shift_start_time, a.grace_period_minutes FROM agent a WHERE 1 = 1");
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
                + "COUNT(DISTINCT CASE WHEN t.status = 'LATE' THEN t.agent_id END), "
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
        return rows(attendance("SELECT a.name, COUNT(*)", f, from, to).and("t.status = 'LATE'")
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
