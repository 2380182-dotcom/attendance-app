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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Proves the impersonation guard on the 5 self-only attendance endpoints:
 * checkin, checkout, face-result, verify-midday/{agentId}, verify-scheduled.
 * A caller's JWT id must match the agentId in the request — a management
 * role gets no exception here, since no real mobile flow calls these
 * on an agent's behalf (confirmed against FaceVerificationModal.js /
 * AdminUsersScreen.js during the earlier analysis pass).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttendanceImpersonationSecurityTest {

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

    private Agent seedAgent(String agentId, String role) {
        Agent agent = new Agent();
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole(role);
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private <T> ResponseEntity<String> postAs(String token, String path, Map<String, T> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void checkInRejectsAgentIdMismatchButAcceptsSelf() {
        Agent self = seedAgent("IMPS_CI_SELF", "AGENT");
        Agent other = seedAgent("IMPS_CI_OTHER", "AGENT");
        String selfToken = tokenProvider.generateToken(self.getId(), self.getAgentId(), "AGENT");

        Map<String, Object> mismatchedBody = new HashMap<>();
        mismatchedBody.put("agentId", other.getId());
        mismatchedBody.put("martId", 1L);
        mismatchedBody.put("latitude", 31.5);
        mismatchedBody.put("longitude", 74.3);

        ResponseEntity<String> mismatch = postAs(selfToken, "/api/attendance/checkin", mismatchedBody);
        assertEquals(HttpStatus.FORBIDDEN, mismatch.getStatusCode(), "Checking in as another agent must be rejected: " + mismatch.getBody());

        Map<String, Object> selfBody = new HashMap<>();
        selfBody.put("agentId", self.getId());
        selfBody.put("martId", 1L);
        selfBody.put("latitude", 31.5);
        selfBody.put("longitude", 74.3);

        ResponseEntity<String> ownRequest = postAs(selfToken, "/api/attendance/checkin", selfBody);
        assertNotEquals(HttpStatus.FORBIDDEN, ownRequest.getStatusCode(),
                "Checking in as yourself must pass the impersonation guard: " + ownRequest.getBody());
    }

    @Test
    void checkOutRejectsAgentIdMismatchButAcceptsSelf() {
        Agent self = seedAgent("IMPS_CO_SELF", "AGENT");
        Agent other = seedAgent("IMPS_CO_OTHER", "AGENT");
        String selfToken = tokenProvider.generateToken(self.getId(), self.getAgentId(), "AGENT");

        Map<String, Object> mismatchedBody = new HashMap<>();
        mismatchedBody.put("agentId", other.getId());
        mismatchedBody.put("latitude", 31.5);
        mismatchedBody.put("longitude", 74.3);
        mismatchedBody.put("faceVerified", true);

        ResponseEntity<String> mismatch = postAs(selfToken, "/api/attendance/checkout", mismatchedBody);
        assertEquals(HttpStatus.FORBIDDEN, mismatch.getStatusCode(), "Checking out another agent must be rejected: " + mismatch.getBody());

        Map<String, Object> selfBody = new HashMap<>();
        selfBody.put("agentId", self.getId());
        selfBody.put("latitude", 31.5);
        selfBody.put("longitude", 74.3);
        selfBody.put("faceVerified", true);

        ResponseEntity<String> ownRequest = postAs(selfToken, "/api/attendance/checkout", selfBody);
        assertNotEquals(HttpStatus.FORBIDDEN, ownRequest.getStatusCode(),
                "Checking out yourself must pass the impersonation guard: " + ownRequest.getBody());
    }

    @Test
    void faceResultRejectsAgentIdMismatchButAcceptsSelf() {
        Agent self = seedAgent("IMPS_FR_SELF", "AGENT");
        Agent other = seedAgent("IMPS_FR_OTHER", "AGENT");
        String selfToken = tokenProvider.generateToken(self.getId(), self.getAgentId(), "AGENT");

        Map<String, Object> mismatchedBody = new HashMap<>();
        mismatchedBody.put("agentId", other.getId());
        mismatchedBody.put("verificationResult", "PASS");
        mismatchedBody.put("confidenceScore", 0.95);
        mismatchedBody.put("checkpointType", "CHECKIN");

        ResponseEntity<String> mismatch = postAs(selfToken, "/api/attendance/face-result", mismatchedBody);
        assertEquals(HttpStatus.FORBIDDEN, mismatch.getStatusCode(), "Reporting face results for another agent must be rejected: " + mismatch.getBody());

        Map<String, Object> selfBody = new HashMap<>();
        selfBody.put("agentId", self.getId());
        selfBody.put("verificationResult", "PASS");
        selfBody.put("confidenceScore", 0.95);
        selfBody.put("checkpointType", "CHECKIN");

        ResponseEntity<String> ownRequest = postAs(selfToken, "/api/attendance/face-result", selfBody);
        assertNotEquals(HttpStatus.FORBIDDEN, ownRequest.getStatusCode(),
                "Reporting your own face result must pass the impersonation guard: " + ownRequest.getBody());
    }

    @Test
    void verifyMidDayRejectsAgentIdMismatchButAcceptsSelf() {
        Agent self = seedAgent("IMPS_MD_SELF", "AGENT");
        Agent other = seedAgent("IMPS_MD_OTHER", "AGENT");
        String selfToken = tokenProvider.generateToken(self.getId(), self.getAgentId(), "AGENT");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(selfToken);

        ResponseEntity<String> mismatch = restTemplate.exchange(
                url("/api/attendance/verify-midday/" + other.getId()), HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.FORBIDDEN, mismatch.getStatusCode(), "Mid-day verify for another agent must be rejected: " + mismatch.getBody());

        ResponseEntity<String> ownRequest = restTemplate.exchange(
                url("/api/attendance/verify-midday/" + self.getId()), HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertNotEquals(HttpStatus.FORBIDDEN, ownRequest.getStatusCode(),
                "Mid-day verify for yourself must pass the impersonation guard: " + ownRequest.getBody());
    }

    @Test
    void verifyScheduledRejectsAgentIdMismatchButAcceptsSelf() {
        Agent self = seedAgent("IMPS_VS_SELF", "AGENT");
        Agent other = seedAgent("IMPS_VS_OTHER", "AGENT");
        String selfToken = tokenProvider.generateToken(self.getId(), self.getAgentId(), "AGENT");

        Map<String, Object> mismatchedBody = new HashMap<>();
        mismatchedBody.put("agentId", other.getId());
        mismatchedBody.put("verificationResult", "PASS");
        mismatchedBody.put("confidenceScore", 0.95);
        mismatchedBody.put("checkpointType", "MIDSHIFT");

        ResponseEntity<String> mismatch = postAs(selfToken, "/api/attendance/verify-scheduled", mismatchedBody);
        assertEquals(HttpStatus.FORBIDDEN, mismatch.getStatusCode(), "Scheduled verify for another agent must be rejected: " + mismatch.getBody());

        Map<String, Object> selfBody = new HashMap<>();
        selfBody.put("agentId", self.getId());
        selfBody.put("verificationResult", "PASS");
        selfBody.put("confidenceScore", 0.95);
        selfBody.put("checkpointType", "MIDSHIFT");

        ResponseEntity<String> ownRequest = postAs(selfToken, "/api/attendance/verify-scheduled", selfBody);
        assertNotEquals(HttpStatus.FORBIDDEN, ownRequest.getStatusCode(),
                "Scheduled verify for yourself must pass the impersonation guard: " + ownRequest.getBody());
    }

    @Test
    void managementRoleGetsNoExceptionOnSelfOnlyEndpoints() {
        // Confirmed against the mobile app: no Admin/HR/Sales flow ever calls
        // checkin/checkout/face-result/verify-midday/verify-scheduled on an
        // agent's behalf, so these stay strictly self-only with no elevated-role
        // bypass, unlike the read endpoints and saveEmbedding().
        Agent admin = seedAgent("IMPS_ADMIN", "ADMIN");
        Agent agent = seedAgent("IMPS_TARGET_AGENT", "AGENT");
        String adminToken = tokenProvider.generateToken(admin.getId(), admin.getAgentId(), "ADMIN");

        Map<String, Object> body = new HashMap<>();
        body.put("agentId", agent.getId());
        body.put("martId", 1L);
        body.put("latitude", 31.5);
        body.put("longitude", 74.3);

        ResponseEntity<String> response = postAs(adminToken, "/api/attendance/checkin", body);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Admin acting on an agent's behalf must still be rejected — self-only has no management exception: " + response.getBody());
    }
}
