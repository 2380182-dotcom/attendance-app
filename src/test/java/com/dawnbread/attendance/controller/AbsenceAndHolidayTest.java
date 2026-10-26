package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.*;
import com.dawnbread.attendance.repository.*;
import com.dawnbread.attendance.security.TokenProvider;
import com.dawnbread.attendance.service.AbsenceService;
import com.dawnbread.attendance.service.AgentService;
import com.dawnbread.attendance.util.KarachiTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V34 saved absences + V35 company holidays. Each test seeds its own tenant
 * and checks only its own agents, so the job running over every agent in
 * the shared test database never makes these assertions flaky.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AbsenceAndHolidayTest {

    /** A Monday well in the past: no other test writes absences for it in these tenants. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);
    private static final List<String> MON_TO_SAT = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT");
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
    private FaceVerificationLogRepository faceVerificationLogRepository;
    @Autowired
    private AbsenceService absenceService;
    @Autowired
    private AgentService agentService;
    @Autowired
    private com.dawnbread.attendance.config.SchedulerConfig schedulerConfig;
    @Autowired
    private NotificationRepository notificationRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    // ------------------------------------------------------------------ seeding

    private Tenant tenant(String label) {
        Tenant t = new Tenant();
        t.setCompanyCode("AB" + label + (System.nanoTime() % 1_000_000));
        t.setName("Absence " + label);
        t.setIsActive(true);
        t.setCreatedAt(LocalDateTime.now());
        t.setCreatedBy("TEST");
        return tenantRepository.save(t);
    }

    private Agent agent(Tenant t, String role, List<String> workingDays) {
        Agent a = new Agent();
        a.setTenantId(t.getId());
        String id = "AB" + System.nanoTime();
        a.setAgentId(id);
        a.setName("Agent " + id);
        a.setEmail(id.toLowerCase() + "@example.com");
        a.setRole(role);
        a.setIsActive(true);
        a.setWorkingDays(workingDays);
        a.setCreatedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        return agentRepository.save(a);
    }

    private Mart mart(Tenant t) {
        Mart m = new Mart();
        m.setTenantId(t.getId());
        m.setName("Absence Mart " + System.nanoTime());
        m.setAddress("Test");
        // Far from every other test's marts so geofence lookups never collide.
        m.setLatitude(24.8600 + (System.nanoTime() % 1000) / 100000.0);
        m.setLongitude(67.0100);
        m.setRadius(100.0);
        m.setGeoFencingEnabled(true);
        m.setIsActive(true);
        m.setCreatedAt(LocalDateTime.now());
        return martRepository.save(m);
    }

    private void checkedInOn(Tenant t, Agent agent, Mart mart, LocalDate date) {
        Attendance a = new Attendance();
        a.setTenantId(t.getId());
        a.setAgent(agent);
        a.setMart(mart);
        a.setCheckInTime(KarachiTime.startOfDayUtc(date).plusHours(4)); // 9 AM Pakistan time that day
        a.setCheckOutTime(KarachiTime.startOfDayUtc(date).plusHours(12));
        a.setStatus("IN");
        attendanceRepository.save(a);
    }

    private boolean absent(Tenant t, Agent agent, LocalDate date) {
        return absenceRepository.existsByTenantIdAndAgentIdAndAbsenceDate(t.getId(), agent.getId(), date);
    }

    private String token(Tenant t, Long id, String role) {
        return tokenProvider.generateToken(id, "AB_" + role, role, t.getId());
    }

    private ResponseEntity<String> call(HttpMethod method, String token, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("http://localhost:" + port + path, method, new HttpEntity<>(body, headers), String.class);
    }

    private static Map<String, Object> holiday(LocalDate date, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("holidayDate", date.toString());
        body.put("name", name);
        return body;
    }

    // ------------------------------------------------------------------ the 10 PM job

    @Test
    void martAgentWithNoCheckInOnAWorkingDayIsMarkedAbsentExactlyOnceEvenIfTheJobRunsTwice() {
        Tenant t = tenant("DUP");
        Agent agent = agent(t, "AGENT", MON_TO_SAT);

        absenceService.recordAbsences(MONDAY);
        absenceService.recordAbsences(MONDAY);   // second run must not add a second row

        assertTrue(absent(t, agent, MONDAY));
        long rows = absenceRepository.findAll().stream()
                .filter(a -> a.getAgentId().equals(agent.getId()) && a.getAbsenceDate().equals(MONDAY)).count();
        assertEquals(1, rows, "the job is idempotent");

        // The database itself refuses a duplicate (e.g. two instances racing).
        AgentAbsence dup = new AgentAbsence();
        dup.setTenantId(t.getId());
        dup.setAgentId(agent.getId());
        dup.setAbsenceDate(MONDAY);
        dup.setCreatedAt(LocalDateTime.now());
        assertThrows(DataIntegrityViolationException.class, () -> absenceRepository.saveAndFlush(dup));
    }

    @Test
    void agentWhoCheckedInThatDayIsNotMarkedAbsent() {
        Tenant t = tenant("PRESENT");
        Agent present = agent(t, "AGENT", MON_TO_SAT);
        Agent missing = agent(t, "AGENT", MON_TO_SAT);
        checkedInOn(t, present, mart(t), MONDAY);

        absenceService.recordAbsences(MONDAY);

        assertFalse(absent(t, present, MONDAY));
        assertTrue(absent(t, missing, MONDAY));
    }

    @Test
    void nonWorkingDayIsSkipped() {
        Tenant t = tenant("OFF");
        Agent monToSat = agent(t, "AGENT", MON_TO_SAT);
        Agent offOnMondays = agent(t, "AGENT", List.of("TUE", "WED", "THU", "FRI", "SAT", "SUN"));
        LocalDate sunday = MONDAY.minusDays(1);

        absenceService.recordAbsences(sunday);
        absenceService.recordAbsences(MONDAY);

        assertFalse(absent(t, monToSat, sunday), "Sunday is not one of his working days");
        assertFalse(absent(t, offOnMondays, MONDAY), "Monday is his day off");
        assertTrue(absent(t, monToSat, MONDAY));
    }

    @Test
    void localAndLmtSalesmenAreNeverMarkedAbsent() {
        Tenant t = tenant("SALES");
        Agent local = agent(t, "SALESMAN_LOCAL", EVERY_DAY);
        Agent lmt = agent(t, "SALESMAN_LMT", EVERY_DAY);
        Agent hr = agent(t, "HR", EVERY_DAY);

        absenceService.recordAbsences(MONDAY);

        assertFalse(absent(t, local, MONDAY));
        assertFalse(absent(t, lmt, MONDAY));
        assertFalse(absent(t, hr, MONDAY), "only mart agents (role AGENT) are tracked");
    }

    @Test
    void inactiveAgentsAreNotMarkedAbsent() {
        Tenant t = tenant("INACTIVE");
        Agent gone = agent(t, "AGENT", MON_TO_SAT);
        gone.setIsActive(false);
        agentRepository.save(gone);

        absenceService.recordAbsences(MONDAY);

        assertFalse(absent(t, gone, MONDAY));
    }

    @Test
    void companyHolidayIsSkippedOnlyForThatCompany() {
        Tenant withHoliday = tenant("HOL");
        Tenant without = tenant("NOHOL");
        Agent a = agent(withHoliday, "AGENT", MON_TO_SAT);
        Agent b = agent(without, "AGENT", MON_TO_SAT);
        CompanyHoliday h = new CompanyHoliday();
        h.setTenantId(withHoliday.getId());
        h.setHolidayDate(MONDAY);
        h.setName("Kashmir Day");
        h.setCreatedAt(LocalDateTime.now());
        holidayRepository.save(h);

        absenceService.recordAbsences(MONDAY);

        assertFalse(absent(withHoliday, a, MONDAY), "holiday: nobody is absent");
        assertTrue(absent(without, b, MONDAY), "another company's holiday does not apply");
    }

    @Test
    void addingAHolidayAfterAbsencesWereSavedRemovesThoseAbsencesOnly() throws Exception {
        Tenant t = tenant("LATEHOL");
        Agent agent = agent(t, "AGENT", MON_TO_SAT);
        LocalDate tuesday = MONDAY.plusDays(1);
        absenceService.recordAbsences(MONDAY);
        absenceService.recordAbsences(tuesday);
        assertTrue(absent(t, agent, MONDAY));

        ResponseEntity<String> created = call(HttpMethod.POST, token(t, 1L, "ADMIN"), "/api/holidays",
                holiday(MONDAY, "Pakistan Day"));
        assertEquals(HttpStatus.CREATED, created.getStatusCode(), created.getBody());

        assertFalse(absent(t, agent, MONDAY), "the holiday removed that day's absence");
        assertTrue(absent(t, agent, tuesday), "other days are untouched");

        // Moving the holiday to Tuesday removes Tuesday's absence too.
        long id = mapper.readTree(created.getBody()).get("data").get("id").asLong();
        ResponseEntity<String> moved = call(HttpMethod.PUT, token(t, 1L, "ADMIN"), "/api/holidays/" + id,
                holiday(tuesday, "Pakistan Day (moved)"));
        assertEquals(HttpStatus.OK, moved.getStatusCode(), moved.getBody());
        assertFalse(absent(t, agent, tuesday));
    }

    @Test
    void aLaterCheckInRemovesThatDaysAbsence() {
        Tenant t = tenant("LATE");
        Agent agent = agent(t, "AGENT", EVERY_DAY);
        Agent other = agent(t, "AGENT", EVERY_DAY);
        Mart mart = mart(t);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        absenceService.recordAbsences(today);
        assertTrue(absent(t, agent, today));
        assertTrue(absent(t, other, today));

        FaceVerificationLog face = new FaceVerificationLog();
        face.setTenantId(t.getId());
        face.setAgentId(agent.getId());
        face.setVerificationTime(LocalDateTime.now());
        face.setSuccess(true);
        face.setSimilarityScore(0.97);
        faceVerificationLogRepository.save(face);

        Map<String, Object> body = new HashMap<>();
        body.put("agentId", agent.getId());
        body.put("martId", mart.getId());
        body.put("latitude", mart.getLatitude());
        body.put("longitude", mart.getLongitude());
        body.put("faceVerified", true);
        ResponseEntity<String> checkIn = call(HttpMethod.POST, token(t, agent.getId(), "AGENT"), "/api/attendance/checkin", body);
        assertEquals(HttpStatus.CREATED, checkIn.getStatusCode(), checkIn.getBody());

        assertFalse(absent(t, agent, today), "checking in removed his absence");
        assertTrue(absent(t, other, today), "nobody else's absence is touched");
    }

    @Test
    void deletingAnAgentAlsoDeletesTheirAbsences() {
        Tenant t = tenant("DEL");
        Agent agent = agent(t, "AGENT", MON_TO_SAT);
        absenceService.recordAbsences(MONDAY);
        assertTrue(absent(t, agent, MONDAY));

        agentService.deleteAgent(agent.getId());

        assertFalse(absent(t, agent, MONDAY));
    }

    private long absentNotifications(Agent agent) {
        return notificationRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId(), org.springframework.data.domain.PageRequest.of(0, 100))
                .stream().filter(n -> "ABSENT".equals(n.getType())).count();
    }

    @Test
    void tenPmNotificationFollowsExactlyTheSameRulesAsTheAbsenceRecords() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        String todayCode = today.getDayOfWeek().name().substring(0, 3);
        List<String> everyDayButToday = EVERY_DAY.stream().filter(d -> !d.equals(todayCode)).toList();

        Tenant t = tenant("NOTIFY");
        Agent missing = agent(t, "AGENT", EVERY_DAY);
        Agent dayOff = agent(t, "AGENT", everyDayButToday);
        Agent local = agent(t, "SALESMAN_LOCAL", EVERY_DAY);
        Agent lmt = agent(t, "SALESMAN_LMT", EVERY_DAY);
        Agent inactive = agent(t, "AGENT", EVERY_DAY);
        inactive.setIsActive(false);
        agentRepository.save(inactive);
        Agent present = agent(t, "AGENT", EVERY_DAY);
        checkedInOn(t, present, mart(t), today);

        Tenant onHoliday = tenant("NOTIFYHOL");
        Agent holidayAgent = agent(onHoliday, "AGENT", EVERY_DAY);
        CompanyHoliday h = new CompanyHoliday();
        h.setTenantId(onHoliday.getId());
        h.setHolidayDate(today);
        h.setName("Test Holiday");
        h.setCreatedAt(LocalDateTime.now());
        holidayRepository.save(h);

        schedulerConfig.markAbsentAgents();

        assertEquals(1, absentNotifications(missing), "absent mart agent on a working day is notified");
        assertTrue(absent(t, missing, today), "and his absence is saved");
        for (Agent notNotified : List.of(dayOff, local, lmt, inactive, present, holidayAgent)) {
            assertEquals(0, absentNotifications(notNotified), notNotified.getRole() + " " + notNotified.getAgentId());
        }
        assertFalse(absent(t, dayOff, today));
        assertFalse(absent(onHoliday, holidayAgent, today));
    }

    // ------------------------------------------------------------------ endpoints

    @Test
    void holidayRulesOnlyAdminChangesHrReadsOthersBlockedDuplicatesRejected() throws Exception {
        Tenant t = tenant("API");
        String admin = token(t, 1L, "ADMIN");
        String hr = token(t, 2L, "HR");

        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.POST, hr, "/api/holidays", holiday(MONDAY, "Eid")).getStatusCode());
        for (String role : List.of("SALES", "AGENT", "SALESMAN_LOCAL", "SALESMAN_LMT")) {
            assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.GET, token(t, 3L, role), "/api/holidays", null).getStatusCode(), role);
        }

        assertEquals(HttpStatus.CREATED, call(HttpMethod.POST, admin, "/api/holidays", holiday(MONDAY, "Eid ul Fitr")).getStatusCode());
        ResponseEntity<String> dup = call(HttpMethod.POST, admin, "/api/holidays", holiday(MONDAY, "Again"));
        assertEquals(HttpStatus.BAD_REQUEST, dup.getStatusCode());
        assertTrue(dup.getBody().contains("already a holiday"));
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.POST, admin, "/api/holidays", holiday(MONDAY.plusDays(3), " ")).getStatusCode());

        JsonNode list = mapper.readTree(call(HttpMethod.GET, hr, "/api/holidays", null).getBody()).get("data");
        assertEquals(1, list.size());
        assertEquals("Eid ul Fitr", list.get(0).get("name").asText());
        long id = list.get(0).get("id").asLong();

        // Another company can neither see, edit nor delete it.
        Tenant other = tenant("API2");
        String otherAdmin = token(other, 1L, "ADMIN");
        assertEquals(0, mapper.readTree(call(HttpMethod.GET, otherAdmin, "/api/holidays", null).getBody()).get("data").size());
        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.PUT, otherAdmin, "/api/holidays/" + id, holiday(MONDAY, "Hijack")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.DELETE, otherAdmin, "/api/holidays/" + id, null).getStatusCode());

        assertEquals(HttpStatus.OK, call(HttpMethod.DELETE, admin, "/api/holidays/" + id, null).getStatusCode());
        assertEquals(0, mapper.readTree(call(HttpMethod.GET, admin, "/api/holidays", null).getBody()).get("data").size());
    }

    @Test
    void hrSeesOnlyTheirOwnCompanysAbsences() throws Exception {
        Tenant a = tenant("HRA");
        Tenant b = tenant("HRB");
        Agent agentA = agent(a, "AGENT", MON_TO_SAT);
        agent(b, "AGENT", MON_TO_SAT);
        absenceService.recordAbsences(MONDAY);

        String range = "/api/hr/absences?startDate=" + MONDAY + "&endDate=" + MONDAY;
        JsonNode forA = mapper.readTree(call(HttpMethod.GET, token(a, 2L, "HR"), range, null).getBody()).get("data");
        assertEquals(1, forA.size());
        assertEquals(agentA.getId().longValue(), forA.get(0).get("agentId").asLong());
        assertEquals(MONDAY.toString(), forA.get(0).get("absenceDate").asText());

        JsonNode oneAgent = mapper.readTree(call(HttpMethod.GET, token(a, 2L, "ADMIN"), range + "&agentId=" + agentA.getId(), null)
                .getBody()).get("data");
        assertEquals(1, oneAgent.size());

        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.GET, token(a, 3L, "SALES"), range, null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.GET, token(a, 3L, "AGENT"), range, null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.GET, token(a, 2L, "HR"),
                "/api/hr/absences?startDate=" + MONDAY + "&endDate=" + MONDAY.minusDays(1), null).getStatusCode());
    }
}
