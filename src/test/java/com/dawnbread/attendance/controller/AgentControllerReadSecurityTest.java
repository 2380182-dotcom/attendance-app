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
 * GET endpoints on /api/agents (roster browsing) — confirmed callers are
 * SalesAgentReportScreen, HRAgentAttendanceReportScreen, ReportGeneratorScreen,
 * and AdminUsersScreen, all agent-picker UIs for Sales/HR/Admin. A bare Agent
 * token has no legitimate reason to enumerate the roster.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentControllerReadSecurityTest {

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
    void bareAgentCannotBrowseRoster() {
        String agentToken = tokenProvider.generateToken(80L, "ROSTER_AGENT", "AGENT");

        String[] endpoints = {
                "/api/agents",
                "/api/agents/80",
                "/api/agents/agentId/ROSTER_AGENT",
                "/api/agents/search?name=a",
                "/api/agents/active",
                "/api/agents/checked-in-today",
                "/api/agents/count"
        };

        for (String endpoint : endpoints) {
            ResponseEntity<String> response = restTemplate.exchange(
                    url(endpoint), HttpMethod.GET, withToken(agentToken), String.class);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                    "Bare Agent role must not be able to GET " + endpoint);
        }
    }

    @Test
    void salesHrAndAdminCanBrowseRoster() {
        String[] endpoints = {
                "/api/agents",
                "/api/agents/active",
                "/api/agents/checked-in-today",
                "/api/agents/count"
        };

        for (String role : new String[] { "SALES", "HR", "ADMIN" }) {
            String token = tokenProvider.generateToken(81L, "ROSTER_" + role, role);
            for (String endpoint : endpoints) {
                ResponseEntity<String> response = restTemplate.exchange(
                        url(endpoint), HttpMethod.GET, withToken(token), String.class);
                assertEquals(HttpStatus.OK, response.getStatusCode(),
                        role + " should be able to GET " + endpoint);
            }
        }
    }

    @Test
    void noTokenIsRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/agents"), HttpMethod.GET, withToken(null), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
