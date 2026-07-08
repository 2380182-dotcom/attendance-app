package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Mart;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mart CRUD via /api/marts: reads stay open to any authenticated role (agents
 * need the mart list to check in), writes are admin-only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MartControllerSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private MartRepository martRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Mart seedMart(String name) {
        Mart mart = new Mart();
        mart.setTenantId(TenantTestHelper.defaultTenantId(tenantRepository));
        mart.setName(name);
        mart.setAddress("1 Test Street");
        mart.setLatitude(31.5);
        mart.setLongitude(74.3);
        mart.setRadius(100.0);
        mart.setCreatedAt(LocalDateTime.now());
        return martRepository.save(mart);
    }

    private HttpEntity<Map<String, Object>> entityWithToken(Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }

    @Test
    void agentCanReadMartsButNotCreateOne() {
        String agentToken = tokenProvider.generateToken(10L, "AGENT_READ", "AGENT");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(agentToken);
        ResponseEntity<String> readResponse = restTemplate.exchange(
                url("/api/marts"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, readResponse.getStatusCode(), "Reads must stay open to any authenticated role");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Agent Created Mart");
        body.put("latitude", 31.5);
        body.put("longitude", 74.3);
        body.put("radius", 100.0);

        ResponseEntity<String> createResponse = restTemplate.exchange(
                url("/api/marts"), HttpMethod.POST, entityWithToken(body, agentToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, createResponse.getStatusCode());
    }

    @Test
    void createUpdateDeleteReactivateRequireAdmin() {
        String adminToken = tokenProvider.generateToken(11L, "MART_ADMIN", "ADMIN");
        String nonAdminToken = tokenProvider.generateToken(12L, "MART_SALES", "SALES");

        // Non-admin cannot create.
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("name", "Sales Attempted Mart");
        createBody.put("latitude", 31.5);
        createBody.put("longitude", 74.3);
        createBody.put("radius", 100.0);
        ResponseEntity<String> forbiddenCreate = restTemplate.exchange(
                url("/api/marts"), HttpMethod.POST, entityWithToken(createBody, nonAdminToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenCreate.getStatusCode());

        // Admin can create.
        ResponseEntity<String> createResponse = restTemplate.exchange(
                url("/api/marts"), HttpMethod.POST, entityWithToken(createBody, adminToken), String.class);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());

        Mart target = seedMart("Update Target Mart");

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("name", "Renamed By Sales");
        ResponseEntity<String> forbiddenUpdate = restTemplate.exchange(
                url("/api/marts/" + target.getId()), HttpMethod.PUT, entityWithToken(updateBody, nonAdminToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenUpdate.getStatusCode());

        Map<String, Object> renameBody = new HashMap<>();
        renameBody.put("name", "Renamed By Admin");
        ResponseEntity<String> okUpdate = restTemplate.exchange(
                url("/api/marts/" + target.getId()), HttpMethod.PUT, entityWithToken(renameBody, adminToken), String.class);
        assertEquals(HttpStatus.OK, okUpdate.getStatusCode());
        assertEquals("Renamed By Admin", martRepository.findById(target.getId()).orElseThrow().getName());

        // Non-admin cannot delete.
        HttpHeaders nonAdminHeaders = new HttpHeaders();
        nonAdminHeaders.setBearerAuth(nonAdminToken);
        ResponseEntity<String> forbiddenDelete = restTemplate.exchange(
                url("/api/marts/" + target.getId()), HttpMethod.DELETE, new HttpEntity<>(nonAdminHeaders), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenDelete.getStatusCode());

        // Admin can delete (soft delete).
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        ResponseEntity<String> okDelete = restTemplate.exchange(
                url("/api/marts/" + target.getId()), HttpMethod.DELETE, new HttpEntity<>(adminHeaders), String.class);
        assertEquals(HttpStatus.OK, okDelete.getStatusCode());

        // Non-admin cannot reactivate.
        ResponseEntity<String> forbiddenReactivate = restTemplate.exchange(
                url("/api/marts/" + target.getId() + "/reactivate"), HttpMethod.PATCH,
                new HttpEntity<>(nonAdminHeaders), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenReactivate.getStatusCode());

        // Admin can reactivate.
        ResponseEntity<String> okReactivate = restTemplate.exchange(
                url("/api/marts/" + target.getId() + "/reactivate"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), String.class);
        assertEquals(HttpStatus.OK, okReactivate.getStatusCode());
        assertTrue(martRepository.findById(target.getId()).orElseThrow().getIsActive());
    }

    @Test
    void noTokenIsRejectedOnWriteEndpoints() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "No Auth Mart");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/marts"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
