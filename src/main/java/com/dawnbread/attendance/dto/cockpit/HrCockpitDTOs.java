package com.dawnbread.attendance.dto.cockpit;

import com.dawnbread.attendance.dto.cockpit.SalesCockpitDTOs.Alert;
import com.dawnbread.attendance.dto.cockpit.SalesCockpitDTOs.DayValue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Response shapes for the HR cockpit. Every count comes from the database. */
public final class HrCockpitDTOs {

    private HrCockpitDTOs() {
    }

    /**
     * One highlight card. kind = "count" or "time" (value in seconds since
     * midnight, Pakistan time). delta is a % for counts and MINUTES for the
     * time card (deltaUnit says which); null = nothing to compare with.
     */
    public record HrCard(String key, String label, String kind, Double value, Double secondaryValue, String context,
                         List<DayValue> sparkline, Double delta, String deltaUnit, String deltaLabel,
                         Boolean higherIsBetter, String meaning, String section) {
    }

    /** holiday = today's company holiday name, null on a normal day. */
    public record HrHighlights(LocalDateTime generatedAt, LocalDate today, String holiday, List<HrCard> cards) {
    }

    public record HrAttention(LocalDateTime generatedAt, List<Alert> alerts) {
    }

    public record HrFilterOptions(List<SalesCockpitDTOs.Option> marts, List<SalesCockpitDTOs.Option> departments) {
    }

    // ------------------------------------------------------------------ Today

    /**
     * One agent on today's live board. state: ON_TIME, LATE, ABSENT,
     * NOT_DUE (shift start + grace not reached yet), OFF (day off / holiday).
     * mart = where they checked in today, else where they usually check in
     * (last 30 days), else null. Times are Pakistan time (HH:mm).
     */
    public record TodayAgent(Long agentId, String name, String department, String mart, String state,
                             String checkIn, String checkOut, boolean onDuty, Integer minutesLate, String dueBy) {
    }

    /** Counts per department or mart. */
    public record TodayGroup(String name, int onTime, int late, int absent, int notDue, int off) {
    }

    public record HrToday(LocalDateTime generatedAt, LocalDate today, String holiday, List<TodayGroup> departments,
                          List<TodayGroup> marts, List<TodayAgent> agents) {
    }

    // ------------------------------------------------------------------ Trend

    /**
     * One day. expected = roster agents working that day (joined by then, not
     * a holiday); present = expected agents who checked in; absent = expected
     * minus present (today: only those already past shift start + grace);
     * late = present agents with a late check-in. Percentages are null when
     * nobody was expected.
     */
    public record TrendDay(LocalDate date, int expected, int present, int late, int absent, Double attendancePct,
                           Double latePct, Double absentPct, String holiday) {
    }

    public record HrTrend(List<TrendDay> days, Double attendancePct, Double latePct, Double absentPct) {
    }

    // ------------------------------------------------------------------ Punctuality

    /** Late check-ins of one agent in the period; avgMinutesLate over the late ones. */
    public record LateAgent(Long agentId, String name, String department, int lateCount, int daysPresent,
                            Double avgMinutesLate) {
    }

    /**
     * Check-ins starting in a 15-minute slot (Pakistan time), split on time /
     * late. until != null marks a folded stretch with no check-ins at all,
     * from time to until (longer than an hour), so night and day shifts fit
     * on one chart.
     */
    public record CheckInSlot(String time, String until, int onTime, int late) {
    }

    public record HrPunctuality(int checkIns, int lateCheckIns, Double onTimePct, Double avgMinutesLate,
                                List<LateAgent> lateByAgent, List<CheckInSlot> distribution) {
    }

    // ------------------------------------------------------------------ Agent scorecard

    /** avgCheckIn in seconds since midnight (Pakistan); avgHours over days with a closed shift. */
    public record AgentScore(Long agentId, String name, String department, int daysExpected, int daysPresent,
                             int daysLate, int daysAbsent, int missedEndDuty, Double avgHours, Double avgCheckIn,
                             Double attendancePct) {
    }

    public record HrAgents(List<AgentScore> rows) {
    }

    /**
     * One day of one agent. state: PRESENT, LATE, ABSENT, OFF, HOLIDAY,
     * NOT_DUE, NOT_JOINED. hours = closed shifts only (missed End Duty
     * shifts are not real hours).
     */
    public record AgentDay(LocalDate date, String state, String checkIn, String checkOut, Double hours,
                           Integer minutesLate, String mart, boolean missedEndDuty, boolean farFromMart, String holiday) {
    }

    // ------------------------------------------------------------------ Locations

    public record MartLocation(Long martId, String name, boolean geofencing, Double radius, int checkIns, int agents,
                               int lateCheckIns, int farCheckIns, Double maxDistance) {
    }

    /** A check-in beyond the mart's radius. */
    public record DistanceOutlier(LocalDate date, String time, String agent, String mart, double distance, Double radius,
                                  boolean geofencing) {
    }

    public record HrLocations(List<MartLocation> marts, List<DistanceOutlier> outliers) {
    }
}
