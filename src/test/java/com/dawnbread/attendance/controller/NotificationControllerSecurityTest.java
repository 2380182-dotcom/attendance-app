package com.dawnbread.attendance.controller;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
