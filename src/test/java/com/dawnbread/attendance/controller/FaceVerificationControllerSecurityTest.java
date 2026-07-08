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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * /api/face/** — self-or-admin for reading/enrolling a specific agent's face
 * data (confirmed callers: agents enroll their own face, AdminUsersScreen
 * enrolls faces on behalf of agents it's onboarding). Config mutation is
 * admin-only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FaceVerificationControllerSecurityTest {

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
    void agentCanReadOwnFaceStatusButNotAnothers() {
        Agent self = seedAgent("FACE_SELF");
        Agent other = seedAgent("FACE_OTHER");
        String selfToken = tokenProvider.generateToken(self.getId(), "FACE_SELF_CALLER", "AGENT");

        ResponseEntity<String> ownStatus = restTemplate.exchange(
                url("/api/face/status/" + self.getId()), HttpMethod.GET, withToken(selfToken), String.class);
        assertEquals(HttpStatus.OK, ownStatus.getStatusCode());

        ResponseEntity<String> othersStatus = restTemplate.exchange(
                url("/api/face/status/" + other.getId()), HttpMethod.GET, withToken(selfToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, othersStatus.getStatusCode());
    }

    @Test
    void adminCanReadAnyAgentsFaceStatusScheduleAndEmbedding() {
        Agent target = seedAgent("FACE_ADMIN_TARGET");
        String adminToken = tokenProvider.generateToken(70L, "FACE_ADMIN_CALLER", "ADMIN");

        ResponseEntity<String> status = restTemplate.exchange(
                url("/api/face/status/" + target.getId()), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, status.getStatusCode());

        ResponseEntity<String> schedule = restTemplate.exchange(
                url("/api/face/schedule/" + target.getId()), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, schedule.getStatusCode());

        ResponseEntity<String> embedding = restTemplate.exchange(
                url("/api/face/embedding/" + target.getId()), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, embedding.getStatusCode());
    }

    @Test
    void agentCanEnrollOwnFaceAndAdminCanEnrollOnBehalfOfAnAgent() {
        Agent self = seedAgent("FACE_ENROLL_SELF");
        String selfToken = tokenProvider.generateToken(self.getId(), "FACE_ENROLL_SELF_CALLER", "AGENT");

        Map<String, Object> selfBody = new HashMap<>();
        selfBody.put("agentId", self.getId());
        selfBody.put("embedding", "ZmFrZS1lbWJlZGRpbmctZGF0YQ==");
        HttpHeaders selfHeaders = new HttpHeaders();
        selfHeaders.setContentType(MediaType.APPLICATION_JSON);
        selfHeaders.setBearerAuth(selfToken);
        ResponseEntity<String> selfEnroll = restTemplate.exchange(
                url("/api/face/embedding"), HttpMethod.POST, new HttpEntity<>(selfBody, selfHeaders), String.class);
        assertEquals(HttpStatus.CREATED, selfEnroll.getStatusCode());

        Agent onboarding = seedAgent("FACE_ENROLL_BY_ADMIN");
        String adminToken = tokenProvider.generateToken(71L, "FACE_ENROLL_ADMIN_CALLER", "ADMIN");
        Map<String, Object> adminBody = new HashMap<>();
        adminBody.put("agentId", onboarding.getId());
        adminBody.put("embedding", "ZmFrZS1lbWJlZGRpbmctZGF0YQ==");
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.setBearerAuth(adminToken);
        ResponseEntity<String> adminEnroll = restTemplate.exchange(
                url("/api/face/embedding"), HttpMethod.POST, new HttpEntity<>(adminBody, adminHeaders), String.class);
        assertEquals(HttpStatus.CREATED, adminEnroll.getStatusCode(), "Admin must be able to enroll on an agent's behalf");
    }

    @Test
    void agentCannotEnrollAnotherAgentsFace() {
        Agent self = seedAgent("FACE_ENROLL_ATTACKER");
        Agent victim = seedAgent("FACE_ENROLL_VICTIM");
        String selfToken = tokenProvider.generateToken(self.getId(), "FACE_ENROLL_ATTACKER_CALLER", "AGENT");

        Map<String, Object> body = new HashMap<>();
        body.put("agentId", victim.getId());
        body.put("embedding", "ZmFrZS1lbWJlZGRpbmctZGF0YQ==");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(selfToken);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/face/embedding"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void updateConfigRequiresAdmin() {
        Agent target = seedAgent("FACE_CONFIG_TARGET");
        String agentToken = tokenProvider.generateToken(target.getId(), "FACE_CONFIG_AGENT_CALLER", "AGENT");

        Map<String, Object> body = new HashMap<>();
        body.put("enabled", true);
        body.put("frequency", 2);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(agentToken);

        ResponseEntity<String> forbidden = restTemplate.exchange(
                url("/api/face/config/" + target.getId()), HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        String adminToken = tokenProvider.generateToken(72L, "FACE_CONFIG_ADMIN_CALLER", "ADMIN");
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.setBearerAuth(adminToken);
        ResponseEntity<String> ok = restTemplate.exchange(
                url("/api/face/config/" + target.getId()), HttpMethod.PUT, new HttpEntity<>(body, adminHeaders), String.class);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
    }

    @Test
    void noTokenIsRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/face/status/1"), HttpMethod.GET, withToken(null), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
