package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.MartRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TokenProvider;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves AttendanceService.checkIn() actually REJECTS a check-in attempt
 * from outside the mart's geofence radius, rather than merely flagging it
 * LATE and letting it through. (Earlier revisions of this test — and of
 * the service — treated "outside the radius" as a lateness signal only;
 * that was itself the bug: an agent anywhere on Earth could check in as
 * long as they hit the endpoint, with no server-side location enforcement
 * at all. See the geofence-gating security finding.)
 *
 * mart.radius is stored in meters (see AdminMartScreen.js: "Radius
 * (meters)"), matching calculateDistance()'s return unit — no conversion
 * needed, and no more "* 1000" unit-inflation bug either.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttendanceGeofenceRadiusIntegrationTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Long tenantId() {
        return TenantTestHelper.defaultTenantId(tenantRepository);
    }

    /**
     * Shift-based lateness (calculateLateMinutes vs. shiftStartTime+grace,
     * and isWorkingDay vs. workingDays) is a SEPARATE trigger ORed into the
     * same "LATE" status this test is isolating distance from. Given a wide
     * open shift (00:00 start, near-24h grace) and every day of the week as
     * a working day, shift compliance can never be the reason for LATE here
     * regardless of what wall-clock time/day this test actually runs at —
     * only the distance check can flip the status.
     */
    private Agent seedAgent(String agentId) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole("AGENT");
        agent.setShiftStartTime(java.time.LocalTime.of(0, 0));
        agent.setShiftEndTime(java.time.LocalTime.of(23, 59));
        agent.setGracePeriodMinutes(1439);
        agent.setWorkingDays(java.util.List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"));
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private Mart seedMart(String name, double lat, double lon, double radiusMeters) {
        Mart mart = new Mart();
        mart.setTenantId(tenantId());
        mart.setName(name);
        mart.setAddress("Test Address");
        mart.setLatitude(lat);
        mart.setLongitude(lon);
        mart.setRadius(radiusMeters);
        mart.setGeoFencingEnabled(true);
        mart.setIsActive(true);
        mart.setCreatedAt(LocalDateTime.now());
        return martRepository.save(mart);
    }

    private ResponseEntity<String> checkIn(String token, Long agentId, Long martId, double lat, double lon) {
        Map<String, Object> body = new HashMap<>();
        body.put("agentId", agentId);
        body.put("martId", martId);
        body.put("latitude", lat);
        body.put("longitude", lon);
        body.put("faceVerified", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        return restTemplate.exchange(url("/api/attendance/checkin"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> checkOut(String token, Long agentId, double lat, double lon) {
        Map<String, Object> body = new HashMap<>();
        body.put("agentId", agentId);
        body.put("latitude", lat);
        body.put("longitude", lon);
        body.put("faceVerified", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        return restTemplate.exchange(url("/api/attendance/checkout"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void checkInWithinMartRadiusSucceeds() throws Exception {
        // 100m radius, exactly matching the real-world unit (meters) the
        // Admin UI collects and displays.
        Mart mart = seedMart("Radius Test Mart", 31.5000, 74.3000, 100.0);
        Agent nearAgent = seedAgent("RADIUS_NEAR_AGENT");
        String nearToken = tokenProvider.generateToken(nearAgent.getId(), nearAgent.getAgentId(), "AGENT", tenantId());

        // Exactly at the mart's own coordinates — distance ~0m, well inside 100m.
        ResponseEntity<String> nearResponse = checkIn(nearToken, nearAgent.getId(), mart.getId(),
                mart.getLatitude(), mart.getLongitude());
        assertEquals(HttpStatus.CREATED, nearResponse.getStatusCode(), nearResponse.getBody());
        JsonNode nearData = objectMapper.readTree(nearResponse.getBody()).get("data");
        assertEquals("IN", nearData.get("status").asText(),
                "Checking in right at the mart must succeed as IN: " + nearResponse.getBody());
    }

    @Test
    void checkInBeyondMartRadiusIsRejected() throws Exception {
        Mart mart = seedMart("Radius Test Mart", 31.5000, 74.3000, 100.0);
        Agent farAgent = seedAgent("RADIUS_FAR_AGENT");
        String farToken = tokenProvider.generateToken(farAgent.getId(), farAgent.getAgentId(), "AGENT", tenantId());

        // ~0.01 degrees away (~1.1km at this latitude) — far outside the
        // 100m radius. Must be REJECTED outright, not merely flagged LATE —
        // that "flag but still let through" behavior was the bug this test
        // now proves is fixed: no attendance row should ever be written for
        // a check-in attempt from outside the geofence.
        ResponseEntity<String> farResponse = checkIn(farToken, farAgent.getId(), mart.getId(),
                mart.getLatitude() + 0.01, mart.getLongitude() + 0.01);
        assertEquals(HttpStatus.BAD_REQUEST, farResponse.getStatusCode(), farResponse.getBody());
        assertTrue(farResponse.getBody().contains("You must be within"),
                "Rejection message should explain the geofence requirement: " + farResponse.getBody());
    }

    @Test
    void checkInIsAllowedWhenMartHasGeoFencingDisabled() throws Exception {
        // The per-mart opt-out (Mart.geoFencingEnabled) must still work —
        // the hard gate only applies when a mart has opted in (the default).
        Mart mart = seedMart("No-Geofence Mart", 31.5000, 74.3000, 100.0);
        mart.setGeoFencingEnabled(false);
        martRepository.save(mart);

        Agent farAgent = seedAgent("RADIUS_EXEMPT_AGENT");
        String farToken = tokenProvider.generateToken(farAgent.getId(), farAgent.getAgentId(), "AGENT", tenantId());

        ResponseEntity<String> farResponse = checkIn(farToken, farAgent.getId(), mart.getId(),
                mart.getLatitude() + 0.01, mart.getLongitude() + 0.01);
        assertEquals(HttpStatus.CREATED, farResponse.getStatusCode(),
                "A mart with geoFencingEnabled=false must not enforce the radius: " + farResponse.getBody());
    }

    @Test
    void checkOutBeyondMartRadiusIsRejected() throws Exception {
        // Checkout is gated the same as check-in — agents are expected to
        // still be at the mart when they end duty.
        Mart mart = seedMart("Radius Test Mart", 31.5000, 74.3000, 100.0);
        Agent agent = seedAgent("RADIUS_CHECKOUT_AGENT");
        String token = tokenProvider.generateToken(agent.getId(), agent.getAgentId(), "AGENT", tenantId());

        ResponseEntity<String> checkInResponse = checkIn(token, agent.getId(), mart.getId(),
                mart.getLatitude(), mart.getLongitude());
        assertEquals(HttpStatus.CREATED, checkInResponse.getStatusCode(), checkInResponse.getBody());

        ResponseEntity<String> checkOutResponse = checkOut(token, agent.getId(),
                mart.getLatitude() + 0.01, mart.getLongitude() + 0.01);
        assertEquals(HttpStatus.BAD_REQUEST, checkOutResponse.getStatusCode(), checkOutResponse.getBody());
        assertTrue(checkOutResponse.getBody().contains("You must be within"),
                "Rejection message should explain the geofence requirement: " + checkOutResponse.getBody());
    }

    @Test
    void checkOutWithinMartRadiusSucceeds() throws Exception {
        Mart mart = seedMart("Radius Test Mart", 31.5000, 74.3000, 100.0);
        Agent agent = seedAgent("RADIUS_CHECKOUT_OK_AGENT");
        String token = tokenProvider.generateToken(agent.getId(), agent.getAgentId(), "AGENT", tenantId());

        ResponseEntity<String> checkInResponse = checkIn(token, agent.getId(), mart.getId(),
                mart.getLatitude(), mart.getLongitude());
        assertEquals(HttpStatus.CREATED, checkInResponse.getStatusCode(), checkInResponse.getBody());

        ResponseEntity<String> checkOutResponse = checkOut(token, agent.getId(),
                mart.getLatitude(), mart.getLongitude());
        assertEquals(HttpStatus.OK, checkOutResponse.getStatusCode(), checkOutResponse.getBody());
    }
}
