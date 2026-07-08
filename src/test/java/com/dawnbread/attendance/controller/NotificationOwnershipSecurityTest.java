package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Notification;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.NotificationRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves PATCH /{id}/read and DELETE /{id} enforce ownership: an agent may
 * only manage their own notifications, except HR/Admin/Sales, whose real
 * workflow (confirmed via HRReportScreen.js) is managing OTHER agents'
 * notifications from a shared department queue.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationOwnershipSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Agent seedAgent(String agentId, String role) {
        Agent agent = new Agent();
        agent.setTenantId(TenantTestHelper.defaultTenantId(tenantRepository));
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole(role);
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private Notification seedNotification(Agent owner) {
        Notification notif = new Notification();
        notif.setTenantId(TenantTestHelper.defaultTenantId(tenantRepository));
        notif.setAgent(owner);
        notif.setAgentName(owner.getName());
        notif.setMessage("Test notification for " + owner.getAgentId());
        notif.setType("CHECK_IN");
        notif.setDepartment("HR");
        notif.setIsRead(false);
        notif.setCreatedAt(LocalDateTime.now());
        return notificationRepository.save(notif);
    }

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    @Test
    void agentCanMarkReadAndDeleteTheirOwnNotification() {
        Agent owner = seedAgent("NOTIF_OWNER_1", "AGENT");
        Notification notif = seedNotification(owner);
        String ownerToken = tokenProvider.generateToken(owner.getId(), owner.getAgentId(), "AGENT",
                TenantTestHelper.defaultTenantId(tenantRepository));

        ResponseEntity<String> readResponse = restTemplate.exchange(
                url("/api/notifications/" + notif.getId() + "/read"), HttpMethod.PATCH, withToken(ownerToken), String.class);
        assertEquals(HttpStatus.OK, readResponse.getStatusCode(), readResponse.getBody());
        assertTrue(notificationRepository.findById(notif.getId()).orElseThrow().getIsRead());

        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                url("/api/notifications/" + notif.getId()), HttpMethod.DELETE, withToken(ownerToken), String.class);
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode(), deleteResponse.getBody());
        assertTrue(notificationRepository.findById(notif.getId()).isEmpty());
    }

    @Test
    void agentCannotMarkReadOrDeleteAnotherAgentsNotification() {
        Agent owner = seedAgent("NOTIF_OWNER_2", "AGENT");
        Agent stranger = seedAgent("NOTIF_STRANGER_2", "AGENT");
        Notification notif = seedNotification(owner);
        String strangerToken = tokenProvider.generateToken(stranger.getId(), stranger.getAgentId(), "AGENT",
                TenantTestHelper.defaultTenantId(tenantRepository));

        ResponseEntity<String> readResponse = restTemplate.exchange(
                url("/api/notifications/" + notif.getId() + "/read"), HttpMethod.PATCH, withToken(strangerToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, readResponse.getStatusCode(),
                "An agent must not be able to mark another agent's notification as read: " + readResponse.getBody());

        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                url("/api/notifications/" + notif.getId()), HttpMethod.DELETE, withToken(strangerToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, deleteResponse.getStatusCode(),
                "An agent must not be able to delete another agent's notification: " + deleteResponse.getBody());

        assertFalse(notificationRepository.findById(notif.getId()).orElseThrow().getIsRead(),
                "The notification must be untouched after the rejected attempt");
    }

    @Test
    void managementRolesCanManageAnyAgentsNotification() {
        Agent owner = seedAgent("NOTIF_OWNER_3", "AGENT");
        Agent hrStaff = seedAgent("NOTIF_HR_3", "HR");
        Notification notif = seedNotification(owner);
        String hrToken = tokenProvider.generateToken(hrStaff.getId(), hrStaff.getAgentId(), "HR",
                TenantTestHelper.defaultTenantId(tenantRepository));

        // Real HRReportScreen workflow: HR marks-as-read a notification about
        // a different agent from the shared HR queue.
        ResponseEntity<String> readResponse = restTemplate.exchange(
                url("/api/notifications/" + notif.getId() + "/read"), HttpMethod.PATCH, withToken(hrToken), String.class);
        assertEquals(HttpStatus.OK, readResponse.getStatusCode(), readResponse.getBody());
        assertTrue(notificationRepository.findById(notif.getId()).orElseThrow().getIsRead());

        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                url("/api/notifications/" + notif.getId()), HttpMethod.DELETE, withToken(hrToken), String.class);
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode(), deleteResponse.getBody());
        assertTrue(notificationRepository.findById(notif.getId()).isEmpty());
    }

    @Test
    void nonExistentNotificationReturns404NotForbidden() {
        Agent agent = seedAgent("NOTIF_AGENT_4", "AGENT");
        String token = tokenProvider.generateToken(agent.getId(), agent.getAgentId(), "AGENT",
                TenantTestHelper.defaultTenantId(tenantRepository));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/notifications/999999999/read"), HttpMethod.PATCH, withToken(token), String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), response.getBody());
    }
}
