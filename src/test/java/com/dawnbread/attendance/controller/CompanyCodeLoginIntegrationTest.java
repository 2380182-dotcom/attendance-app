package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.TenantRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the real end-to-end Company Code login contract via HTTP, not a
 * unit test of AgentService in isolation: the same agentId/password pair
 * exists validly in two different tenants, and the company code is what
 * decides which one actually authenticates — including the specific case
 * the whole redesign exists to prevent: a genuinely valid agentId/password
 * combination succeeding under the WRONG company.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompanyCodeLoginIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Tenant seedTenant(String companyCode, String name) {
        Tenant tenant = new Tenant();
        tenant.setCompanyCode(companyCode);
        tenant.setName(name);
        tenant.setIsActive(true);
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setCreatedBy("TEST");
        return tenantRepository.save(tenant);
    }

    private Agent seedAgent(Long tenantId, String agentId, String rawPassword) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId);
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "-" + tenantId + "@example.com");
        agent.setPassword(passwordEncoder.encode(rawPassword));
        agent.setRole("AGENT");
        agent.setDepartment("SALES");
        agent.setCreatedAt(LocalDateTime.now());
        agent.setIsActive(true);
        return agentRepository.save(agent);
    }

    private ResponseEntity<String> login(String companyCode, String agentId, String password) {
        Map<String, String> body = new HashMap<>();
        if (companyCode != null) body.put("companyCode", companyCode);
        if (agentId != null) body.put("agentId", agentId);
        if (password != null) body.put("password", password);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url("/api/auth/login"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void loginScopesToTheCorrectTenantByCompanyCode() throws Exception {
        Tenant tenantA = seedTenant("COMPA", "Company A");
        Tenant tenantB = seedTenant("COMPB", "Company B");

        // Deliberately the SAME agentId AND password in both tenants — the
        // only thing that can distinguish them is the company code.
        seedAgent(tenantA.getId(), "COMMON001", "SharedPass123");
        seedAgent(tenantB.getId(), "COMMON001", "SharedPass123");

        ResponseEntity<String> responseA = login("COMPA", "COMMON001", "SharedPass123");
        assertEquals(HttpStatus.OK, responseA.getStatusCode(), responseA.getBody());
        JsonNode dataA = objectMapper.readTree(responseA.getBody()).get("data");
        assertEquals("COMPA", dataA.get("companyCode").asText());
        assertTrue(dataA.has("token") && !dataA.get("token").asText().isBlank());

        ResponseEntity<String> responseB = login("COMPB", "COMMON001", "SharedPass123");
        assertEquals(HttpStatus.OK, responseB.getStatusCode(), responseB.getBody());
        JsonNode dataB = objectMapper.readTree(responseB.getBody()).get("data");
        assertEquals("COMPB", dataB.get("companyCode").asText());

        // Two different agents (different tenants), so different ids even
        // though agentId/password were identical strings.
        assertTrue(dataA.get("id").asLong() != dataB.get("id").asLong());
    }

    @Test
    void validAgentIdAndPasswordFailsUnderTheWrongCompanyCode() {
        Tenant tenantA = seedTenant("REALCO", "Real Company");
        Tenant tenantB = seedTenant("OTHERCO", "Other Company");
        seedAgent(tenantA.getId(), "VALID_USER", "CorrectPass123");
        // No agent named VALID_USER exists under tenantB at all.

        ResponseEntity<String> correctLogin = login("REALCO", "VALID_USER", "CorrectPass123");
        assertEquals(HttpStatus.OK, correctLogin.getStatusCode(), "Sanity check — correct company must work: " + correctLogin.getBody());

        ResponseEntity<String> wrongCompanyLogin = login("OTHERCO", "VALID_USER", "CorrectPass123");
        assertEquals(HttpStatus.UNAUTHORIZED, wrongCompanyLogin.getStatusCode(),
                "A genuinely valid agentId/password pair must be rejected under the wrong company code: " + wrongCompanyLogin.getBody());
    }

    @Test
    void unknownCompanyCodeIsRejected() {
        ResponseEntity<String> response = login("DOES_NOT_EXIST_CO", "ANYONE", "AnyPassword123");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), response.getBody());
    }

    @Test
    void inactiveTenantIsRejectedEvenWithCorrectCredentials() {
        Tenant suspended = seedTenant("SUSPENDEDCO", "Suspended Company");
        suspended.setIsActive(false);
        tenantRepository.save(suspended);
        seedAgent(suspended.getId(), "STILL_VALID", "Password123");

        ResponseEntity<String> response = login("SUSPENDEDCO", "STILL_VALID", "Password123");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), response.getBody());
    }

    @Test
    void missingCompanyCodeIsRejectedWithBadRequest() {
        ResponseEntity<String> response = login(null, "SOMEONE", "Password123");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
