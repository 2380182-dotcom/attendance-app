package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.repository.AgentRepository;
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

/**
 * /api/reports/** — the HR and Sales per-agent CSV endpoints must respect the
 * same department boundary already built into their CSV services (HR CSV
 * never has sales data and vice versa) at the authorization layer too: an HR
 * account must not be able to call the Sales CSV endpoint and vice versa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExcelExportControllerSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AgentRepository agentRepository;

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

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(headers);
    }

    @Test
    void salesCannotPullHrAttendanceCsv() {
        Agent agent = seedAgent("HR_CSV_TARGET");
        String salesToken = tokenProvider.generateToken(40L, "HR_CSV_SALES_CALLER", "SALES");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/reports/hr/agent-attendance-csv?agentId=" + agent.getId()
                        + "&from=2026-01-01&to=2026-01-31"),
                HttpMethod.GET, withToken(salesToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "Sales must not read the HR attendance CSV");
    }

    @Test
    void hrCanPullHrAttendanceCsv() {
        Agent agent = seedAgent("HR_CSV_TARGET_OK");
        String hrToken = tokenProvider.generateToken(41L, "HR_CSV_HR_CALLER", "HR");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/reports/hr/agent-attendance-csv?agentId=" + agent.getId()
                        + "&from=2026-01-01&to=2026-01-31"),
                HttpMethod.GET, withToken(hrToken), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void hrCannotPullSalesAgentCsv() {
        Agent agent = seedAgent("SALES_CSV_TARGET");
        String hrToken = tokenProvider.generateToken(42L, "SALES_CSV_HR_CALLER", "HR");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/reports/sales/agent-sales-csv?agentId=" + agent.getId()
                        + "&from=2026-01-01&to=2026-01-31"),
                HttpMethod.GET, withToken(hrToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "HR must not read the Sales agent CSV");
    }

    @Test
    void salesCanPullSalesAgentCsv() {
        Agent agent = seedAgent("SALES_CSV_TARGET_OK");
        String salesToken = tokenProvider.generateToken(43L, "SALES_CSV_SALES_CALLER", "SALES");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/reports/sales/agent-sales-csv?agentId=" + agent.getId()
                        + "&from=2026-01-01&to=2026-01-31"),
                HttpMethod.GET, withToken(salesToken), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void generalExportRequiresAdminOrHr() {
        String salesToken = tokenProvider.generateToken(44L, "EXPORT_SALES_CALLER", "SALES");
        ResponseEntity<String> forbidden = restTemplate.exchange(
                url("/api/reports/export"), HttpMethod.GET, withToken(salesToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        String adminToken = tokenProvider.generateToken(45L, "EXPORT_ADMIN_CALLER", "ADMIN");
        ResponseEntity<String> ok = restTemplate.exchange(
                url("/api/reports/export"), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
    }

    @Test
    void agentExportAllowsSelfButNotOtherAgents() {
        Agent self = seedAgent("EXPORT_SELF");
        Agent other = seedAgent("EXPORT_OTHER");
        String selfToken = tokenProvider.generateToken(self.getId(), "EXPORT_SELF_CALLER", "AGENT");

        ResponseEntity<String> ownExport = restTemplate.exchange(
                url("/api/reports/export/agent/" + self.getId()), HttpMethod.GET, withToken(selfToken), String.class);
        assertEquals(HttpStatus.OK, ownExport.getStatusCode());

        ResponseEntity<String> othersExport = restTemplate.exchange(
                url("/api/reports/export/agent/" + other.getId()), HttpMethod.GET, withToken(selfToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, othersExport.getStatusCode(),
                "An agent must not be able to export another agent's attendance history");
    }

    @Test
    void noTokenIsRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/reports/export"), HttpMethod.GET, withToken(null), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
