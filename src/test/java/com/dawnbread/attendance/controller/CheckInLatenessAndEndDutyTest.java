package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.config.SchedulerConfig;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.MartRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TokenProvider;
import com.dawnbread.attendance.util.KarachiTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Lateness is decided in Pakistan time at check-in, and the 11:59 PM job
 * keeps a LATE status while flagging the missed End Duty.
 *
 * Before the fix check-in compared the server's UTC clock with the Pakistan
 * shift start: a check-in an hour after shift start (Pakistan) was 4 hours
 * "early" in UTC and saved as IN.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckInLatenessAndEndDutyTest {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private TokenProvider tokenProvider;
    @Autowired
    private AgentRepository agentRepository;
    @Autowired
    private MartRepository martRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private SchedulerConfig schedulerConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long tenantId() {
        return TenantTestHelper.defaultTenantId(tenantRepository);
    }

    /** Works only on today's Pakistan weekday, so a UTC-date check would also show up as NON_WORKING_DAY. */
    private Agent seedAgent(String agentId, LocalTime shiftStart) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId(agentId + System.nanoTime());
        agent.setName("Late " + agentId);
        agent.setEmail(agent.getAgentId().toLowerCase() + "@example.com");
        agent.setRole("AGENT");
        agent.setShiftStartTime(shiftStart);
        agent.setShiftEndTime(LocalTime.of(23, 59));
        agent.setGracePeriodMinutes(0);
        agent.setWorkingDays(List.of(LocalDate.now(KarachiTime.ZONE).getDayOfWeek().name().substring(0, 3)));
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private Mart seedMart() {
        Mart mart = new Mart();
        mart.setTenantId(tenantId());
        mart.setName("Late Test Mart");
        mart.setAddress("Test Address");
        mart.setLatitude(31.5);
        mart.setLongitude(74.3);
        mart.setRadius(100.0);
        mart.setGeoFencingEnabled(true);
        mart.setIsActive(true);
        mart.setCreatedAt(LocalDateTime.now());
        return martRepository.save(mart);
    }

    /** Checks in through the API and returns the saved row. */
    private Attendance checkIn(Agent agent, Mart mart) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("agentId", agent.getId());
        body.put("martId", mart.getId());
        body.put("latitude", mart.getLatitude());
        body.put("longitude", mart.getLongitude());
        body.put("faceVerified", true);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenProvider.generateToken(agent.getId(), agent.getAgentId(), "AGENT", tenantId()));
        ResponseEntity<String> r = restTemplate.exchange("http://localhost:" + port + "/api/attendance/checkin",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.CREATED, r.getStatusCode(), r.getBody());
        JsonNode data = objectMapper.readTree(r.getBody()).get("data");
        return attendanceRepository.findById(data.get("id").asLong()).orElseThrow();
    }

    @Test
    void aCheckInAfterShiftStartInPakistanTimeIsLate() throws Exception {
        LocalTime nowPk = LocalTime.now(KarachiTime.ZONE).truncatedTo(ChronoUnit.MINUTES);
        int sinceMidnight = nowPk.toSecondOfDay() / 60;
        assumeTrue(sinceMidnight >= 3, "too close to midnight for a same-day shift start");
        int behind = Math.min(60, sinceMidnight - 1);   // an hour, or as much of today as there is
        Agent agent = seedAgent("LATE", nowPk.minusMinutes(behind));

        Attendance saved = checkIn(agent, seedMart());
        assertEquals("LATE", saved.getStatus(), "Pakistan working day, after shift start: late, not IN / NON_WORKING_DAY");
        int late = saved.getLateMinutes();
        assertTrue(late >= behind - 1 && late <= behind + 2, "about " + behind + " minutes late, got " + late);
    }

    @Test
    void aCheckInBeforeShiftStartInPakistanTimeIsOnTime() throws Exception {
        LocalTime nowPk = LocalTime.now(KarachiTime.ZONE).truncatedTo(ChronoUnit.MINUTES);
        assumeTrue(nowPk.isBefore(LocalTime.of(22, 50)), "shift start would fall on the next day");
        Agent agent = seedAgent("ON_TIME", nowPk.plusHours(1));

        Attendance saved = checkIn(agent, seedMart());
        assertEquals("IN", saved.getStatus());
        assertEquals(0, saved.getLateMinutes());
    }

    private Attendance openShift(Agent agent, Mart mart, String status) {
        Attendance at = new Attendance();
        at.setTenantId(tenantId());
        at.setAgent(agent);
        at.setMart(mart);
        at.setCheckInTime(LocalDateTime.now().minusHours(3));
        at.setStatus(status);
        at.setLateMinutes("LATE".equals(status) ? 40 : 0);
        return attendanceRepository.save(at);
    }

    @Test
    void theElevenFiftyNineJobKeepsLateAndFlagsTheMissedEndDuty() {
        Mart mart = seedMart();
        Attendance late = openShift(seedAgent("JOB_LATE", LocalTime.of(9, 0)), mart, "LATE");
        Attendance onTime = openShift(seedAgent("JOB_ON_TIME", LocalTime.of(9, 0)), mart, "IN");

        schedulerConfig.autoCheckoutAllAgents();

        Attendance lateAfter = attendanceRepository.findById(late.getId()).orElseThrow();
        assertEquals("LATE", lateAfter.getStatus(), "late must not be overwritten");
        assertTrue(lateAfter.getMissedEndDuty());
        assertNotNull(lateAfter.getCheckOutTime());
        assertEquals(40, lateAfter.getLateMinutes());
        assertFalse(lateAfter.getFaceVerifiedCheckout());

        Attendance onTimeAfter = attendanceRepository.findById(onTime.getId()).orElseThrow();
        assertEquals("MISSED_END_DUTY", onTimeAfter.getStatus(), "unchanged for screens that only read the status");
        assertTrue(onTimeAfter.getMissedEndDuty());
        assertNotNull(onTimeAfter.getCheckOutTime());
    }
}
