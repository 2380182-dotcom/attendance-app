package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.TenantRepository;
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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * /api/attendance/** — checkin/checkout/face-result/verify-midday/verify-scheduled
 * stay open to any authenticated role (self-service actions); every read
 * endpoint here is either a cross-agent aggregate (management-only: Admin/HR/
 * Sales) or a specific agent's record (self-or-management).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttendanceControllerSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Agent seedAgent(String agentId) {
        Agent agent = new Agent();
        agent.setTenantId(TenantTestHelper.defaultTenantId(tenantRepository));
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole("AGENT");
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(headers);
    }

    @Test
    void bareAgentCannotReadCrossAgentAggregates() {
        String agentToken = tokenProvider.generateToken(90L, "ATT_AGGREGATE_AGENT", "AGENT");

        String[] managementOnlyEndpoints = {
                "/api/attendance",
                "/api/attendance/1",
                "/api/attendance/date-range?startDate=2026-01-01T00:00:00&endDate=2026-01-31T23:59:59",
                "/api/attendance/status/IN",
                "/api/attendance/open",
                "/api/attendance/report/today",
                "/api/attendance/report/daily?date=2026-01-01",
                "/api/attendance/statistics?startDate=2026-01-01T00:00:00&endDate=2026-01-31T23:59:59",
                "/api/attendance/daily-report?date=2026-01-01"
        };

        for (String endpoint : managementOnlyEndpoints) {
            ResponseEntity<String> response = restTemplate.exchange(
                    url(endpoint), HttpMethod.GET, withToken(agentToken), String.class);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                    "Bare Agent role must not be able to GET " + endpoint);
        }
    }

    @Test
    void managementRolesCanReadCrossAgentAggregates() {
        String[] endpoints = {
                "/api/attendance",
                "/api/attendance/date-range?startDate=2026-01-01T00:00:00&endDate=2026-01-31T23:59:59",
                "/api/attendance/status/IN",
                "/api/attendance/open",
                "/api/attendance/report/today",
                "/api/attendance/report/daily?date=2026-01-01",
                "/api/attendance/statistics?startDate=2026-01-01T00:00:00&endDate=2026-01-31T23:59:59",
                "/api/attendance/daily-report?date=2026-01-01"
        };

        for (String role : new String[] { "ADMIN", "HR", "SALES" }) {
            String token = tokenProvider.generateToken(91L, "ATT_AGGREGATE_" + role, role);
            for (String endpoint : endpoints) {
                ResponseEntity<String> response = restTemplate.exchange(
                        url(endpoint), HttpMethod.GET, withToken(token), String.class);
                assertEquals(HttpStatus.OK, response.getStatusCode(),
                        role + " should be able to GET " + endpoint);
            }
        }
    }

    @Test
    void agentCanReadOwnRecordsButNotAnotherAgents() {
        Agent self = seedAgent("ATT_SELF");
        Agent other = seedAgent("ATT_OTHER");
        String selfToken = tokenProvider.generateToken(self.getId(), "ATT_SELF_CALLER", "AGENT");

        String[] ownEndpoints = {
                "/api/attendance/agent/" + self.getId(),
                "/api/attendance/agent/" + self.getId() + "/today",
                "/api/attendance/agent/" + self.getId() + "/date-range?startDate=2026-01-01T00:00:00&endDate=2026-01-31T23:59:59",
                "/api/attendance/agent/" + self.getId() + "/monthly?year=2026&month=1",
                "/api/attendance/agent/" + self.getId() + "/is-checked-in",
                "/api/attendance/agent/" + self.getId() + "/count"
        };
        for (String endpoint : ownEndpoints) {
            ResponseEntity<String> response = restTemplate.exchange(
                    url(endpoint), HttpMethod.GET, withToken(selfToken), String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "Self should be able to GET " + endpoint);
        }

        String[] othersEndpoints = {
                "/api/attendance/agent/" + other.getId(),
                "/api/attendance/agent/" + other.getId() + "/today",
                "/api/attendance/agent/" + other.getId() + "/date-range?startDate=2026-01-01T00:00:00&endDate=2026-01-31T23:59:59",
                "/api/attendance/agent/" + other.getId() + "/monthly?year=2026&month=1",
                "/api/attendance/agent/" + other.getId() + "/is-checked-in",
                "/api/attendance/agent/" + other.getId() + "/count"
        };
        for (String endpoint : othersEndpoints) {
            ResponseEntity<String> response = restTemplate.exchange(
                    url(endpoint), HttpMethod.GET, withToken(selfToken), String.class);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                    "An agent must not be able to GET another agent's " + endpoint);
        }
    }

    @Test
    void managementCanReadAnyAgentsRecords() {
        Agent target = seedAgent("ATT_MGMT_TARGET");
        String adminToken = tokenProvider.generateToken(92L, "ATT_MGMT_ADMIN", "ADMIN");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/attendance/agent/" + target.getId()), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void checkInEndpointStaysOpenToAnyAuthenticatedRole() {
        String agentToken = tokenProvider.generateToken(93L, "ATT_CHECKIN_AGENT", "AGENT");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(agentToken);

        // No body is supplied, so this will fail validation/business-logic — the
        // point is only that it must not be blocked at the role-check layer (403).
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/attendance/checkin"), HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertNotEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void noTokenIsRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/attendance"), HttpMethod.GET, withToken(null), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
