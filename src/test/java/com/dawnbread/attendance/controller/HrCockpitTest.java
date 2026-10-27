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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HR cockpit (step 3, part 1): highlights + needs attention. Each test seeds
 * its own tenant. Agents get shift start 00:00 with no grace so "absent
 * today" never depends on what time the test runs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HrCockpitTest {

    private static final String BASE = "/api/cockpit/hr/";
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

    /** A closed shift on a Pakistan day at a Pakistan time. */
    private void checkIn(Tenant t, Agent a, Mart m, LocalDate day, LocalTime pkTime, String status, double distance) {
        Attendance at = new Attendance();
        at.setTenantId(t.getId());
        at.setAgent(a);
        at.setMart(m);
        LocalDateTime utc = KarachiTime.startOfDayUtc(day).plusSeconds(pkTime.toSecondOfDay());
        at.setCheckInTime(utc);
        at.setCheckOutTime(utc.plusMinutes(1));
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
            for (String p : List.of("highlights", "attention", "filter-options")) {
                assertEquals(HttpStatus.FORBIDDEN, get(token(t, role), p).getStatusCode(), role + " " + p);
            }
        }
        for (String role : List.of("ADMIN", "HR")) {
            for (String p : List.of("highlights", "attention", "filter-options")) {
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
        checkIn(a, agentA, m, today, LocalTime.of(9, 0), "LATE", 10);

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
}
