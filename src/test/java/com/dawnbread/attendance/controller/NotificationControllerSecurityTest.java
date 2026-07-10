package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Notification;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.NotificationRepository;
import com.dawnbread.attendance.repository.TenantRepository;
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
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /api/notifications/** — department feeds (sales/hr) split by role, and an
 * agent may only read their own notifications (agentId path variable checked
 * against the verified token's id claim), not any other agent's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationControllerSecurityTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(headers);
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

    private void seedNotifications(Agent owner, int count) {
        for (int i = 0; i < count; i++) {
            Notification notif = new Notification();
            notif.setTenantId(TenantTestHelper.defaultTenantId(tenantRepository));
            notif.setAgent(owner);
            notif.setAgentName(owner.getName());
            notif.setMessage("Paged notification " + i);
            notif.setType("CHECK_IN");
            notif.setDepartment("HR");
            notif.setIsRead(false);
            notif.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(notif);
        }
    }

    @Test
    void hrCannotReadSalesNotificationsAndViceVersa() {
        String hrToken = tokenProvider.generateToken(60L, "NOTIF_HR", "HR");
        ResponseEntity<String> hrOnSales = restTemplate.exchange(
                url("/api/notifications/sales"), HttpMethod.GET, withToken(hrToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, hrOnSales.getStatusCode());

        String salesToken = tokenProvider.generateToken(61L, "NOTIF_SALES", "SALES");
        ResponseEntity<String> salesOnHr = restTemplate.exchange(
                url("/api/notifications/hr"), HttpMethod.GET, withToken(salesToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, salesOnHr.getStatusCode());
    }

    @Test
    void salesAndHrCanReadTheirOwnDepartmentFeed() {
        String salesToken = tokenProvider.generateToken(62L, "NOTIF_SALES_OK", "SALES");
        ResponseEntity<String> salesOk = restTemplate.exchange(
                url("/api/notifications/sales"), HttpMethod.GET, withToken(salesToken), String.class);
        assertEquals(HttpStatus.OK, salesOk.getStatusCode());

        String hrToken = tokenProvider.generateToken(63L, "NOTIF_HR_OK", "HR");
        ResponseEntity<String> hrOk = restTemplate.exchange(
                url("/api/notifications/hr"), HttpMethod.GET, withToken(hrToken), String.class);
        assertEquals(HttpStatus.OK, hrOk.getStatusCode());
    }

    @Test
    void agentCanReadOwnNotificationsButNotAnotherAgents() {
        String selfToken = tokenProvider.generateToken(64L, "NOTIF_SELF", "AGENT");

        ResponseEntity<String> ownNotifs = restTemplate.exchange(
                url("/api/notifications/agent/64"), HttpMethod.GET, withToken(selfToken), String.class);
        assertEquals(HttpStatus.OK, ownNotifs.getStatusCode());

        ResponseEntity<String> othersNotifs = restTemplate.exchange(
                url("/api/notifications/agent/65"), HttpMethod.GET, withToken(selfToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, othersNotifs.getStatusCode(),
                "An agent must not be able to read another agent's notifications");
    }

    @Test
    void managementRolesCanReadAnyAgentsNotifications() {
        String adminToken = tokenProvider.generateToken(66L, "NOTIF_ADMIN", "ADMIN");
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/notifications/agent/999"), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void noTokenIsRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/notifications/agent/1"), HttpMethod.GET, withToken(null), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    /**
     * Proves the endpoint returns a genuine page, not the full unbounded
     * history (audit Hardening d): with 25 seeded notifications and size=10,
     * only 10 come back, hasNext is true, and totalElements reflects the
     * real total.
     */
    @Test
    void agentNotificationsAreActuallyPaginated() throws Exception {
        Agent owner = seedAgent("NOTIF_PAGE_OWNER");
        seedNotifications(owner, 25);
        String ownerToken = tokenProvider.generateToken(owner.getId(), owner.getAgentId(), "AGENT",
                TenantTestHelper.defaultTenantId(tenantRepository));

        ResponseEntity<String> firstPage = restTemplate.exchange(
                url("/api/notifications/agent/" + owner.getId() + "?page=0&size=10"),
                HttpMethod.GET, withToken(ownerToken), String.class);
        assertEquals(HttpStatus.OK, firstPage.getStatusCode(), firstPage.getBody());

        JsonNode data = objectMapper.readTree(firstPage.getBody()).get("data");
        assertEquals(10, data.get("content").size(), "First page must contain exactly `size` items: " + firstPage.getBody());
        assertEquals(25, data.get("totalElements").asLong());
        assertTrue(data.get("hasNext").asBoolean(), "25 items over a page size of 10 must have a next page");
        assertEquals(0, data.get("page").asInt());

        ResponseEntity<String> lastPage = restTemplate.exchange(
                url("/api/notifications/agent/" + owner.getId() + "?page=2&size=10"),
                HttpMethod.GET, withToken(ownerToken), String.class);
        JsonNode lastData = objectMapper.readTree(lastPage.getBody()).get("data");
        assertEquals(5, lastData.get("content").size(), "Third page must hold the remaining 5 items");
        assertTrue(!lastData.get("hasNext").asBoolean(), "The last page must not report a further page");
    }

    /**
     * A caller passing an oversized `size` must not get an unbounded page
     * back — that would defeat the whole point of pagination.
     */
    @Test
    void pageSizeIsCappedRegardlessOfWhatTheCallerRequests() {
        Agent owner = seedAgent("NOTIF_PAGE_CAP");
        seedNotifications(owner, 5);
        String ownerToken = tokenProvider.generateToken(owner.getId(), owner.getAgentId(), "AGENT",
                TenantTestHelper.defaultTenantId(tenantRepository));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/notifications/agent/" + owner.getId() + "?page=0&size=99999"),
                HttpMethod.GET, withToken(ownerToken), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        assertTrue(response.getBody().contains("\"size\":100"),
                "An oversized page size request must be capped at the server-enforced maximum: " + response.getBody());
    }
}
