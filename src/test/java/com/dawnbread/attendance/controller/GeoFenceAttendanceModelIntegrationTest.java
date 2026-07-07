package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.GeoFenceLogRepository;
import com.dawnbread.attendance.repository.MartRepository;
import com.dawnbread.attendance.repository.NotificationRepository;
import com.dawnbread.attendance.security.TokenProvider;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the corrected attendance model end-to-end via real HTTP:
 * (a) a geofence entry NEVER creates an attendance row by itself — it only
 *     prompts verification (ENTERED_PENDING_VERIFICATION) — and the row is
 *     created exclusively by a real, verified POST /attendance/checkin,
 * (b) subsequent entries/exits fire Sales-only notifications and never touch
 *     attendance,
 * (c) End Duty (POST /attendance/checkout) finalizes the official check-out,
 * (d) the resulting record reflects first-check-in -> End-Duty-checkout.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GeoFenceAttendanceModelIntegrationTest {

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
    private AttendanceRepository attendanceRepository;

    @Autowired
    private GeoFenceLogRepository geoFenceLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Agent seedAgent(String agentId) {
        Agent agent = new Agent();
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole("AGENT");
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private Mart seedMart(String name, double lat, double lon) {
        Mart mart = new Mart();
        mart.setName(name);
        mart.setAddress("Test Address");
        mart.setLatitude(lat);
        mart.setLongitude(lon);
        mart.setRadius(100.0);
        mart.setGeoFencingEnabled(true);
        mart.setIsActive(true);
        mart.setCreatedAt(LocalDateTime.now());
        return martRepository.save(mart);
    }

    private ResponseEntity<String> geoFenceCheck(String token, Long agentId, double lat, double lon) {
        Map<String, Object> body = new HashMap<>();
        body.put("agentId", agentId);
        body.put("latitude", lat);
        body.put("longitude", lon);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        return restTemplate.exchange(url("/api/geo-fence/check"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private long countHrNotificationsFor(Long agentId) {
        return notificationRepository.findByAgentIdOrderByCreatedAtDesc(agentId).stream()
                .filter(n -> "HR".equals(n.getDepartment()))
                .count();
    }

    private long countSalesGeofenceActivityNotificationsFor(Long agentId) {
        return notificationRepository.findByAgentIdOrderByCreatedAtDesc(agentId).stream()
                .filter(n -> "SALES".equals(n.getDepartment()) && "GEOFENCE_ACTIVITY".equals(n.getType()))
                .count();
    }

    @Test
    void firstEntryChecksInSubsequentEntriesDoNotThenEndDutyFinalizes() {
        Agent agent = seedAgent("MODEL_AGENT_1");
        Mart mart = seedMart("Model Mart 1", 31.5000, 74.3000);
        String token = tokenProvider.generateToken(agent.getId(), agent.getAgentId(), "AGENT");

        double insideLat = mart.getLatitude();
        double insideLon = mart.getLongitude();
        double outsideLat = mart.getLatitude() + 1.0; // far outside the 100m radius
        double outsideLon = mart.getLongitude() + 1.0;

        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        // --- (a) First entry of the day only PROMPTS verification — creates no row ---
        ResponseEntity<String> first = geoFenceCheck(token, agent.getId(), insideLat, insideLon);
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertTrue(first.getBody().contains("\"status\":\"ENTERED_PENDING_VERIFICATION\""),
                "First entry must only prompt verification, never auto check in: " + first.getBody());

        assertTrue(attendanceRepository.findByAgentIdAndCheckInTimeBetween(agent.getId(), startOfDay, endOfDay).isEmpty(),
                "Geofence entry alone must not create any attendance row");
        assertEquals(0, countHrNotificationsFor(agent.getId()),
                "HR must not be notified until a real verified check-in happens");

        // --- (a) Duty only starts once a real, verified check-in is submitted ---
        Map<String, Object> checkInBody = new HashMap<>();
        checkInBody.put("agentId", agent.getId());
        checkInBody.put("martId", mart.getId());
        checkInBody.put("latitude", insideLat);
        checkInBody.put("longitude", insideLon);
        checkInBody.put("faceVerified", true);

        HttpHeaders checkInHeaders = new HttpHeaders();
        checkInHeaders.setContentType(MediaType.APPLICATION_JSON);
        checkInHeaders.setBearerAuth(token);

        ResponseEntity<String> checkIn = restTemplate.exchange(
                url("/api/attendance/checkin"), HttpMethod.POST, new HttpEntity<>(checkInBody, checkInHeaders), String.class);
        assertEquals(HttpStatus.CREATED, checkIn.getStatusCode(), "Verified check-in: " + checkIn.getBody());

        List<Attendance> todaysRecordsAfterCheckIn =
                attendanceRepository.findByAgentIdAndCheckInTimeBetween(agent.getId(), startOfDay, endOfDay);
        assertEquals(1, todaysRecordsAfterCheckIn.size(), "Exactly one attendance row must exist after the verified check-in");
        Long attendanceId = todaysRecordsAfterCheckIn.get(0).getId();
        LocalDateTime originalCheckInTime = todaysRecordsAfterCheckIn.get(0).getCheckInTime();
        assertTrue(Boolean.TRUE.equals(todaysRecordsAfterCheckIn.get(0).getFaceVerifiedCheckin()),
                "Check-in must be recorded as face-verified");
        long hrNotifsAfterCheckIn = countHrNotificationsFor(agent.getId());
        assertTrue(hrNotifsAfterCheckIn >= 1, "Check-in should have notified HR");

        // Repeated ping while still stationary inside -> STAYED, no new log/row.
        ResponseEntity<String> stayed = geoFenceCheck(token, agent.getId(), insideLat, insideLon);
        assertTrue(stayed.getBody().contains("\"status\":\"STAYED\""), "Repeat ping inside must be STAYED: " + stayed.getBody());
        assertEquals(1, geoFenceLogRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId()).size(),
                "No new geofence log on a stationary repeat ping");

        // --- (b) Exit is notify-only, never touches attendance ---
        ResponseEntity<String> exit = geoFenceCheck(token, agent.getId(), outsideLat, outsideLon);
        assertEquals(HttpStatus.OK, exit.getStatusCode());
        assertTrue(exit.getBody().contains("\"status\":\"EXITED_LOGGED\""), "Exit must be EXITED_LOGGED, never a checkout: " + exit.getBody());

        Attendance stillOpenAfterExit = attendanceRepository.findById(attendanceId).orElseThrow();
        assertEquals(null, stillOpenAfterExit.getCheckOutTime(), "Exit must NOT set a checkout time");
        assertEquals(1, attendanceRepository.findByAgentIdAndCheckInTimeBetween(agent.getId(), startOfDay, endOfDay).size(),
                "Still exactly one attendance row after an exit");

        // --- (b) Re-entry same day is notify-only, does not create a second check-in ---
        ResponseEntity<String> reEntry = geoFenceCheck(token, agent.getId(), insideLat, insideLon);
        assertTrue(reEntry.getBody().contains("\"status\":\"ENTERED_LOGGED\""),
                "Re-entry after already checking in today must be ENTERED_LOGGED, not a fresh check-in: " + reEntry.getBody());
        assertEquals(1, attendanceRepository.findByAgentIdAndCheckInTimeBetween(agent.getId(), startOfDay, endOfDay).size(),
                "Still exactly one attendance row after re-entry — no duplicate check-in created");

        // Both the exit and the re-entry should have pinged Sales, and neither should have pinged HR again.
        assertEquals(2, countSalesGeofenceActivityNotificationsFor(agent.getId()),
                "Exit + re-entry should each fire exactly one Sales geofence-activity notification");
        assertEquals(hrNotifsAfterCheckIn, countHrNotificationsFor(agent.getId()),
                "HR must not be notified for mid-day presence blips, only the two official bookends");

        // --- (c) End Duty (POST /attendance/checkout) finalizes the official check-out ---
        Map<String, Object> checkoutBody = new HashMap<>();
        checkoutBody.put("agentId", agent.getId());
        checkoutBody.put("latitude", outsideLat);
        checkoutBody.put("longitude", outsideLon);
        checkoutBody.put("faceVerified", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        ResponseEntity<String> endDuty = restTemplate.exchange(
                url("/api/attendance/checkout"), HttpMethod.POST, new HttpEntity<>(checkoutBody, headers), String.class);
        assertEquals(HttpStatus.OK, endDuty.getStatusCode(), "End Duty checkout: " + endDuty.getBody());

        // --- (d) Final record reflects first-check-in -> End-Duty-checkout ---
        Attendance finalRecord = attendanceRepository.findById(attendanceId).orElseThrow();
        assertEquals(originalCheckInTime, finalRecord.getCheckInTime(),
                "Check-in time must still be the very first entry's timestamp, untouched by the mid-day exit/re-entry");
        assertTrue(finalRecord.getCheckOutTime() != null, "End Duty must set the official checkout time");
        assertTrue(Boolean.TRUE.equals(finalRecord.getFaceVerifiedCheckout()), "End Duty checkout must be recorded as face-verified");
        assertEquals(1, attendanceRepository.findByAgentIdAndCheckInTimeBetween(agent.getId(), startOfDay, endOfDay).size(),
                "The whole day still resolves to exactly one attendance row: first check-in through End-Duty checkout");
    }

    @Test
    void geoFenceExitAloneNeverCreatesAnOpenAttendanceCloseWithoutEndDuty() {
        // An agent who was never checked in (no first entry yet) walking near a
        // mart and then away must not somehow trigger a checkout — there's
        // nothing open to close, and geofence events never call checkout.
        Agent agent = seedAgent("MODEL_AGENT_2");
        Mart mart = seedMart("Model Mart 2", 31.6000, 74.4000);
        String token = tokenProvider.generateToken(agent.getId(), agent.getAgentId(), "AGENT");

        ResponseEntity<String> outside = geoFenceCheck(token, agent.getId(),
                mart.getLatitude() + 1.0, mart.getLongitude() + 1.0);
        assertTrue(outside.getBody().contains("\"status\":\"OUTSIDE\""), outside.getBody());
        assertTrue(attendanceRepository.findOpenAttendanceByAgentId(agent.getId()).isEmpty());
    }

    @Test
    void geoFenceEntryAloneNeverCreatesAttendanceWithoutVerification() {
        // GPS proximity alone must never be treated as identity. An agent who
        // walks into a mart's geofence and never completes face verification
        // must end the day with NO attendance row at all — not open, not
        // flagged, not pending. Only a real POST /attendance/checkin creates one.
        Agent agent = seedAgent("MODEL_AGENT_3");
        Mart mart = seedMart("Model Mart 3", 31.7000, 74.5000);
        String token = tokenProvider.generateToken(agent.getId(), agent.getAgentId(), "AGENT");

        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        ResponseEntity<String> entry = geoFenceCheck(token, agent.getId(), mart.getLatitude(), mart.getLongitude());
        assertEquals(HttpStatus.OK, entry.getStatusCode());
        assertTrue(entry.getBody().contains("\"status\":\"ENTERED_PENDING_VERIFICATION\""),
                "Entry must only prompt verification: " + entry.getBody());
        assertTrue(entry.getBody().contains("\"attendance\":null"),
                "No attendance object of any kind — pending or otherwise — may be returned: " + entry.getBody());

        // Repeat pings (agent lingering, never verifying) still must not create anything.
        geoFenceCheck(token, agent.getId(), mart.getLatitude(), mart.getLongitude());
        geoFenceCheck(token, agent.getId(), mart.getLatitude(), mart.getLongitude());

        assertTrue(attendanceRepository.findByAgentIdAndCheckInTimeBetween(agent.getId(), startOfDay, endOfDay).isEmpty(),
                "No attendance row of any kind must exist when verification never happened");
        assertTrue(attendanceRepository.findOpenAttendanceByAgentId(agent.getId()).isEmpty());
    }
}
