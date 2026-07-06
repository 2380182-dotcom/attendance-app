package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.SalesRecordRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PUT /api/sales/{id}/override — admin/HR only, and the acting username is
 * now derived from the verified JWT rather than a client-supplied query param.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SalesControllerOverrideSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AgentRepository agentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Agent seedAgent(String agentId) {
        Agent agent = new Agent();
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole("AGENT");
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private SalesRecord seedSalesRecord() {
        Agent agent = seedAgent("OVERRIDE_SUBJECT_" + System.nanoTime());
        SalesRecord record = new SalesRecord();
        record.setAgent(agent);
        record.setStoreName("Test Store");
        record.setTotalAmount(0.0);
        record.setTotalUnits(0);
        record.setSaleDate(LocalDate.now());
        record.setSaleTime(LocalTime.now());
        record.setSubmittedAt(LocalDateTime.now());
        record.setLocation("Test Location");
        record.setCreatedAt(LocalDateTime.now());
        return salesRecordRepository.save(record);
    }

    private Product seedProduct() {
        Product product = new Product();
        product.setName("Override Test Bread");
        product.setPrice(50.0);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private Map<String, Object> overridePayload(Long productId) {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId);
        item.put("quantity", 2);

        Map<String, Object> body = new HashMap<>();
        body.put("agentId", 1);
        body.put("location", "Test Location");
        body.put("items", List.of(item));
        return body;
    }

    @Test
    void overrideWithoutTokenIsRejected() {
        SalesRecord record = seedSalesRecord();
        Product product = seedProduct();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(overridePayload(product.getId()), headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/sales/" + record.getId() + "/override?reason=test"), HttpMethod.PUT, request, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void salesAndAgentRolesCannotOverride() {
        SalesRecord record = seedSalesRecord();
        Product product = seedProduct();

        for (String role : new String[] { "SALES", "AGENT" }) {
            String token = tokenProvider.generateToken(30L, "OVERRIDE_" + role, role);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(overridePayload(product.getId()), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url("/api/sales/" + record.getId() + "/override?reason=test"), HttpMethod.PUT, request, String.class);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), role + " must not be able to override sales records");
        }
    }

    @Test
    void adminAndHrCanOverrideAndActingUsernameComesFromToken() throws Exception {
        for (String role : new String[] { "ADMIN", "HR" }) {
            SalesRecord record = seedSalesRecord();
            Product product = seedProduct();
            String actingUsername = "REAL_" + role + "_OVERRIDER";
            String token = tokenProvider.generateToken(31L, actingUsername, role);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            // Note: no "username" query param sent at all — proves the endpoint
            // no longer needs (or trusts) a client-supplied identity.
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(overridePayload(product.getId()), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url("/api/sales/" + record.getId() + "/override?reason=test"), HttpMethod.PUT, request, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), role + " should be able to override");

            SalesRecord updated = salesRecordRepository.findById(record.getId()).orElseThrow();
            assertEquals(actingUsername, updated.getModifiedBy(),
                    "Acting username must be the verified token's subject, not a client-supplied param");
        }
    }
}
