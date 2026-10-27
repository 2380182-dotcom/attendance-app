package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.*;
import com.dawnbread.attendance.repository.*;
import com.dawnbread.attendance.security.TokenProvider;
import com.dawnbread.attendance.util.KarachiTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HR cockpit (step 3): every section. Each test seeds its own tenant. Agents
 * get shift start 00:00 with no grace so "absent today" never depends on
 * what time the test runs; each check-in row carries shift start 09:30 (as
 * check-in saves it), so "late" = after 09:30 Pakistan time whatever status
 * was saved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HrCockpitTest {

    private static final String BASE = "/api/cockpit/hr/";
    private static final List<String> PATHS = List.of("highlights", "attention", "filter-options",
            "today", "trend", "punctuality", "agents", "locations");
    private static final List<String> EVERY_DAY = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private TokenProvider tokenProvider;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private AgentRepository agentRepository;
    @Autowired
    private MartRepository martRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private AgentAbsenceRepository absenceRepository;
    @Autowired
    private CompanyHolidayRepository holidayRepository;
    @Autowired
    private FaceVerificationLogRepository faceRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalDate today = LocalDate.now(KarachiTime.ZONE);

    // ------------------------------------------------------------------ seeding

    private Tenant tenant(String label) {
        Tenant t = new Tenant();
        t.setCompanyCode("HR" + label + (System.nanoTime() % 1_000_000));
        t.setName("HR " + label);
        t.setIsActive(true);
        t.setCreatedAt(LocalDateTime.now());
        t.setCreatedBy("TEST");
        return tenantRepository.save(t);
    }

    private Agent agent(Tenant t, String name, String role, String department, List<String> workingDays) {
        Agent a = new Agent();
        a.setTenantId(t.getId());
        String id = "HR" + System.nanoTime();
        a.setAgentId(id);
        a.setName(name);
        a.setEmail(id.toLowerCase() + "@example.com");
        a.setRole(role);
        a.setDepartment(department);
        a.setIsActive(true);
        a.setWorkingDays(workingDays);
        a.setShiftStartTime(LocalTime.MIDNIGHT);
        a.setGracePeriodMinutes(0);
        a.setCreatedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        return agentRepository.save(a);
    }

    private Mart mart(Tenant t, String name) {
        Mart m = new Mart();
        m.setTenantId(t.getId());
        m.setName(name);
        m.setAddress("Test");
        m.setLatitude(31.5);
        m.setLongitude(74.3);
        m.setRadius(100.0);
        m.setGeoFencingEnabled(false);
        m.setIsActive(true);
        m.setCreatedAt(LocalDateTime.now());
        return martRepository.save(m);
    }

    /** A closed one-minute shift on a Pakistan day at a Pakistan time. */
    private void checkIn(Tenant t, Agent a, Mart m, LocalDate day, LocalTime pkTime, String status, double distance) {
        shift(t, a, m, day, pkTime, status, distance, Duration.ofMinutes(1));
    }

    /** A shift of the given length (null = still on duty). Shift start on the row: 09:30. */
    private void shift(Tenant t, Agent a, Mart m, LocalDate day, LocalTime pkTime, String status, double distance, Duration worked) {
        Attendance at = new Attendance();
        at.setTenantId(t.getId());
        at.setAgent(a);
        at.setMart(m);
        LocalDateTime utc = KarachiTime.startOfDayUtc(day).plusSeconds(pkTime.toSecondOfDay());
        at.setCheckInTime(utc);
        at.setCheckOutTime(worked == null ? null : utc.plus(worked));
        at.setShiftStartTime(LocalTime.of(9, 30));
        at.setStatus(status);
        at.setDistanceFromMart(distance);
        attendanceRepository.save(at);
    }

    private void absence(Tenant t, Agent a, LocalDate day) {
        AgentAbsence ab = new AgentAbsence();
        ab.setTenantId(t.getId());
        ab.setAgentId(a.getId());
        ab.setAbsenceDate(day);
        ab.setCreatedAt(LocalDateTime.now());
        absenceRepository.save(ab);
    }

    private void faceFailure(Tenant t, Agent a) {
        FaceVerificationLog log = new FaceVerificationLog();
        log.setTenantId(t.getId());
        log.setAgentId(a.getId());
        log.setVerificationTime(LocalDateTime.now());
        log.setSuccess(false);
        log.setSimilarityScore(0.2);
        faceRepository.save(log);
    }

    private String token(Tenant t, String role) {
        return tokenProvider.generateToken(999L, "HR_" + role, role, t.getId());
    }

    private ResponseEntity<String> get(String token, String path) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return restTemplate.exchange("http://localhost:" + port + BASE + path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private JsonNode data(String token, String path) throws Exception {
        ResponseEntity<String> r = get(token, path);
        assertEquals(HttpStatus.OK, r.getStatusCode(), path + " -> " + r.getBody());
        return mapper.readTree(r.getBody()).get("data");
    }

    private static JsonNode card(JsonNode h, String key) {
        for (JsonNode c : h.get("cards")) if (key.equals(c.get("key").asText())) return c;
        throw new AssertionError("no card " + key);
    }

    private static JsonNode alert(JsonNode a, String key) {
        for (JsonNode x : a.get("alerts")) if (key.equals(x.get("key").asText())) return x;
        return null;
    }

    private static String notToday(LocalDate today) {
        return today.getDayOfWeek().name().substring(0, 3);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void onlyAdminAndHrCanReadTheHrCockpit() {
        Tenant t = tenant("ROLE");
        for (String role : List.of("SALES", "SALESMAN_LOCAL", "SALESMAN_LMT", "AGENT")) {
            for (String p : PATHS) {
                assertEquals(HttpStatus.FORBIDDEN, get(token(t, role), p).getStatusCode(), role + " " + p);
            }
        }
        for (String role : List.of("ADMIN", "HR")) {
            for (String p : PATHS) {
                assertEquals(HttpStatus.OK, get(token(t, role), p).getStatusCode(), role + " " + p);
            }
        }
        assertEquals(HttpStatus.BAD_REQUEST, get(token(t, "HR"), "highlights?startDate=2026-10-30&endDate=2026-10-01").getStatusCode());
    }

    @Test
    void highlightsCountTodayExactlyWithTheHrDayRules() throws Exception {
        Tenant t = tenant("HL");
        Mart m = mart(t, "Main Mart");
        Agent onTime = agent(t, "On Time", "AGENT", "Bakery", EVERY_DAY);
        Agent late = agent(t, "Late One", "AGENT", "Bakery", EVERY_DAY);
        Agent missing = agent(t, "Missing One", "AGENT", "Bakery", EVERY_DAY);
        Agent dayOff = agent(t, "Day Off", "AGENT", "Bakery",
                EVERY_DAY.stream().filter(d -> !d.equals(notToday(today))).toList());
        Agent emptyDays = agent(t, "No Days Set", "AGENT", "Bakery", List.of()); // empty = works every day
        agent(t, "A Salesman", "SALESMAN_LMT", "Sales", EVERY_DAY);              // never counted by HR

        checkIn(t, onTime, m, today, LocalTime.of(9, 0), "IN", 500);   // far beyond the 100 m radius
        checkIn(t, late, m, today, LocalTime.of(10, 0), "LATE", 20);
        checkIn(t, emptyDays, m, today, LocalTime.of(9, 30), "IN", 10);
        checkIn(t, onTime, m, today.minusDays(1), LocalTime.of(9, 0), "MISSED_END_DUTY", 10);
        absence(t, missing, today.minusDays(1));
        faceFailure(t, late);

        JsonNode h = data(token(t, "HR"), "highlights");
        assertTrue(h.get("holiday").isNull());
        assertEquals(3, card(h, "checked_in").get("value").asLong());
        assertEquals(4, card(h, "checked_in").get("secondaryValue").asLong(), "expected = working today (day-off agent excluded)");
        assertEquals(1, card(h, "late").get("value").asLong());
        assertEquals(1, card(h, "absent").get("value").asLong(), "only 'Missing One'");
        assertEquals(1, card(h, "off").get("value").asLong());
        assertEquals(1, card(h, "missed_end_duty").get("value").asLong(), "yesterday");
        assertEquals("time", card(h, "avg_check_in").get("kind").asText());
        assertEquals(9 * 3600 + 30 * 60, card(h, "avg_check_in").get("value").asDouble(), 1.0, "avg of 09:00, 10:00, 09:30");
        assertEquals(1, card(h, "face_failures").get("value").asLong());
        assertEquals(1, card(h, "far_from_mart").get("value").asLong());
        JsonNode absentSpark = card(h, "absent").get("sparkline");
        assertEquals(14, absentSpark.size());
        assertEquals(1.0, absentSpark.get(12).get("value").asDouble(), 0.001, "yesterday from the saved absence record");

        // Department filter: nobody in Dairy.
        JsonNode dairy = data(token(t, "HR"), "highlights?department=Dairy");
        assertEquals(0, card(dairy, "checked_in").get("value").asLong());
        assertEquals(0, card(dairy, "absent").get("value").asLong());

        // Mart filter: an empty mart has no check-ins and no roster (nobody checked in there).
        Mart other = mart(t, "Other Mart");
        JsonNode otherMart = data(token(t, "HR"), "highlights?martId=" + other.getId());
        assertEquals(0, card(otherMart, "checked_in").get("value").asLong());
        assertEquals(0, card(otherMart, "absent").get("value").asLong());
        assertEquals(3, card(data(token(t, "HR"), "highlights?martId=" + m.getId()), "checked_in").get("value").asLong());
    }

    @Test
    void onACompanyHolidayNobodyIsAbsentAndEveryoneIsOff() throws Exception {
        Tenant t = tenant("HOL");
        agent(t, "Agent One", "AGENT", "Bakery", EVERY_DAY);
        agent(t, "Agent Two", "AGENT", "Bakery", EVERY_DAY);
        CompanyHoliday h = new CompanyHoliday();
        h.setTenantId(t.getId());
        h.setHolidayDate(today);
        h.setName("Iqbal Day");
        h.setCreatedAt(LocalDateTime.now());
        holidayRepository.save(h);

        JsonNode hl = data(token(t, "ADMIN"), "highlights");
        assertEquals("Iqbal Day", hl.get("holiday").asText());
        assertEquals(0, card(hl, "absent").get("value").asLong());
        assertEquals(2, card(hl, "off").get("value").asLong());
        assertFalse(card(hl, "checked_in").path("secondaryValue").isNumber(), "nobody is expected on a holiday: no \"x / 0\"");
        assertNull(alert(data(token(t, "ADMIN"), "attention"), "absent_streak"));
    }

    @Test
    void needsAttentionFlagsStreaksLateWeeksMissedEndDutyAndFarCheckIns() throws Exception {
        Tenant t = tenant("ATT");
        Mart m = mart(t, "Mart");
        Agent streaker = agent(t, "Streaker", "AGENT", "Bakery", EVERY_DAY);
        Agent lateOne = agent(t, "Often Late", "AGENT", "Bakery", EVERY_DAY);
        Agent fine = agent(t, "Fine", "AGENT", "Bakery", EVERY_DAY);
        // Absent today (live) + saved absences on the two days before = 3 working days in a row.
        absence(t, streaker, today.minusDays(1));
        absence(t, streaker, today.minusDays(2));
        // Late 3 times today (separate closed shifts) -> "late 3+ this week" whatever weekday it is.
        for (int i = 0; i < 3; i++) checkIn(t, lateOne, m, today, LocalTime.of(10, i), "LATE", 10);
        checkIn(t, fine, m, today, LocalTime.of(9, 0), "IN", 900);
        checkIn(t, fine, m, today.minusDays(2), LocalTime.of(9, 0), "MISSED_END_DUTY", 10);

        JsonNode a = data(token(t, "HR"), "attention");
        JsonNode streak = alert(a, "absent_streak");
        assertNotNull(streak, a.toString());
        assertEquals("CRITICAL", streak.get("severity").asText());
        assertEquals("Streaker", streak.get("items").get(0).get("label").asText());
        assertEquals("3 working days", streak.get("items").get(0).get("value").asText());
        assertEquals("CRITICAL", a.get("alerts").get(0).get("severity").asText(), "most urgent first");

        JsonNode late = alert(a, "late_week");
        assertNotNull(late);
        assertEquals("Often Late", late.get("items").get(0).get("label").asText());
        assertEquals("3 times", late.get("items").get(0).get("value").asText());

        JsonNode missed = alert(a, "missed_end_duty");
        assertNotNull(missed);
        assertEquals(1, missed.get("count").asInt());

        JsonNode far = alert(a, "far_from_mart");
        assertNotNull(far);
        assertTrue(far.get("items").get(0).get("value").asText().contains("farthest 900 m"), far.toString());
    }

    @Test
    void anotherCompanySeesNothingAndAnEmptyCompanyGetsZerosNotErrors() throws Exception {
        Tenant a = tenant("ISOA");
        Tenant b = tenant("ISOB");
        Mart m = mart(a, "A Mart");
        Agent agentA = agent(a, "Tenant A Agent", "AGENT", "Bakery", EVERY_DAY);
        checkIn(a, agentA, m, today, LocalTime.of(10, 0), "IN", 10);   // saved as IN by the UTC clock, but late

        JsonNode hb = data(token(b, "HR"), "highlights");
        for (JsonNode c : hb.get("cards")) {
            if (!c.get("value").isNull()) assertEquals(0.0, c.get("value").asDouble(), 0.001, c.get("key").asText());
        }
        assertTrue(card(hb, "avg_check_in").get("value").isNull());
        assertEquals(0, data(token(b, "HR"), "attention").get("alerts").size());
        JsonNode opts = data(token(b, "HR"), "filter-options");
        assertEquals(0, opts.get("marts").size());
        assertEquals(0, opts.get("departments").size());
        JsonNode optsA = data(token(a, "HR"), "filter-options");
        assertEquals(1, optsA.get("marts").size());
        assertEquals("Bakery", optsA.get("departments").get(0).get("name").asText());
        assertEquals(1, card(data(token(a, "HR"), "highlights"), "late").get("value").asLong());
    }

    // ------------------------------------------------------------------ part 2

    private static JsonNode byName(JsonNode list, String field, String value) {
        for (JsonNode x : list) if (value.equals(x.get(field).asText())) return x;
        throw new AssertionError("no " + field + "=" + value + " in " + list);
    }

    @Test
    void todayBoardShowsEachAgentOnceWithTheirStateAndGroupsThem() throws Exception {
        Tenant t = tenant("TOD");
        Mart main = mart(t, "Main Mart");
        Mart second = mart(t, "Second Mart");
        Agent onTime = agent(t, "On Time", "AGENT", "Bakery", EVERY_DAY);
        Agent late = agent(t, "Late One", "AGENT", "Bakery", EVERY_DAY);
        Agent missing = agent(t, "Missing", "AGENT", "Dairy", EVERY_DAY);
        Agent dayOff = agent(t, "Day Off", "AGENT", "Dairy",
                EVERY_DAY.stream().filter(d -> !d.equals(notToday(today))).toList());
        Agent notDue = agent(t, "Not Due", "AGENT", "Bakery", EVERY_DAY);
        notDue.setShiftStartTime(LocalTime.of(23, 59));
        agentRepository.save(notDue);
        Agent mover = agent(t, "Mover", "AGENT", "Bakery", EVERY_DAY);
        agent(t, "A Salesman", "SALESMAN_LOCAL", "Sales", EVERY_DAY);

        shift(t, onTime, main, today, LocalTime.of(9, 0), "IN", 10, null);       // still on duty
        checkIn(t, late, main, today, LocalTime.of(10, 0), "IN", 10);            // 30 min after 09:30
        checkIn(t, missing, second, today.minusDays(1), LocalTime.of(9, 0), "IN", 10);
        checkIn(t, mover, main, today.minusDays(1), LocalTime.of(9, 0), "IN", 10);
        checkIn(t, mover, second, today, LocalTime.of(9, 10), "IN", 10);         // usual mart: Main; today: Second

        JsonNode d = data(token(t, "HR"), "today");
        JsonNode agents = d.get("agents");
        assertEquals(6, agents.size(), "salesmen are not on the HR board");
        assertEquals("ABSENT", agents.get(0).get("state").asText(), "most urgent first");
        JsonNode o = byName(agents, "name", "On Time");
        assertEquals("ON_TIME", o.get("state").asText());
        assertEquals("09:00", o.get("checkIn").asText());
        assertTrue(o.get("onDuty").asBoolean());
        assertTrue(o.get("checkOut").isNull());
        JsonNode l = byName(agents, "name", "Late One");
        assertEquals("LATE", l.get("state").asText());
        assertEquals(30, l.get("minutesLate").asInt());
        assertEquals("10:01", l.get("checkOut").asText());
        JsonNode m = byName(agents, "name", "Missing");
        assertEquals("ABSENT", m.get("state").asText());
        assertEquals("Second Mart", m.get("mart").asText(), "absent agents show their usual mart");
        assertEquals("OFF", byName(agents, "name", "Day Off").get("state").asText());
        if (LocalTime.now(KarachiTime.ZONE).isBefore(LocalTime.of(23, 50))) {
            assertEquals("NOT_DUE", byName(agents, "name", "Not Due").get("state").asText());
        }
        assertEquals("Second Mart", byName(agents, "name", "Mover").get("mart").asText(), "today's mart wins");

        JsonNode dairy = byName(d.get("departments"), "name", "Dairy");
        assertEquals(1, dairy.get("absent").asInt());
        assertEquals(1, dairy.get("off").asInt());
        JsonNode mainMart = byName(d.get("marts"), "name", "Main Mart");
        assertEquals(1, mainMart.get("onTime").asInt());
        assertEquals(1, mainMart.get("late").asInt());

        // Mart filter: agents who worked at Main Mart in the last 30 days. Mover checked in
        // elsewhere today: present, not absent.
        JsonNode atMain = data(token(t, "HR"), "today?martId=" + main.getId()).get("agents");
        assertEquals(3, atMain.size(), atMain.toString());
        assertEquals("ON_TIME", byName(atMain, "name", "Mover").get("state").asText());
    }

    @Test
    void trendCountsExpectedPresentLateAndAbsentPerDaySkippingHolidays() throws Exception {
        Tenant t = tenant("TRD");
        Mart m = mart(t, "Mart");
        Agent a = agent(t, "Daily", "AGENT", "Bakery", EVERY_DAY);
        CompanyHoliday h = new CompanyHoliday();
        h.setTenantId(t.getId());
        h.setHolidayDate(today.minusDays(4));
        h.setName("Test Day");
        h.setCreatedAt(LocalDateTime.now());
        holidayRepository.save(h);
        checkIn(t, a, m, today.minusDays(2), LocalTime.of(10, 0), "IN", 10);   // late
        checkIn(t, a, m, today.minusDays(1), LocalTime.of(9, 0), "IN", 10);    // on time
        // today-3 and today: no check-in -> absent

        JsonNode tr = data(token(t, "HR"), "trend?startDate=" + today.minusDays(4) + "&endDate=" + today);
        JsonNode days = tr.get("days");
        assertEquals(5, days.size());
        assertEquals("Test Day", days.get(0).get("holiday").asText());
        assertEquals(0, days.get(0).get("expected").asInt());
        assertTrue(days.get(0).get("attendancePct").isNull(), "nobody expected on a holiday");
        assertEquals(1, days.get(1).get("absent").asInt());
        assertEquals(100.0, days.get(1).get("absentPct").asDouble(), 0.01);
        assertEquals(1, days.get(2).get("late").asInt());
        assertEquals(100.0, days.get(2).get("latePct").asDouble(), 0.01);
        assertEquals(0, days.get(3).get("late").asInt());
        assertEquals(1, days.get(4).get("absent").asInt(), "today, past shift start");
        assertEquals(50.0, tr.get("attendancePct").asDouble(), 0.01, "2 of 4 expected days");
    }

    @Test
    void punctualityRanksLateAgentsAndBucketsCheckInTimes() throws Exception {
        Tenant t = tenant("PUN");
        Mart m = mart(t, "Mart");
        Agent a = agent(t, "Often Late", "AGENT", "Bakery", EVERY_DAY);
        Agent b = agent(t, "Sometimes", "AGENT", "Bakery", EVERY_DAY);
        checkIn(t, a, m, today.minusDays(1), LocalTime.of(10, 0), "IN", 10);   // 30 late
        checkIn(t, a, m, today.minusDays(2), LocalTime.of(10, 10), "IN", 10);  // 40 late
        checkIn(t, b, m, today.minusDays(1), LocalTime.of(9, 0), "IN", 10);
        checkIn(t, b, m, today.minusDays(2), LocalTime.of(9, 40), "MISSED_END_DUTY", 10);  // 10 late, status overwritten

        JsonNode p = data(token(t, "HR"), "punctuality");
        assertEquals(4, p.get("checkIns").asInt());
        assertEquals(3, p.get("lateCheckIns").asInt());
        assertEquals(25.0, p.get("onTimePct").asDouble(), 0.01);
        assertEquals(26.7, p.get("avgMinutesLate").asDouble(), 0.05, "(30+40+10)/3");
        JsonNode first = p.get("lateByAgent").get(0);
        assertEquals("Often Late", first.get("name").asText());
        assertEquals(2, first.get("lateCount").asInt());
        assertEquals(35.0, first.get("avgMinutesLate").asDouble(), 0.01);
        JsonNode slots = p.get("distribution");
        assertEquals("09:00", slots.get(0).get("time").asText());
        assertEquals("10:00", slots.get(slots.size() - 1).get("time").asText());
        assertEquals(5, slots.size(), "09:00 to 10:00 in 15-minute slots, gaps included");
        assertEquals(1, byName(slots, "time", "09:30").get("late").asInt());

        // A night-shift check-in far from the rest: the empty hours fold into one marker.
        checkIn(t, b, m, today.minusDays(3), LocalTime.of(0, 10), "IN", 10);
        JsonNode folded = data(token(t, "HR"), "punctuality?startDate=" + today.minusDays(10) + "&endDate=" + today).get("distribution");
        assertEquals("00:00", folded.get(0).get("time").asText());
        assertEquals("00:15", folded.get(1).get("time").asText());
        assertEquals("09:00", folded.get(1).get("until").asText(), "00:15 to 09:00 had no check-ins");
        assertEquals("09:00", folded.get(2).get("time").asText());
        assertTrue(folded.get(2).get("until").isNull());
    }

    @Test
    void scorecardAndDayPanelPerAgent() throws Exception {
        Tenant t = tenant("SCO");
        Mart m = mart(t, "Mart");
        Agent a = agent(t, "Worker", "AGENT", "Bakery", EVERY_DAY);
        shift(t, a, m, today.minusDays(1), LocalTime.of(9, 0), "IN", 10, Duration.ofHours(8));
        shift(t, a, m, today.minusDays(2), LocalTime.of(10, 0), "IN", 600, Duration.ofHours(6));
        checkIn(t, a, m, today.minusDays(3), LocalTime.of(9, 0), "MISSED_END_DUTY", 10);
        // today-4: absent; today: absent (due at midnight)
        String range = "startDate=" + today.minusDays(4) + "&endDate=" + today;

        JsonNode row = byName(data(token(t, "HR"), "agents?" + range).get("rows"), "name", "Worker");
        assertEquals(5, row.get("daysExpected").asInt());
        assertEquals(3, row.get("daysPresent").asInt());
        assertEquals(1, row.get("daysLate").asInt());
        assertEquals(2, row.get("daysAbsent").asInt());
        assertEquals(1, row.get("missedEndDuty").asInt());
        assertEquals(7.0, row.get("avgHours").asDouble(), 0.01, "(8 + 6) / 2 closed days; missed End Duty is not real hours");
        assertEquals(60.0, row.get("attendancePct").asDouble(), 0.01);

        JsonNode days = data(token(t, "HR"), "agents/" + a.getId() + "/days?" + range);
        assertEquals(5, days.size());
        assertEquals(today.toString(), days.get(0).get("date").asText(), "newest first");
        assertEquals("ABSENT", days.get(0).get("state").asText());
        assertEquals("PRESENT", days.get(1).get("state").asText());
        assertEquals(8.0, days.get(1).get("hours").asDouble(), 0.01);
        assertEquals("LATE", days.get(2).get("state").asText());
        assertEquals(30, days.get(2).get("minutesLate").asInt());
        assertTrue(days.get(2).get("farFromMart").asBoolean());
        assertTrue(days.get(3).get("missedEndDuty").asBoolean());
        assertTrue(days.get(3).get("hours").isNull());

        // Another company's agent, or a salesman: not found.
        Tenant other = tenant("SCO2");
        assertEquals(HttpStatus.NOT_FOUND, get(token(other, "HR"), "agents/" + a.getId() + "/days").getStatusCode());
        Agent salesman = agent(t, "Seller", "SALESMAN_LMT", "Sales", EVERY_DAY);
        assertEquals(HttpStatus.NOT_FOUND, get(token(t, "HR"), "agents/" + salesman.getId() + "/days").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get(token(t, "SALES"), "agents/" + a.getId() + "/days").getStatusCode());
    }

    @Test
    void aLateShiftWithMissedEndDutyCountsAsBoth() throws Exception {
        Tenant t = tenant("BOTH");
        Mart m = mart(t, "Mart");
        Agent a = agent(t, "Late And Forgot", "AGENT", "Bakery", EVERY_DAY);
        // What the 11:59 PM job writes since V36: status LATE kept, flag set.
        Attendance at = new Attendance();
        at.setTenantId(t.getId());
        at.setAgent(a);
        at.setMart(m);
        LocalDateTime in = KarachiTime.startOfDayUtc(today.minusDays(1)).plusHours(10);
        at.setCheckInTime(in);
        at.setCheckOutTime(KarachiTime.startOfDayUtc(today.minusDays(1)).plusHours(23).plusMinutes(59));
        at.setShiftStartTime(LocalTime.of(9, 30));
        at.setStatus("LATE");
        at.setMissedEndDuty(true);
        at.setDistanceFromMart(10.0);
        attendanceRepository.save(at);
        String range = "startDate=" + today.minusDays(1) + "&endDate=" + today.minusDays(1);

        JsonNode row = byName(data(token(t, "HR"), "agents?" + range).get("rows"), "name", "Late And Forgot");
        assertEquals(1, row.get("daysLate").asInt());
        assertEquals(1, row.get("missedEndDuty").asInt());
        assertTrue(row.get("avgHours").isNull(), "a missed End Duty shift has no real hours");
        JsonNode day = data(token(t, "HR"), "agents/" + a.getId() + "/days?" + range).get(0);
        assertEquals("LATE", day.get("state").asText());
        assertTrue(day.get("missedEndDuty").asBoolean());
        assertEquals(1, alert(data(token(t, "HR"), "attention"), "missed_end_duty").get("count").asInt());
    }

    @Test
    void theDailyReportAndTheCockpitAgreeOnWhoWasLate() throws Exception {
        Tenant t = tenant("DRPT");
        Mart m = mart(t, "Mart");
        // Agents' CURRENT shift start is 00:00; the rows carry 09:30 (saved at check-in). Grace 0.
        Agent onTime = agent(t, "On Time", "AGENT", "Bakery", EVERY_DAY);
        Agent late = agent(t, "Half Hour", "AGENT", "Bakery", EVERY_DAY);
        Agent seconds = agent(t, "Thirty Seconds", "AGENT", "Bakery", EVERY_DAY);
        Agent savedIn = agent(t, "Saved As In", "AGENT", "Bakery", EVERY_DAY);
        LocalDate day = today.minusDays(1);
        checkIn(t, onTime, m, day, LocalTime.of(9, 0), "IN", 10);
        checkIn(t, late, m, day, LocalTime.of(10, 0), "LATE", 10);
        checkIn(t, seconds, m, day, LocalTime.of(9, 30, 30), "IN", 10);   // under a whole minute: on time
        checkIn(t, savedIn, m, day, LocalTime.of(10, 45), "IN", 10);       // the old UTC check saved it as IN

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token(t, "HR"));
        ResponseEntity<String> r = restTemplate.exchange("http://localhost:" + port + "/api/attendance/daily-report?date=" + day,
                HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), r.getBody());
        java.util.Map<String, JsonNode> report = new java.util.HashMap<>();
        for (JsonNode row : mapper.readTree(r.getBody()).get("data")) {
            report.put(row.get("attendance").get("agentName").asText(), row);
        }
        assertEquals("ON_TIME", report.get("On Time").get("shiftCompliance").asText());
        assertEquals("LATE", report.get("Half Hour").get("shiftCompliance").asText());
        assertEquals(30, report.get("Half Hour").get("lateMinutes").asInt());
        assertEquals("ON_TIME", report.get("Thirty Seconds").get("shiftCompliance").asText());
        assertEquals("LATE", report.get("Saved As In").get("shiftCompliance").asText());
        assertEquals(75, report.get("Saved As In").get("lateMinutes").asInt());

        String range = "startDate=" + day + "&endDate=" + day;
        JsonNode p = data(token(t, "HR"), "punctuality?" + range);
        assertEquals(2, p.get("lateCheckIns").asInt(), "same late count as the daily report");
        JsonNode lateRows = p.get("lateByAgent");
        assertEquals(30.0, byName(lateRows, "name", "Half Hour").get("avgMinutesLate").asDouble(), 0.01);
        assertEquals(75.0, byName(lateRows, "name", "Saved As In").get("avgMinutesLate").asDouble(), 0.01);
    }

    @Test
    void locationsCountCheckInsPerMartAndListTheFarthestFirst() throws Exception {
        Tenant t = tenant("LOC");
        Mart near = mart(t, "Near Mart");
        Mart open = mart(t, "Open Mart");
        Agent a = agent(t, "Walker", "AGENT", "Bakery", EVERY_DAY);
        checkIn(t, a, near, today.minusDays(1), LocalTime.of(9, 0), "IN", 20);
        checkIn(t, a, open, today.minusDays(2), LocalTime.of(10, 0), "IN", 450);
        checkIn(t, a, open, today.minusDays(3), LocalTime.of(9, 0), "IN", 1200);

        JsonNode loc = data(token(t, "HR"), "locations");
        JsonNode o = byName(loc.get("marts"), "name", "Open Mart");
        assertEquals(2, o.get("checkIns").asInt());
        assertEquals(2, o.get("farCheckIns").asInt());
        assertEquals(1, o.get("lateCheckIns").asInt());
        assertEquals(1200.0, o.get("maxDistance").asDouble(), 0.01);
        assertEquals("Open Mart", loc.get("marts").get(0).get("name").asText(), "most check-ins first");
        JsonNode out = loc.get("outliers");
        assertEquals(2, out.size());
        assertEquals(1200.0, out.get(0).get("distance").asDouble(), 0.01);
        assertEquals("09:00", out.get(0).get("time").asText());
        assertEquals(0, data(token(t, "HR"), "locations?martId=" + near.getId()).get("outliers").size());
    }
}
