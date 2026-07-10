package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.MartRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one test this whole multi-tenancy effort exists to make pass: two real
 * tenants, identically-shaped data, real HTTP through the actual Hibernate
 * filter + SecurityInterceptor chain — not a unit test of the filter
 * mechanism in isolation. Proves Tenant A's token can never read or write
 * Tenant B's rows, including when guessing Tenant B's real primary keys
 * directly (the exact class of bug a manually-added "WHERE tenant_id = ?" on
 * every repository method would be one missed method away from reintroducing).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantIsolationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private MartRepository martRepository;

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

    private Agent seedAgent(Long tenantId, String agentId, String role) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId);
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "-" + tenantId + "@example.com");
        agent.setRole(role);
        agent.setDepartment("MANAGEMENT");
        agent.setCreatedAt(LocalDateTime.now());
        agent.setIsActive(true);
        return agentRepository.save(agent);
    }

    private Mart seedMart(Long tenantId, String name) {
        Mart mart = new Mart();
        mart.setTenantId(tenantId);
        mart.setName(name);
        mart.setAddress("1 Test Street");
        mart.setLatitude(31.5);
        mart.setLongitude(74.3);
        mart.setRadius(100.0);
        mart.setIsActive(true);
        mart.setCreatedAt(LocalDateTime.now());
        return martRepository.save(mart);
    }

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    @Test
    void crossTenantReadsAreBlockedEvenWithGuessedIds() {
        Tenant tenantA = seedTenant("ISOA", "Isolation Co A");
        Tenant tenantB = seedTenant("ISOB", "Isolation Co B");

        // Deliberately the SAME agentId string in both tenants — proves the
        // per-tenant unique index (not a global one) is what's really in effect.
        Agent adminA = seedAgent(tenantA.getId(), "SHARED_ADMIN", "ADMIN");
        Agent adminB = seedAgent(tenantB.getId(), "SHARED_ADMIN", "ADMIN");
        Mart martA = seedMart(tenantA.getId(), "Mart Alpha");
        Mart martB = seedMart(tenantB.getId(), "Mart Beta");

        String tokenA = tokenProvider.generateToken(adminA.getId(), adminA.getAgentId(), "ADMIN", tenantA.getId());

        // --- List endpoints only ever show the caller's own tenant ---
        ResponseEntity<String> agentList = restTemplate.exchange(
                url("/api/agents"), HttpMethod.GET, withToken(tokenA), String.class);
        assertEquals(HttpStatus.OK, agentList.getStatusCode());
        assertTrue(agentList.getBody().contains(adminA.getEmail()), "Tenant A's own admin must be visible: " + agentList.getBody());
        assertFalse(agentList.getBody().contains(adminB.getEmail()), "Tenant B's admin must NOT leak into Tenant A's agent list: " + agentList.getBody());

        ResponseEntity<String> martList = restTemplate.exchange(
                url("/api/marts"), HttpMethod.GET, withToken(tokenA), String.class);
        assertEquals(HttpStatus.OK, martList.getStatusCode());
        assertTrue(martList.getBody().contains("Mart Alpha"), "Tenant A's own mart must be visible: " + martList.getBody());
        assertFalse(martList.getBody().contains("Mart Beta"), "Tenant B's mart must NOT leak into Tenant A's mart list: " + martList.getBody());

        // --- Guessing Tenant B's real primary key directly must 404, not leak data ---
        ResponseEntity<String> guessedAgent = restTemplate.exchange(
                url("/api/agents/" + adminB.getId()), HttpMethod.GET, withToken(tokenA), String.class);
        assertEquals(HttpStatus.NOT_FOUND, guessedAgent.getStatusCode(),
                "Fetching Tenant B's agent by its real id from Tenant A's token must 404, not succeed: " + guessedAgent.getBody());

        ResponseEntity<String> guessedMart = restTemplate.exchange(
                url("/api/marts/" + martB.getId()), HttpMethod.GET, withToken(tokenA), String.class);
        assertEquals(HttpStatus.NOT_FOUND, guessedMart.getStatusCode(),
                "Fetching Tenant B's mart by its real id from Tenant A's token must 404, not succeed: " + guessedMart.getBody());

        // --- Meanwhile Tenant B's own token still sees its own data fine ---
        String tokenB = tokenProvider.generateToken(adminB.getId(), adminB.getAgentId(), "ADMIN", tenantB.getId());
        ResponseEntity<String> ownAgent = restTemplate.exchange(
                url("/api/agents/" + adminB.getId()), HttpMethod.GET, withToken(tokenB), String.class);
        assertEquals(HttpStatus.OK, ownAgent.getStatusCode(), "Tenant B reading its own agent must still work: " + ownAgent.getBody());
    }

    @Test
    void newlyCreatedDataIsAutoStampedWithTheCreatingCallersTenant() {
        Tenant tenantA = seedTenant("ISOC", "Isolation Co C");
        Agent adminA = seedAgent(tenantA.getId(), "CREATOR_ADMIN", "ADMIN");
        String tokenA = tokenProvider.generateToken(adminA.getId(), adminA.getAgentId(), "ADMIN", tenantA.getId());

        Map<String, Object> newAgentBody = new HashMap<>();
        newAgentBody.put("agentId", "NEW_HIRE_001");
        newAgentBody.put("name", "New Hire");
        newAgentBody.put("email", "new.hire@example.com");
        newAgentBody.put("phone", "03001234567");
        newAgentBody.put("password", "Password123");
        newAgentBody.put("role", "AGENT");
        newAgentBody.put("department", "SALES");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenA);

        ResponseEntity<String> createResponse = restTemplate.exchange(
                url("/api/agents"), HttpMethod.POST, new HttpEntity<>(newAgentBody, headers), String.class);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode(), "Create must succeed: " + createResponse.getBody());

        // Fetched via a plain repository call in the test's own thread — no
        // Hibernate filter is active here, so this reads the raw row exactly
        // as TenantEntityListener stamped it during the HTTP request above.
        Agent created = agentRepository.findByAgentId("NEW_HIRE_001").orElseThrow();
        assertEquals(tenantA.getId(), created.getTenantId(),
                "A new agent created by Tenant A's admin must be auto-stamped with Tenant A's own tenant_id, not left null or defaulted elsewhere");
    }

    /**
     * Audit Finding 01: before AgentController.createAgent bound
     * AgentRegistrationDTO instead of the raw Agent entity, a client-supplied
     * `tenantId` field in the request body was accepted verbatim — any admin
     * could plant a row in an arbitrary tenant by simply setting the field.
     * AgentRegistrationDTO has no tenantId field at all, so this must now be
     * silently ignored and the real caller's tenant used instead, no matter
     * what the request body claims.
     */
    @Test
    void tenantIdInTheRequestBodyIsIgnoredWhenCreatingAnAgent() {
        Tenant tenantA = seedTenant("ISOD_A", "Isolation Co D-A");
        Tenant tenantB = seedTenant("ISOD_B", "Isolation Co D-B");
        Agent adminA = seedAgent(tenantA.getId(), "INJECT_ADMIN", "ADMIN");
        String tokenA = tokenProvider.generateToken(adminA.getId(), adminA.getAgentId(), "ADMIN", tenantA.getId());

        Map<String, Object> maliciousBody = new HashMap<>();
        maliciousBody.put("agentId", "INJECTED_HIRE_001");
        maliciousBody.put("name", "Injected Hire");
        maliciousBody.put("email", "injected.hire@example.com");
        maliciousBody.put("phone", "03001234567");
        maliciousBody.put("password", "Password123");
        maliciousBody.put("role", "AGENT");
        maliciousBody.put("department", "SALES");
        maliciousBody.put("tenantId", tenantB.getId()); // attacker-controlled — must be ignored

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenA);

        ResponseEntity<String> createResponse = restTemplate.exchange(
                url("/api/agents"), HttpMethod.POST, new HttpEntity<>(maliciousBody, headers), String.class);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode(), "Create must succeed: " + createResponse.getBody());

        Agent created = agentRepository.findByAgentId("INJECTED_HIRE_001").orElseThrow();
        assertEquals(tenantA.getId(), created.getTenantId(),
                "A tenantId supplied in the request body must never override the caller's real tenant");
        assertFalse(created.getTenantId().equals(tenantB.getId()),
                "The agent must not have been planted in the attacker-targeted tenant");
    }
}
