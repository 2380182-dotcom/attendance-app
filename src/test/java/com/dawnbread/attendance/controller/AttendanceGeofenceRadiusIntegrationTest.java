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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Proves the AttendanceService.checkIn() distance-based LATE check now
 * compares meters against meters, matching mart.radius's real unit (see
 * AdminMartScreen.js: "Radius (meters)") and GeoFencingService's own
 * distance <= mart.getRadius() comparison. Before the fix, "distance >
 * mart.getRadius() * 1000" inflated a 100m mart's late-threshold to 100km,
 * so checking in 1+ km away from the mart would still silently report "IN".
 *
 * Isolates the distance effect from the separate shift-timing LATE trigger
 * (AttendanceService ORs the two together) by comparing a near check-in
 * against a far check-in for the SAME mart, moments apart in the same test
 * — shift compliance is effectively constant between the two calls, so any
 * difference in outcome is attributable to distance alone.
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

    @Test
    void checkInBeyondMartRadiusIsFlaggedLateNotIn() throws Exception {
        // 100m radius, exactly matching the real-world unit (meters) the
        // Admin UI collects and displays.
        Mart mart = seedMart("Radius Test Mart", 31.5000, 74.3000, 100.0);

        Agent nearAgent = seedAgent("RADIUS_NEAR_AGENT");
        Agent farAgent = seedAgent("RADIUS_FAR_AGENT");
        String nearToken = tokenProvider.generateToken(nearAgent.getId(), nearAgent.getAgentId(), "AGENT", tenantId());
        String farToken = tokenProvider.generateToken(farAgent.getId(), farAgent.getAgentId(), "AGENT", tenantId());

        // Near: exactly at the mart's own coordinates — distance ~0m, well inside 100m.
        ResponseEntity<String> nearResponse = checkIn(nearToken, nearAgent.getId(), mart.getId(),
                mart.getLatitude(), mart.getLongitude());
        assertEquals(HttpStatus.CREATED, nearResponse.getStatusCode(), nearResponse.getBody());
        JsonNode nearData = objectMapper.readTree(nearResponse.getBody()).get("data");
        double nearDistance = nearData.get("distanceFromMart").asDouble();
        assertEquals("IN", nearData.get("status").asText(),
                "Checking in right at the mart (distance=" + nearDistance + "m) must not be LATE: " + nearResponse.getBody());

        // Far: ~0.01 degrees away (~1.1km at this latitude) — far outside the
        // 100m radius, but nowhere near the OLD buggy 100km threshold
        // (radius * 1000), so this specifically proves the fix and not just
        // an extreme distance that would have failed either way.
        ResponseEntity<String> farResponse = checkIn(farToken, farAgent.getId(), mart.getId(),
                mart.getLatitude() + 0.01, mart.getLongitude() + 0.01);
        assertEquals(HttpStatus.CREATED, farResponse.getStatusCode(), farResponse.getBody());
        JsonNode farData = objectMapper.readTree(farResponse.getBody()).get("data");
        double farDistance = farData.get("distanceFromMart").asDouble();
        assertEquals("LATE", farData.get("status").asText(),
                "Checking in " + farDistance + "m from a 100m-radius mart must be flagged LATE: " + farResponse.getBody());

        // The two calls happen moments apart in the same test, so shift
        // compliance (the OTHER thing that can trigger LATE) is effectively
        // identical for both — the differing outcome is attributable to
        // distance alone, proving this isn't just shift-based lateness.
        assertNotEquals(nearData.get("status").asText(), farData.get("status").asText());
    }
}
