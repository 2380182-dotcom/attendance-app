package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Role-based pricing (P3): ADMIN and SALES may manage product pricing;
 * everyone else — including a bare AGENT and a SALESMAN_LMT — must not.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductPricingControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Long tenantId() {
        return tenantRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setCompanyCode("DAWNBREAD");
                    t.setName("Dawn Bread");
                    t.setIsActive(true);
                    t.setCreatedAt(LocalDateTime.now());
                    t.setCreatedBy("TEST");
                    return tenantRepository.save(t);
                })
                .getId();
    }

    private Product seedProduct() {
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("Pricing Controller Test Bread " + System.nanoTime());
        product.setAgentPrice(50.0);
        product.setSalesmanPrice(60.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private HttpEntity<Map<String, Object>> entityWithToken(Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void adminCanReadAndUpdatePricing() {
        Product product = seedProduct();
        String adminToken = tokenProvider.generateToken(1L, "PRICE_ADMIN", "ADMIN");

        ResponseEntity<String> getResponse = restTemplate.exchange(
                url("/api/products/pricing"), HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders() {{ setBearerAuth(adminToken); }}), String.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());

        Map<String, Object> update = new HashMap<>();
        update.put("agentPrice", 100.0);
        update.put("salesmanPrice", 150.0);
        ResponseEntity<String> putResponse = restTemplate.exchange(
                url("/api/products/" + product.getId() + "/pricing"), HttpMethod.PUT,
                entityWithToken(update, adminToken), String.class);
        assertEquals(HttpStatus.OK, putResponse.getStatusCode());
        assertEquals(true, putResponse.getBody().contains("100.0"));
        assertEquals(true, putResponse.getBody().contains("150.0"));

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(100.0, reloaded.getAgentPrice(), 0.001);
        assertEquals(150.0, reloaded.getSalesmanPrice(), 0.001);
    }

    @Test
    void salesCanManagePricingToo() {
        Product product = seedProduct();
        String salesToken = tokenProvider.generateToken(2L, "PRICE_SALES", "SALES");

        Map<String, Object> update = new HashMap<>();
        update.put("agentPrice", 80.0);
        update.put("salesmanPrice", 90.0);
        ResponseEntity<String> putResponse = restTemplate.exchange(
                url("/api/products/" + product.getId() + "/pricing"), HttpMethod.PUT,
                entityWithToken(update, salesToken), String.class);
        assertEquals(HttpStatus.OK, putResponse.getStatusCode(), "SALES must be able to manage pricing, not just ADMIN");
    }

    @Test
    void bareAgentAndSalesmanCannotManagePricing() {
        Product product = seedProduct();
        String agentToken = tokenProvider.generateToken(3L, "PRICE_AGENT", "AGENT");
        String lmtToken = tokenProvider.generateToken(4L, "PRICE_LMT", "SALESMAN_LMT");

        Map<String, Object> update = new HashMap<>();
        update.put("agentPrice", 1.0);
        update.put("salesmanPrice", 1.0);

        ResponseEntity<String> agentResponse = restTemplate.exchange(
                url("/api/products/" + product.getId() + "/pricing"), HttpMethod.PUT,
                entityWithToken(update, agentToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, agentResponse.getStatusCode());

        ResponseEntity<String> lmtResponse = restTemplate.exchange(
                url("/api/products/" + product.getId() + "/pricing"), HttpMethod.PUT,
                entityWithToken(update, lmtToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, lmtResponse.getStatusCode());

        // Neither attempt may have actually changed the price.
        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(50.0, reloaded.getAgentPrice(), 0.001);
        assertEquals(60.0, reloaded.getSalesmanPrice(), 0.001);
    }
}
