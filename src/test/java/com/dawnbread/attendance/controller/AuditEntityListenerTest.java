package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.AuditLog;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AuditLogRepository;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves AuditEntityListener (entity-listener-based, not AuditLogService's
 * manual call sites) actually captures Agent/Mart mutations, and — the whole
 * point of building it this way — that it fires even when a test bypasses
 * the service layer entirely and saves/deletes through the repository
 * directly. A service-layer-only audit mechanism would miss every one of
 * the "bypass" tests below; a Hibernate-lifecycle one cannot.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditEntityListenerTest {

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
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Long tenantId() {
        return TenantTestHelper.defaultTenantId(tenantRepository);
    }

    private Agent seedAgent(String agentId, String role) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole(role);
        agent.setCreatedAt(LocalDateTime.now());
        agent.setIsActive(true);
        return agentRepository.save(agent);
    }

    private List<AuditLog> findByActionAndDetailsContaining(String action, String needle) {
        return auditLogRepository.findAll().stream()
                .filter(l -> action.equals(l.getAction()) && l.getDetails() != null && l.getDetails().contains(needle))
                .toList();
    }

    @Test
    void creatingAnAgentViaTheHttpApiProducesAnAuditRowAttributedToTheRealCaller() throws Exception {
        Agent admin = seedAgent("AUDIT_ADMIN_1", "ADMIN");
        String adminToken = tokenProvider.generateToken(admin.getId(), admin.getAgentId(), "ADMIN", tenantId());

        Map<String, Object> body = new HashMap<>();
        body.put("agentId", "AUDIT_NEW_HIRE_1");
        body.put("name", "Audit New Hire");
        body.put("email", "audit.new.hire@example.com");
        body.put("phone", "03001234567");
        body.put("password", "Password123");
        body.put("role", "AGENT");
        body.put("department", "SALES");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/agents"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), response.getBody());

        List<AuditLog> rows = findByActionAndDetailsContaining("AGENT_CREATED", "AUDIT_NEW_HIRE_1");
        assertEquals(1, rows.size(), "Exactly one audit row must exist for this creation");
        assertEquals(admin.getAgentId(), rows.get(0).getUsername(),
                "The audit row must attribute the change to the real caller (the admin), not the created agent");
        assertEquals("SUCCESS", rows.get(0).getStatus());
    }

    @Test
    void updatingAnAgentDirectlyViaRepositoryStillProducesAnAuditRow() {
        // No HTTP request, no AgentService call — proves this isn't a
        // service-layer hook that a bypassing caller could skip.
        Agent agent = seedAgent("AUDIT_BYPASS_UPDATE", "AGENT");
        agent.setName("Renamed Directly");
        agentRepository.save(agent);

        List<AuditLog> rows = findByActionAndDetailsContaining("AGENT_UPDATED", "AUDIT_BYPASS_UPDATE");
        assertFalse(rows.isEmpty(), "A direct repository update must still produce an audit row");
        assertTrue(rows.get(0).getDetails().contains("Renamed Directly"),
                "The audit row must reflect the updated state: " + rows.get(0).getDetails());
    }

    @Test
    void deletingAnAgentDirectlyViaRepositoryStillProducesAnAuditRow() {
        Agent agent = seedAgent("AUDIT_BYPASS_DELETE", "AGENT");
        Long deletedId = agent.getId();
        agentRepository.delete(agent);

        List<AuditLog> rows = findByActionAndDetailsContaining("AGENT_DELETED", "AUDIT_BYPASS_DELETE");
        assertFalse(rows.isEmpty(), "A direct repository delete must still produce an audit row");
        assertTrue(rows.get(0).getDetails().contains("id=" + deletedId),
                "The audit row must capture the deleted row's id before it's gone: " + rows.get(0).getDetails());
    }

    @Test
    void martCreatedDirectlyViaRepositoryProducesAnAuditRow() {
        Mart mart = new Mart();
        mart.setTenantId(tenantId());
        mart.setName("Audit Test Mart " + System.nanoTime());
        mart.setAddress("1 Audit Street");
        mart.setLatitude(31.5);
        mart.setLongitude(74.3);
        mart.setRadius(100.0);
        mart.setIsActive(true);
        mart.setCreatedAt(LocalDateTime.now());
        Mart saved = martRepository.save(mart);

        List<AuditLog> rows = findByActionAndDetailsContaining("MART_CREATED", "id=" + saved.getId());
        assertFalse(rows.isEmpty(), "Creating a Mart directly via the repository must produce an audit row");
    }

    @Test
    void auditDetailsNeverIncludeSensitiveAgentFields() {
        Agent agent = seedAgent("AUDIT_SENSITIVE_CHECK", "AGENT");
        agent.setPassword("$2a$10$SuperSecretHashedPasswordValue");
        agent.setFaceTemplate("raw-face-template-blob-should-never-leak");
        agent.setFaceEmbedding("raw-face-embedding-floats-should-never-leak");
        agentRepository.save(agent);

        List<AuditLog> rows = findByActionAndDetailsContaining("AGENT_UPDATED", "AUDIT_SENSITIVE_CHECK");
        assertFalse(rows.isEmpty());
        String details = rows.get(rows.size() - 1).getDetails();
        assertFalse(details.contains("SuperSecretHashedPasswordValue"), "Audit details must never contain the password: " + details);
        assertFalse(details.contains("raw-face-template-blob-should-never-leak"), "Audit details must never contain the face template: " + details);
        assertFalse(details.contains("raw-face-embedding-floats-should-never-leak"), "Audit details must never contain the face embedding: " + details);
    }

    @Test
    void noActorContextFallsBackToSystemRatherThanFailing() {
        // This test method runs no HTTP request, so SecurityInterceptor never
        // ran and AuditContext is unset on this thread — the listener must
        // degrade gracefully, not throw and roll back the actual save.
        Agent agent = seedAgent("AUDIT_NO_ACTOR", "AGENT");

        List<AuditLog> rows = findByActionAndDetailsContaining("AGENT_CREATED", "AUDIT_NO_ACTOR");
        assertFalse(rows.isEmpty());
        assertEquals("SYSTEM", rows.get(0).getUsername());
    }
}
