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
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * /api/hr/dashboard/** backs HRDashboardScreen (HR-role only reachable in the
 * mobile navigator). Admin/HR only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HRDashboardControllerSecurityTest {

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
    void salesCannotReadHrDashboard() {
        String salesToken = tokenProvider.generateToken(100L, "HRDASH_SALES", "SALES");

        for (String endpoint : new String[] { "/api/hr/dashboard/attendance-sales",
                "/api/hr/dashboard/top-performers", "/api/hr/dashboard/compliance" }) {
            ResponseEntity<String> response = restTemplate.exchange(
                    url(endpoint), HttpMethod.GET, withToken(salesToken), String.class);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "Sales must not read " + endpoint);
        }
    }

    @Test
    void hrAndAdminCanReadHrDashboard() {
        for (String role : new String[] { "HR", "ADMIN" }) {
            String token = tokenProvider.generateToken(101L, "HRDASH_" + role, role);
            for (String endpoint : new String[] { "/api/hr/dashboard/attendance-sales",
                    "/api/hr/dashboard/top-performers", "/api/hr/dashboard/compliance" }) {
                ResponseEntity<String> response = restTemplate.exchange(
                        url(endpoint), HttpMethod.GET, withToken(token), String.class);
                assertEquals(HttpStatus.OK, response.getStatusCode(), role + " should read " + endpoint);
            }
        }
    }

    @Test
    void noTokenIsRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/hr/dashboard/attendance-sales"), HttpMethod.GET, withToken(null), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
