package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.security.TokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves PUT/DELETE /api/agents/{id} can no longer be used by a non-admin
 * token, and specifically that a non-admin cannot use PUT to grant themselves
 * (or anyone) the ADMIN role. Same pattern as the other *SecurityTest classes:
 * real HTTP through the actual interceptor + controller chain, against the
 * isolated H2 test database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentControllerUpdateDeleteSecurityTest {

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

    private Agent seedAgent(String agentId, String role) {
        Agent agent = new Agent();
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setPhone("03001234567");
        agent.setPassword("Password123");
        agent.setRole(role);
        agent.setDepartment("SALES");
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    // ---------- PUT /api/agents/{id} ----------

    @Test
    void updateAgentWithoutTokenIsRejected() {
        Agent target = seedAgent("UPDNOAUTH", "SALES");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Changed Name");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(url("/api/agents/" + target.getId()), HttpMethod.PUT, request, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Agent unchanged = agentRepository.findById(target.getId()).orElseThrow();
        assertEquals("Seed UPDNOAUTH", unchanged.getName());
    }

    @Test
    void nonAdminCannotUpdateAnotherAccountOrSelfPromoteToAdmin() {
        Agent target = seedAgent("UPDVICTIM", "SALES");
        String nonAdminToken = tokenProvider.generateToken(target.getId(), "SALES_CALLER", "SALES");

        // The caller tries to both change basic profile fields AND grant
        // themselves ADMIN on their own record in a single request.
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Self Promoted");
        body.put("role", "ADMIN");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(nonAdminToken);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(url("/api/agents/" + target.getId()), HttpMethod.PUT, request, String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Agent unchanged = agentRepository.findById(target.getId()).orElseThrow();
        assertEquals("SALES", unchanged.getRole(), "Role must not have been escalated");
        assertEquals("Seed UPDVICTIM", unchanged.getName(), "No field should have been updated either");
    }

    @Test
    void adminCanUpdateAnAccountIncludingItsRole() throws Exception {
        Agent target = seedAgent("UPDTARGET", "SALES");
        String adminToken = tokenProvider.generateToken(97L, "REAL_ADMIN3", "ADMIN");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Promoted By Admin");
        body.put("role", "HR");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(url("/api/agents/" + target.getId()), HttpMethod.PUT, request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertEquals("HR", json.get("data").get("role").asText());

        Agent updated = agentRepository.findById(target.getId()).orElseThrow();
        assertEquals("HR", updated.getRole());
        assertEquals("Promoted By Admin", updated.getName());
    }

    // ---------- DELETE /api/agents/{id} ----------

    @Test
    void deleteAgentWithoutTokenIsRejected() {
        Agent target = seedAgent("DELNOAUTH", "SALES");

        ResponseEntity<String> response =
                restTemplate.exchange(url("/api/agents/" + target.getId()), HttpMethod.DELETE, null, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(agentRepository.existsById(target.getId()), "Account must still exist");
    }

    @Test
    void nonAdminCannotDeleteAnAccount() {
        Agent target = seedAgent("DELVICTIM", "SALES");
        String nonAdminToken = tokenProvider.generateToken(3L, "HR_CALLER", "HR");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(nonAdminToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange(url("/api/agents/" + target.getId()), HttpMethod.DELETE, request, String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(agentRepository.existsById(target.getId()), "Account must still exist");
    }

    @Test
    void adminCanDeleteAnAccount() {
        Agent target = seedAgent("DELTARGET", "SALES");
        String adminToken = tokenProvider.generateToken(96L, "REAL_ADMIN4", "ADMIN");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange(url("/api/agents/" + target.getId()), HttpMethod.DELETE, request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(agentRepository.findById(target.getId()).isEmpty(), "Account should have been deleted");
    }
}
