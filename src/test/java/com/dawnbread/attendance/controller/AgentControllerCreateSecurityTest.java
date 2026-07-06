package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.repository.AgentRepository;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves POST /api/agents can no longer be used by a non-admin token to create
 * accounts (including ADMIN accounts), end-to-end through the real interceptor
 * + controller chain, against the isolated H2 test database. Same pattern as
 * AuthControllerRegisterSecurityTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentControllerCreateSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AgentRepository agentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Map<String, Object> newAgentPayload(String agentId, String role) {
        Map<String, Object> body = new HashMap<>();
        body.put("agentId", agentId);
        body.put("name", "Test User " + agentId);
        body.put("email", agentId.toLowerCase() + "@example.com");
        body.put("phone", "03001234567");
        body.put("password", "Password123");
        body.put("role", role);
        body.put("department", "SALES");
        return body;
    }

    @Test
    void createAgentWithoutTokenIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(newAgentPayload("NOAUTH02", "ADMIN"), headers);

        ResponseEntity<String> response =
                restTemplate.exchange(url("/api/agents"), HttpMethod.POST, request, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(agentRepository.existsByAgentId("NOAUTH02"), "No account should have been created");
    }

    @Test
    void createAgentWithNonAdminTokenIsForbiddenEvenWhenBodyRequestsAdminRole() {
        String nonAdminToken = tokenProvider.generateToken(2L, "SALES_CALLER", "SALES");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(nonAdminToken);
        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(newAgentPayload("ESCALATE2", "ADMIN"), headers);

        ResponseEntity<String> response =
                restTemplate.exchange(url("/api/agents"), HttpMethod.POST, request, String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertFalse(agentRepository.existsByAgentId("ESCALATE2"),
                "A non-admin token must not be able to create an ADMIN account via /api/agents");
    }

    @Test
    void createAgentWithAdminTokenSucceedsAndPersistsRequestedRole() throws Exception {
        String adminToken = tokenProvider.generateToken(98L, "REAL_ADMIN2", "ADMIN");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);
        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(newAgentPayload("NEWHIRE2", "HR"), headers);

        ResponseEntity<String> response =
                restTemplate.exchange(url("/api/agents"), HttpMethod.POST, request, String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertTrue(body.get("success").asBoolean());
        assertEquals("HR", body.get("data").get("role").asText());

        Agent saved = agentRepository.findByAgentId("NEWHIRE2").orElseThrow();
        assertEquals("HR", saved.getRole());
    }
}
