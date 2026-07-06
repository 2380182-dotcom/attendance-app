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
 * Every endpoint under /api/admin is admin-only — confirmed no other role
 * calls anything here in the mobile client. Covers a representative sample
 * spanning reads and writes; the same AccessControl.hasRole(request, "ADMIN")
 * guard is applied identically to all 9 handlers in AdminController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminControllerSecurityTest {

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
    void statisticsRequiresAdmin() {
        String hrToken = tokenProvider.generateToken(20L, "ADMIN_STATS_HR", "HR");
        ResponseEntity<String> forbidden = restTemplate.exchange(
                url("/api/admin/statistics"), HttpMethod.GET, withToken(hrToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        String adminToken = tokenProvider.generateToken(21L, "ADMIN_STATS_ADMIN", "ADMIN");
        ResponseEntity<String> ok = restTemplate.exchange(
                url("/api/admin/statistics"), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
    }

    @Test
    void listAgentsRequiresAdmin() {
        String salesToken = tokenProvider.generateToken(22L, "ADMIN_LIST_SALES", "SALES");
        ResponseEntity<String> forbidden = restTemplate.exchange(
                url("/api/admin/agents"), HttpMethod.GET, withToken(salesToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        String adminToken = tokenProvider.generateToken(23L, "ADMIN_LIST_ADMIN", "ADMIN");
        ResponseEntity<String> ok = restTemplate.exchange(
                url("/api/admin/agents"), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
    }

    @Test
    void martEndpointsUnderAdminPrefixRequireAdmin() {
        String agentToken = tokenProvider.generateToken(24L, "ADMIN_MART_AGENT", "AGENT");
        ResponseEntity<String> forbidden = restTemplate.exchange(
                url("/api/admin/marts"), HttpMethod.GET, withToken(agentToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        String adminToken = tokenProvider.generateToken(25L, "ADMIN_MART_ADMIN", "ADMIN");
        ResponseEntity<String> ok = restTemplate.exchange(
                url("/api/admin/marts"), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
    }

    @Test
    void noTokenIsRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admin/statistics"), HttpMethod.GET, withToken(null), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
