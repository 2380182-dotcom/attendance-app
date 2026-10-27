package com.dawnbread.attendance.service;

import com.dawnbread.attendance.config.CacheConfig;
import com.dawnbread.attendance.dto.cockpit.HrCockpitDTOs.*;
import com.dawnbread.attendance.dto.cockpit.HrFilter;
import com.dawnbread.attendance.dto.cockpit.SalesCockpitDTOs.Alert;
import com.dawnbread.attendance.dto.cockpit.SalesCockpitDTOs.AlertItem;
import com.dawnbread.attendance.dto.cockpit.SalesCockpitDTOs.DayValue;
import com.dawnbread.attendance.dto.cockpit.SalesCockpitDTOs.Option;
import com.dawnbread.attendance.repository.HrCockpitQueries;
import com.dawnbread.attendance.repository.SalesCockpitQueries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

import static com.dawnbread.attendance.repository.SalesCockpitQueries.id;
import static com.dawnbread.attendance.repository.SalesCockpitQueries.lng;
import static com.dawnbread.attendance.repository.SalesCockpitQueries.num;

/**
 * HR cockpit (mart agents). All counting happens in HrCockpitQueries; this
 * class applies the HR day rules to small rosters and grouped rows:
 *
 * - working day: the agent's workingDays (empty/none = every day, the same
 *   rule as ShiftValidationService) and not a company holiday (V35);
 * - absent TODAY: working day, no check-in, and shift start + grace has
 *   passed (Pakistan time); earlier days come from the saved absence
 *   records (V34, written by the 10 PM job);
 * - off today: not a working day (or everyone, on a company holiday).
 *
 * Cached 90 s per tenant + filter; identical requests share one computation.
 */
@Service
@Transactional(readOnly = true)
public class HrCockpitService {

    static final int SPARK_DAYS = 14;
    static final int ALERT_ITEMS = 10;
    static final int LATE_TIMES_THIS_WEEK = 3;
    static final int ABSENT_STREAK_DAYS = 2;
    static final LocalTime DEFAULT_SHIFT_START = LocalTime.of(9, 0);
    static final int DEFAULT_GRACE_MINUTES = 15;

    private static final String KEY_PREFIX = "T(com.dawnbread.attendance.security.TenantContext).getTenantId() + ':";

    @Autowired
    private HrCockpitQueries q;
    @Autowired
    private SalesCockpitQueries base;
    @Autowired
    private Clock clock;

    /** One roster agent with what the day rules need. */
    record RosterAgent(Long id, String name, LocalTime shiftStart, int graceMinutes, List<String> workingDays) {
        boolean worksOn(LocalDate d) {
            return workingDays == null || workingDays.isEmpty()
                    || workingDays.contains(d.getDayOfWeek().name().substring(0, 3));
        }

        LocalTime dueBy() {
            return (shiftStart != null ? shiftStart : DEFAULT_SHIFT_START).plusMinutes(graceMinutes);
        }
    }

    private List<RosterAgent> roster(HrFilter f, LocalDate today) {
        List<Object[]> rows = q.roster(f, today);
        Map<Long, List<String>> days = base.workingDays(rows.stream().map(r -> id(r[0])).toList());
        List<RosterAgent> out = new ArrayList<>();
        for (Object[] r : rows) {
            Long agentId = id(r[0]);
            int grace = r[4] == null ? DEFAULT_GRACE_MINUTES : (int) lng(r[4]);
            out.add(new RosterAgent(agentId, (String) r[1], (LocalTime) r[3], grace, days.get(agentId)));
        }
        return out;
    }

    private Map<LocalDate, String> holidays(LocalDate from, LocalDate to) {
        Map<LocalDate, String> m = new HashMap<>();
        q.holidays(from, to).forEach(r -> m.put((LocalDate) r[0], (String) r[1]));
        return m;
    }

    private Set<Long> checkedIn(HrFilter f, LocalDate day) {
        Set<Long> ids = new HashSet<>();
        q.checkedInAgentIds(f, day).forEach(r -> ids.add(id(r[0])));
        return ids;
    }

    /** Absent right now: working day, not a holiday, no check-in, and their shift start + grace has passed. */
    private List<RosterAgent> absentNow(List<RosterAgent> roster, Set<Long> checkedIn, LocalDate today, LocalTime now,
                                        boolean holiday) {
        if (holiday) return List.of();
        return roster.stream()
                .filter(a -> a.worksOn(today) && !checkedIn.contains(a.id()) && !now.isBefore(a.dueBy()))
                .toList();
    }

    // ================================================================ Highlights

    @Cacheable(cacheNames = CacheConfig.HR_COCKPIT, sync = true, key = KEY_PREFIX + "highlights:' + #f.cacheKey()")
    public HrHighlights highlights(HrFilter f) {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        LocalDate sparkStart = today.minusDays(SPARK_DAYS - 1);

        List<RosterAgent> roster = roster(f, today);
        Map<LocalDate, String> holidays = holidays(sparkStart, today);
        String todayHoliday = holidays.get(today);
        boolean holiday = todayHoliday != null;

        Map<LocalDate, Object[]> daily = new HashMap<>();
        q.dailyAttendance(f, sparkStart, today).forEach(r -> daily.put((LocalDate) r[0], r));
        Map<LocalDate, Double> face = SalesCockpitQueries.byDate(q.dailyFaceFailures(f, sparkStart, today, today), 1);
        Map<LocalDate, Double> absences = SalesCockpitQueries.byDate(q.dailyAbsences(f, sparkStart, today.minusDays(1), today), 1);

        Set<Long> inToday = checkedIn(f, today);
        List<RosterAgent> absent = absentNow(roster, inToday, today, now, holiday);
        long expected = holiday ? 0 : roster.stream().filter(a -> a.worksOn(today)).count();
        long notYetDue = holiday ? 0 : roster.stream()
                .filter(a -> a.worksOn(today) && !inToday.contains(a.id()) && now.isBefore(a.dueBy())).count();
        long off = holiday ? roster.size() : roster.stream().filter(a -> !a.worksOn(today)).count();

        List<HrCard> cards = new ArrayList<>();

        List<DayValue> inSpark = spark(sparkStart, today, d -> col(daily, d, 1));
        double inNow = inToday.size();
        // Nobody expected (holiday, or a day off for everyone): no "x / 0", just who came in anyway.
        cards.add(new HrCard("checked_in", "Checked in today", "count", inNow, expected > 0 ? (double) expected : null,
                holiday ? "Company holiday: " + todayHoliday + " · nobody expected"
                        : expected == 0 ? "Nobody is expected today"
                        : "of " + expected + " expected today" + (notYetDue > 0 ? " · " + notYetDue + " not due yet" : ""),
                inSpark, pct(inNow, weekAgo(inSpark)), "%", "vs same day last week", true, "ok", "today"));

        List<DayValue> lateSpark = spark(sparkStart, today, d -> col(daily, d, 2));
        double lateNow = last(lateSpark);
        cards.add(new HrCard("late", "Late today", "count", lateNow, null,
                "of " + (long) inNow + " checked in · after shift start + grace",
                lateSpark, pct(lateNow, weekAgo(lateSpark)), "%", "vs same day last week", false, "late", "punctuality"));

        List<DayValue> absentSpark = spark(sparkStart, today, d -> d.equals(today) ? absent.size() : absences.getOrDefault(d, 0.0));
        cards.add(new HrCard("absent", "Absent today", "count", (double) absent.size(), null,
                holiday ? "Company holiday — nobody is absent"
                        : "Working today, no check-in after shift start + grace",
                absentSpark, pct(absent.size(), weekAgo(absentSpark)), "%", "vs same day last week", false, "alert", "today"));

        cards.add(new HrCard("off", "Off today", "count", (double) off, (double) roster.size(),
                holiday ? "Company holiday: " + todayHoliday : "Not a working day for them · of " + roster.size() + " agents",
                spark(sparkStart, today, d -> holidays.containsKey(d) ? roster.size()
                        : roster.stream().filter(a -> !a.worksOn(d)).count()),
                null, "%", "", null, "neutral", "today"));

        List<DayValue> missedSpark = spark(sparkStart, today, d -> col(daily, d, 3));
        double missedYesterday = valueOn(missedSpark, today.minusDays(1));
        cards.add(new HrCard("missed_end_duty", "Missed end duty", "count", missedYesterday, null,
                "Yesterday · shifts closed automatically at 11:59 PM",
                missedSpark, pct(missedYesterday, valueOn(missedSpark, today.minusDays(8))), "%", "vs a week before",
                false, "alert", "agents"));

        List<DayValue> timeSpark = new ArrayList<>();
        for (LocalDate d = sparkStart; !d.isAfter(today); d = d.plusDays(1)) {
            Object[] r = daily.get(d);
            if (r != null && r[4] != null) timeSpark.add(new DayValue(d, num(r[4])));
        }
        Object[] t = daily.get(today);
        Double avgNow = t == null || t[4] == null ? null : num(t[4]);
        Object[] w = daily.get(today.minusDays(7));
        Double minutesLater = avgNow == null || w == null || w[4] == null ? null : (avgNow - num(w[4])) / 60.0;
        cards.add(new HrCard("avg_check_in", "Average check-in", "time", avgNow, null,
                avgNow == null ? "No check-ins yet today" : "Today, Pakistan time",
                timeSpark, minutesLater, " min", minutesLater == null ? "" : (minutesLater >= 0 ? "later than last week" : "earlier than last week"),
                false, "neutral", "punctuality"));

        List<DayValue> faceSpark = spark(sparkStart, today, d -> face.getOrDefault(d, 0.0));
        double faceNow = last(faceSpark);
        cards.add(new HrCard("face_failures", "Face check failures", "count", faceNow, null,
                "Today · failed face verifications",
                faceSpark, pct(faceNow, weekAgo(faceSpark)), "%", "vs same day last week", false, "alert", "agents"));

        List<DayValue> farSpark = spark(sparkStart, today, d -> col(daily, d, 5));
        double farNow = last(farSpark);
        cards.add(new HrCard("far_from_mart", "Check-ins far from mart", "count", farNow, null,
                "Today · beyond the mart's radius (only possible where geofencing is off)",
                farSpark, pct(farNow, weekAgo(farSpark)), "%", "vs same day last week", false, "alert", "locations"));

        return new HrHighlights(LocalDateTime.now(clock), today, todayHoliday, cards);
    }

    // ================================================================ Needs attention

    @Cacheable(cacheNames = CacheConfig.HR_COCKPIT, sync = true, key = KEY_PREFIX + "attention:' + #f.cacheKey()")
    public HrAttention attention(HrFilter f) {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        List<Alert> alerts = new ArrayList<>();

        // Absent 2+ working days in a row (saved absences + today's live absence).
        List<RosterAgent> roster = roster(f, today);
        LocalDate since = today.minusDays(21);
        Map<LocalDate, String> holidays = holidays(since, today);
        Map<Long, Set<LocalDate>> absentDays = new HashMap<>();
        q.absencesSince(f, since, today).forEach(r -> absentDays.computeIfAbsent(id(r[0]), k -> new HashSet<>()).add((LocalDate) r[1]));
        Set<Long> absentToday = new HashSet<>();
        absentNow(roster, checkedIn(f, today), today, now, holidays.containsKey(today)).forEach(a -> absentToday.add(a.id()));

        List<AlertItem> streaks = new ArrayList<>();
        List<Integer> streakLengths = new ArrayList<>();
        for (RosterAgent a : roster) {
            Set<LocalDate> days = absentDays.getOrDefault(a.id(), Set.of());
            int streak = 0;
            LocalDate d = today;
            if (absentToday.contains(a.id())) {
                streak = 1;
                d = today.minusDays(1);
            } else {
                d = today.minusDays(1); // today not decided yet (or present): count back from yesterday
            }
            for (; !d.isBefore(since); d = d.minusDays(1)) {
                if (!a.worksOn(d) || holidays.containsKey(d)) continue; // days off don't break or extend a streak
                if (days.contains(d)) streak++;
                else break;
            }
            if (streak >= ABSENT_STREAK_DAYS) {
                streaks.add(new AlertItem(a.name(), streak + " working days"));
                streakLengths.add(streak);
            }
        }
        if (!streaks.isEmpty()) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < streaks.size(); i++) order.add(i);
            order.sort(Comparator.comparingInt((Integer i) -> -streakLengths.get(i)));
            List<AlertItem> sorted = order.stream().map(streaks::get).toList();
            alerts.add(new Alert("absent_streak", "CRITICAL",
                    count(sorted.size(), "agent", "agents") + " absent " + ABSENT_STREAK_DAYS + "+ days in a row",
                    "Consecutive working days without a check-in (days off and holidays skipped)",
                    sorted.size(), "today", head(sorted)));
        }

        // Late 3+ times this week (Monday to today).
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        List<AlertItem> late = items(q.lateAtLeast(f, monday, today, LATE_TIMES_THIS_WEEK), "time", "times");
        if (!late.isEmpty()) {
            alerts.add(new Alert("late_week", "WARNING",
                    count(late.size(), "agent", "agents") + " late " + LATE_TIMES_THIS_WEEK + "+ times this week",
                    "Since Monday " + monday, late.size(), "punctuality", head(late)));
        }

        // Missed end duty in the last 7 days.
        List<Object[]> missed = q.missedEndDuty(f, today.minusDays(7), today.minusDays(1));
        if (!missed.isEmpty()) {
            long shifts = missed.stream().mapToLong(r -> lng(r[1])).sum();
            alerts.add(new Alert("missed_end_duty", "WARNING",
                    count(shifts, "shift", "shifts") + " without End Duty in the last 7 days",
                    "Closed automatically at 11:59 PM — hours for those days are not real", (int) shifts, "agents",
                    head(items(missed, "shift", "shifts"))));
        }

        // Check-ins farther than the mart's radius in the last 7 days.
        List<Object[]> far = q.farFromMart(f, today.minusDays(6), today);
        if (!far.isEmpty()) {
            long checkIns = far.stream().mapToLong(r -> lng(r[1])).sum();
            List<AlertItem> farItems = new ArrayList<>();
            far.forEach(r -> farItems.add(new AlertItem((String) r[0],
                    count(lng(r[1]), "check-in", "check-ins") + " · farthest " + Math.round(num(r[2])) + " m")));
            alerts.add(new Alert("far_from_mart", "WARNING",
                    count(checkIns, "check-in", "check-ins") + " far from the mart in the last 7 days",
                    "Beyond the mart's radius — only possible at marts with geofencing switched off", (int) checkIns,
                    "locations", head(farItems)));
        }

        alerts.sort(Comparator.comparingInt((Alert a) -> "CRITICAL".equals(a.severity()) ? 0 : 1)
                .thenComparing(Comparator.comparingInt(Alert::count).reversed()));
        return new HrAttention(LocalDateTime.now(clock), alerts);
    }

    // ================================================================ Filter options

    @Cacheable(cacheNames = CacheConfig.HR_COCKPIT, sync = true, key = KEY_PREFIX + "options'")
    public HrFilterOptions filterOptions() {
        List<Option> marts = new ArrayList<>();
        q.activeMarts().forEach(r -> marts.add(new Option(id(r[0]), (String) r[1], null)));
        List<Option> departments = new ArrayList<>();
        q.departments().forEach(r -> departments.add(new Option(null, (String) r[0], null)));
        return new HrFilterOptions(marts, departments);
    }

    // ================================================================ helpers

    private static double col(Map<LocalDate, Object[]> daily, LocalDate d, int i) {
        Object[] r = daily.get(d);
        return r == null ? 0 : num(r[i]);
    }

    private interface DayFn {
        double value(LocalDate d);
    }

    private static List<DayValue> spark(LocalDate from, LocalDate to, DayFn fn) {
        List<DayValue> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) out.add(new DayValue(d, fn.value(d)));
        return out;
    }

    private static double last(List<DayValue> s) {
        return s.isEmpty() ? 0 : s.get(s.size() - 1).value();
    }

    private static double weekAgo(List<DayValue> s) {
        return s.size() >= 8 ? s.get(s.size() - 8).value() : 0;
    }

    private static double valueOn(List<DayValue> s, LocalDate d) {
        return s.stream().filter(v -> v.date().equals(d)).mapToDouble(DayValue::value).findFirst().orElse(0);
    }

    static Double pct(double current, double previous) {
        return previous == 0 ? null : (current - previous) * 100.0 / Math.abs(previous);
    }

    private static String count(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    private static List<AlertItem> items(List<Object[]> rows, String one, String many) {
        List<AlertItem> out = new ArrayList<>();
        rows.forEach(r -> out.add(new AlertItem((String) r[0], count(lng(r[1]), one, many))));
        return out;
    }

    private static List<AlertItem> head(List<AlertItem> items) {
        return items.size() > ALERT_ITEMS ? new ArrayList<>(items.subList(0, ALERT_ITEMS)) : items;
    }
}
