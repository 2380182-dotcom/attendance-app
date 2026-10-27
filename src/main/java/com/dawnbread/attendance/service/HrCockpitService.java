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
import com.dawnbread.attendance.util.KarachiTime;
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

    /** One roster agent with what the day rules need. joined = account creation day (null = unknown). */
    record RosterAgent(Long id, String name, String department, LocalTime shiftStart, int graceMinutes,
                       List<String> workingDays, LocalDate joined) {
        boolean worksOn(LocalDate d) {
            return workingDays == null || workingDays.isEmpty()
                    || workingDays.contains(d.getDayOfWeek().name().substring(0, 3));
        }

        /** Expected at work that day: a working day, not a holiday, and already joined. */
        boolean expectedOn(LocalDate d, Map<LocalDate, String> holidays) {
            return worksOn(d) && !holidays.containsKey(d) && (joined == null || !d.isBefore(joined));
        }

        LocalTime dueBy() {
            return (shiftStart != null ? shiftStart : DEFAULT_SHIFT_START).plusMinutes(graceMinutes);
        }
    }

    private List<RosterAgent> roster(HrFilter f, LocalDate today) {
        return toRoster(q.roster(f, today));
    }

    private List<RosterAgent> toRoster(List<Object[]> rows) {
        Map<Long, List<String>> days = base.workingDays(rows.stream().map(r -> id(r[0])).toList());
        List<RosterAgent> out = new ArrayList<>();
        for (Object[] r : rows) {
            Long agentId = id(r[0]);
            int grace = r[4] == null ? DEFAULT_GRACE_MINUTES : (int) lng(r[4]);
            LocalDate joined = r[5] == null ? null : pk((LocalDateTime) r[5]).toLocalDate();
            out.add(new RosterAgent(agentId, (String) r[1], (String) r[2], (LocalTime) r[3], grace, days.get(agentId), joined));
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
        Set<Long> inAnywhere = f.martId() == null ? inToday : checkedIn(f.withoutMart(), today);
        List<RosterAgent> absent = absentNow(roster, inAnywhere, today, now, holiday);
        long expected = holiday ? 0 : roster.stream().filter(a -> a.worksOn(today)).count();
        long notYetDue = holiday ? 0 : roster.stream()
                .filter(a -> a.worksOn(today) && !inAnywhere.contains(a.id()) && now.isBefore(a.dueBy())).count();
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
        absentNow(roster, checkedIn(f.withoutMart(), today), today, now, holidays.containsKey(today)).forEach(a -> absentToday.add(a.id()));

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

    // ================================================================ Today (live board)

    private static final List<String> STATE_ORDER = List.of("ABSENT", "LATE", "NOT_DUE", "ON_TIME", "OFF");

    @Cacheable(cacheNames = CacheConfig.HR_COCKPIT, sync = true, key = KEY_PREFIX + "today:' + #f.cacheKey()")
    public HrToday today(HrFilter f) {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        List<RosterAgent> roster = roster(f, today);
        String holiday = holidays(today, today).get(today);

        // Check-ins at any mart decide the state; the mart filter only picks the roster.
        Map<Long, Object[]> in = new HashMap<>();
        q.todayByAgent(f.withoutMart(), today).forEach(r -> in.merge(id(r[0]), r, (first, more) -> new Object[] {
                first[0], first[1], first[2], later((LocalDateTime) first[3], (LocalDateTime) more[3]),
                Math.max(lng(first[4]), lng(more[4])), first[5] }));
        Map<Long, String> usual = usualMarts(f.withoutMart(), today);

        List<TodayAgent> agents = new ArrayList<>();
        for (RosterAgent a : roster) {
            Object[] r = in.get(a.id());
            String state;
            String checkIn = null;
            String checkOut = null;
            boolean onDuty = false;
            Integer minutesLate = null;
            String mart = usual.get(a.id());
            if (r != null) {
                long late = Math.round(num(r[5]));
                state = late > 0 ? "LATE" : "ON_TIME";
                minutesLate = late > 0 ? (int) late : null;
                mart = (String) r[1];
                checkIn = hhmm(pk((LocalDateTime) r[2]));
                onDuty = lng(r[4]) == 1;
                checkOut = onDuty || r[3] == null ? null : hhmm(pk((LocalDateTime) r[3]));
            } else if (holiday != null || !a.worksOn(today)) {
                state = "OFF";
            } else if (now.isBefore(a.dueBy())) {
                state = "NOT_DUE";
            } else {
                state = "ABSENT";
            }
            agents.add(new TodayAgent(a.id(), a.name(), blankAs(a.department(), "No department"), mart, state,
                    checkIn, checkOut, onDuty, minutesLate, hhmm(a.dueBy())));
        }
        agents.sort(Comparator.comparingInt((TodayAgent t) -> STATE_ORDER.indexOf(t.state()))
                .thenComparing(TodayAgent::name, String.CASE_INSENSITIVE_ORDER));

        return new HrToday(LocalDateTime.now(clock), today, holiday,
                groups(agents, TodayAgent::department), groups(agents, t -> t.mart() == null ? "No mart yet" : t.mart()), agents);
    }

    /** The mart each agent checked in at most in the last 30 days. */
    private Map<Long, String> usualMarts(HrFilter f, LocalDate today) {
        Map<Long, String> mart = new HashMap<>();
        Map<Long, Long> best = new HashMap<>();
        for (Object[] r : q.usualMarts(f, today)) {
            Long agentId = id(r[0]);
            long n = lng(r[2]);
            if (n > best.getOrDefault(agentId, 0L)) {
                best.put(agentId, n);
                mart.put(agentId, (String) r[1]);
            }
        }
        return mart;
    }

    private static List<TodayGroup> groups(List<TodayAgent> agents, java.util.function.Function<TodayAgent, String> key) {
        Map<String, int[]> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (TodayAgent a : agents) {
            counts.computeIfAbsent(key.apply(a), k -> new int[5])[STATE_INDEX.get(a.state())]++;
        }
        List<TodayGroup> out = new ArrayList<>();
        counts.forEach((name, c) -> out.add(new TodayGroup(name, c[0], c[1], c[2], c[3], c[4])));
        return out;
    }

    private static final Map<String, Integer> STATE_INDEX = Map.of("ON_TIME", 0, "LATE", 1, "ABSENT", 2, "NOT_DUE", 3, "OFF", 4);

    // ================================================================ Trend

    @Cacheable(cacheNames = CacheConfig.HR_COCKPIT, sync = true, key = KEY_PREFIX + "trend:' + #f.cacheKey()")
    public HrTrend trend(HrFilter f) {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        LocalDate to = f.endDate().isAfter(today) ? today : f.endDate();
        List<RosterAgent> roster = roster(f, today);
        Map<LocalDate, String> holidays = holidays(f.startDate(), to);
        Map<LocalDate, Map<Long, Boolean>> byDay = presence(f, roster, f.startDate(), to);

        List<TrendDay> days = new ArrayList<>();
        int sumExpected = 0, sumPresent = 0, sumLate = 0, sumAbsent = 0;
        for (LocalDate d = f.startDate(); !d.isAfter(to); d = d.plusDays(1)) {
            Map<Long, Boolean> seen = byDay.getOrDefault(d, Map.of());
            int expected = 0, present = 0, late = 0, absent = 0;
            for (RosterAgent a : roster) {
                if (!a.expectedOn(d, holidays)) continue;
                Boolean wasLate = seen.get(a.id());
                if (wasLate != null) {
                    expected++;
                    present++;
                    if (wasLate) late++;
                } else if (!d.equals(today) || !now.isBefore(a.dueBy())) {
                    expected++;   // today: only agents already past shift start + grace
                    absent++;
                }
            }
            days.add(new TrendDay(d, expected, present, late, absent, share(present, expected), share(late, present),
                    share(absent, expected), holidays.get(d)));
            sumExpected += expected;
            sumPresent += present;
            sumLate += late;
            sumAbsent += absent;
        }
        return new HrTrend(days, share(sumPresent, sumExpected), share(sumLate, sumPresent), share(sumAbsent, sumExpected));
    }

    /**
     * Per day, the roster agents who checked in (at any mart) and whether
     * they were late. Bounded by roster x days of the range.
     */
    private Map<LocalDate, Map<Long, Boolean>> presence(HrFilter f, List<RosterAgent> roster, LocalDate from, LocalDate to) {
        Set<Long> ids = new HashSet<>();
        roster.forEach(a -> ids.add(a.id()));
        Map<LocalDate, Map<Long, Boolean>> byDay = new HashMap<>();
        for (Object[] r : q.agentDays(f.withoutMart(), from, to)) {
            Long agentId = id(r[0]);
            if (ids.contains(agentId)) {
                byDay.computeIfAbsent((LocalDate) r[1], k -> new HashMap<>()).put(agentId, lng(r[2]) == 1);
            }
        }
        return byDay;
    }

    // ================================================================ Punctuality

    static final int LATE_AGENTS = 15;
    /** Up to an hour of empty 15-minute slots is shown as is; longer stretches fold into one marker. */
    static final int MAX_EMPTY_SLOTS = 4;

    private static String slotTime(int slot) {
        return String.format("%02d:%02d", slot / 4, (slot % 4) * 15);
    }

    @Cacheable(cacheNames = CacheConfig.HR_COCKPIT, sync = true, key = KEY_PREFIX + "punctuality:' + #f.cacheKey()")
    public HrPunctuality punctuality(HrFilter f) {
        List<LateAgent> lateAgents = new ArrayList<>();
        for (Object[] r : q.lateByAgent(f, f.startDate(), f.endDate())) {
            int late = (int) lng(r[3]);
            if (late > 0) {
                lateAgents.add(new LateAgent(id(r[0]), (String) r[1], blankAs((String) r[2], "No department"), late,
                        (int) lng(r[4]), r[5] == null ? null : round1(num(r[5]))));
            }
        }
        double lateMinutes = lateAgents.stream().mapToDouble(a -> a.avgMinutesLate() == null ? 0 : a.avgMinutesLate() * a.lateCount()).sum();
        lateAgents.sort(Comparator.comparingInt(LateAgent::lateCount).reversed()
                .thenComparing(Comparator.comparing((LateAgent a) -> a.avgMinutesLate() == null ? 0 : a.avgMinutesLate()).reversed())
                .thenComparing(LateAgent::name, String.CASE_INSENSITIVE_ORDER));

        List<Object[]> slots = q.checkInSlots(f, f.startDate(), f.endDate());
        Map<Integer, Object[]> bySlot = new HashMap<>();
        slots.forEach(r -> bySlot.put((int) lng(r[0]), r));
        List<CheckInSlot> distribution = new ArrayList<>();
        int checkIns = 0, late = 0;
        if (!slots.isEmpty()) {
            int first = (int) lng(slots.get(0)[0]);
            int last = (int) lng(slots.get(slots.size() - 1)[0]);
            for (int i = first; i <= last; i++) {
                Object[] r = bySlot.get(i);
                if (r == null) {
                    int end = i;
                    while (end <= last && !bySlot.containsKey(end)) end++;
                    if (end - i > MAX_EMPTY_SLOTS) {   // a long empty stretch folds into one marker
                        distribution.add(new CheckInSlot(slotTime(i), slotTime(end), 0, 0));
                        i = end - 1;
                        continue;
                    }
                }
                int onTime = r == null ? 0 : (int) lng(r[1]);   // short gaps stay visible as empty slots
                int lateHere = r == null ? 0 : (int) lng(r[2]);
                distribution.add(new CheckInSlot(slotTime(i), null, onTime, lateHere));
                checkIns += onTime + lateHere;
                late += lateHere;
            }
        }
        int lateTotal = late;
        return new HrPunctuality(checkIns, late, checkIns == 0 ? null : share(checkIns - late, checkIns),
                lateTotal == 0 ? null : round1(lateMinutes / lateTotal),
                lateAgents.size() > LATE_AGENTS ? new ArrayList<>(lateAgents.subList(0, LATE_AGENTS)) : lateAgents,
                distribution);
    }

    // ================================================================ Agent scorecard

    @Cacheable(cacheNames = CacheConfig.HR_COCKPIT, sync = true, key = KEY_PREFIX + "agents:' + #f.cacheKey()")
    public HrAgents agents(HrFilter f) {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        LocalDate to = f.endDate().isAfter(today) ? today : f.endDate();
        List<RosterAgent> roster = roster(f, today);
        Map<LocalDate, String> holidays = holidays(f.startDate(), to);
        Map<LocalDate, Map<Long, Boolean>> byDay = presence(f, roster, f.startDate(), to);
        Map<Long, Object[]> totals = new HashMap<>();
        q.agentTotals(f.withoutMart(), f.startDate(), to).forEach(r -> totals.put(id(r[0]), r));

        List<AgentScore> rows = new ArrayList<>();
        for (RosterAgent a : roster) {
            int expected = 0, present = 0, late = 0, absent = 0;
            for (LocalDate d = f.startDate(); !d.isAfter(to); d = d.plusDays(1)) {
                Boolean wasLate = byDay.getOrDefault(d, Map.of()).get(a.id());
                if (wasLate != null) {
                    present++;
                    if (wasLate) late++;
                }
                if (!a.expectedOn(d, holidays)) continue;
                if (wasLate != null) expected++;
                else if (!d.equals(today) || !now.isBefore(a.dueBy())) {
                    expected++;
                    absent++;
                }
            }
            Object[] t = totals.get(a.id());
            int closedDays = t == null ? 0 : (int) lng(t[3]);
            Double avgHours = closedDays == 0 ? null : round1(num(t[2]) / 3600.0 / closedDays);
            Double avgCheckIn = t == null || t[4] == null ? null : num(t[4]);
            rows.add(new AgentScore(a.id(), a.name(), blankAs(a.department(), "No department"), expected, present, late, absent,
                    t == null ? 0 : (int) lng(t[1]), avgHours, avgCheckIn, share(expected - absent, expected)));
        }
        return new HrAgents(rows);
    }

    /**
     * One agent, day by day (newest first). Only active mart agents of the
     * caller's company (null = not found). The mart filter is ignored: the
     * panel shows every shift of that agent.
     */
    @Cacheable(cacheNames = CacheConfig.HR_COCKPIT, sync = true, key = KEY_PREFIX + "agent-days:' + #agentId + ':' + #f.cacheKey()")
    public List<AgentDay> agentDays(HrFilter f, long agentId) {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        HrFilter any = new HrFilter(f.startDate(), f.endDate(), "", null);
        List<RosterAgent> found = toRoster(q.rosterAgent(any, agentId, today));
        if (found.isEmpty()) return null;
        RosterAgent a = found.get(0);
        LocalDate to = f.endDate().isAfter(today) ? today : f.endDate();
        Map<LocalDate, String> holidays = holidays(f.startDate(), to);

        Map<LocalDate, List<Object[]>> shifts = new HashMap<>();
        for (Object[] r : q.agentShifts(any, agentId, f.startDate(), to)) {
            shifts.computeIfAbsent(pk((LocalDateTime) r[0]).toLocalDate(), k -> new ArrayList<>()).add(r);
        }
        List<AgentDay> out = new ArrayList<>();
        for (LocalDate d = to; !d.isBefore(f.startDate()); d = d.minusDays(1)) {
            List<Object[]> day = shifts.get(d);
            String holiday = holidays.get(d);
            if (day != null) {
                Object[] first = day.get(0);
                long late = Math.round(num(first[4]));
                boolean open = day.stream().anyMatch(r -> r[1] == null);
                LocalDateTime lastOut = null;
                double seconds = 0;
                boolean closedShift = false;
                boolean missed = false;
                boolean far = false;
                for (Object[] r : day) {
                    lastOut = later(lastOut, (LocalDateTime) r[1]);
                    missed |= lng(r[2]) == 1;
                    far |= lng(r[5]) == 1;
                    if (r[1] != null && lng(r[2]) == 0) {
                        seconds += Duration.between((LocalDateTime) r[0], (LocalDateTime) r[1]).getSeconds();
                        closedShift = true;
                    }
                }
                out.add(new AgentDay(d, late > 0 ? "LATE" : "PRESENT", hhmm(pk((LocalDateTime) first[0])),
                        open || lastOut == null ? null : hhmm(pk(lastOut)), closedShift ? round1(seconds / 3600.0) : null,
                        late > 0 ? (int) late : null, (String) first[3], missed, far, holiday));
                continue;
            }
            String state;
            if (holiday != null) state = "HOLIDAY";
            else if (!a.worksOn(d)) state = "OFF";
            else if (a.joined() != null && d.isBefore(a.joined())) state = "NOT_JOINED";
            else if (d.equals(today) && now.isBefore(a.dueBy())) state = "NOT_DUE";
            else state = "ABSENT";
            out.add(new AgentDay(d, state, null, null, null, null, null, false, false, holiday));
        }
        return out;
    }

    // ================================================================ Locations

    static final int OUTLIERS = 20;

    @Cacheable(cacheNames = CacheConfig.HR_COCKPIT, sync = true, key = KEY_PREFIX + "locations:' + #f.cacheKey()")
    public HrLocations locations(HrFilter f) {
        List<MartLocation> marts = new ArrayList<>();
        for (Object[] r : q.byMart(f, f.startDate(), f.endDate())) {
            marts.add(new MartLocation(id(r[0]), (String) r[1], Boolean.TRUE.equals(r[2]), r[3] == null ? null : num(r[3]),
                    (int) lng(r[4]), (int) lng(r[5]), (int) lng(r[6]), (int) lng(r[7]), r[8] == null ? null : round1(num(r[8]))));
        }
        List<DistanceOutlier> outliers = new ArrayList<>();
        for (Object[] r : q.distanceOutliers(f, f.startDate(), f.endDate(), OUTLIERS)) {
            LocalDateTime at = pk((LocalDateTime) r[0]);
            outliers.add(new DistanceOutlier(at.toLocalDate(), hhmm(at.toLocalTime()), (String) r[1], (String) r[2],
                    round1(num(r[3])), r[4] == null ? null : num(r[4]), Boolean.TRUE.equals(r[5])));
        }
        return new HrLocations(marts, outliers);
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

    /** A UTC timestamp from the database as Pakistan local time. */
    private static LocalDateTime pk(LocalDateTime utc) {
        return KarachiTime.fromUtc(utc);
    }

    private static String hhmm(LocalDateTime t) {
        return hhmm(t.toLocalTime());
    }

    private static String hhmm(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return a == null ? b : b == null ? a : (b.isAfter(a) ? b : a);
    }

    private static String blankAs(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    /** part / whole as a % with one decimal; null when whole is 0. */
    static Double share(int part, int whole) {
        return whole == 0 ? null : round1(part * 100.0 / whole);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

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
