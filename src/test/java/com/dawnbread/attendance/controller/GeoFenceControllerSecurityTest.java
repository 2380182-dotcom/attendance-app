package com.dawnbread.attendance.controller;

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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * /api/geo-fence/check stays open to any authenticated role (agents call it
 * during check-in/out). The log-reading endpoints expose other agents'
 * location history and are Admin/HR only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GeoFenceControllerSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(headers);
    }

    @Test
    void agentCanCallCheckGeoFence() {
        String agentToken = tokenProvider.generateToken(50L, "GEOFENCE_AGENT", "AGENT");

        Map<String, Object> body = new HashMap<>();
        body.put("agentId", 50);
        body.put("latitude", 31.5);
        body.put("longitude", 74.3);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(agentToken);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/geo-fence/check"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        // Reaches the handler successfully (200) or a business-logic 400 (e.g. no
        // matching mart/attendance) — either way it must not be blocked by role.
        assertEquals(false, response.getStatusCode().equals(HttpStatus.FORBIDDEN),
                "checkGeoFence must stay open to any authenticated role");
    }

    @Test
    void agentCannotReadGeoFenceLogs() {
        String agentToken = tokenProvider.generateToken(51L, "GEOFENCE_LOGS_AGENT", "AGENT");

        ResponseEntity<String> agentLogs = restTemplate.exchange(
                url("/api/geo-fence/logs/agent/51"), HttpMethod.GET, withToken(agentToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, agentLogs.getStatusCode());

        ResponseEntity<String> allLogs = restTemplate.exchange(
                url("/api/geo-fence/logs"), HttpMethod.GET, withToken(agentToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, allLogs.getStatusCode());
    }

    @Test
    void adminAndHrCanReadGeoFenceLogs() {
        for (String role : new String[] { "ADMIN", "HR" }) {
            String token = tokenProvider.generateToken(52L, "GEOFENCE_LOGS_" + role, role);

            ResponseEntity<String> agentLogs = restTemplate.exchange(
                    url("/api/geo-fence/logs/agent/52"), HttpMethod.GET, withToken(token), String.class);
            assertEquals(HttpStatus.OK, agentLogs.getStatusCode(), role + " should be able to read agent logs");

            ResponseEntity<String> allLogs = restTemplate.exchange(
                    url("/api/geo-fence/logs"), HttpMethod.GET, withToken(token), String.class);
            assertEquals(HttpStatus.OK, allLogs.getStatusCode(), role + " should be able to read all logs");
        }
    }

    @Test
    void noTokenIsRejectedOnLogs() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/geo-fence/logs"), HttpMethod.GET, withToken(null), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
