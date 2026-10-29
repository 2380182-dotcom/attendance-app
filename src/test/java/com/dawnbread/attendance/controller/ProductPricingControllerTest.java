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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private ResponseEntity<String> call(HttpMethod method, String path, Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void aNewProductStaysHiddenUntilBothPricesAreEnteredAndItIsSwitchedOn() {
        // As V38 adds new products: hidden, prices "Not set" (null).
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("Hidden Official List Product " + System.nanoTime());
        product.setCategory("BISCUIT");
        product.setUnit("pack");
        product.setIsActive(false);
        product.setCreatedAt(LocalDateTime.now());
        product = productRepository.save(product);
        String sales = tokenProvider.generateToken(2L, "PRICE_SALES", "SALES");
        String admin = tokenProvider.generateToken(1L, "PRICE_ADMIN", "ADMIN");
        String salesman = tokenProvider.generateToken(5L, "PRICE_LOCAL", "SALESMAN_LOCAL");
        String name = product.getName();

        // Listed on the Pricing page, prices "Not set", not active.
        String list = call(HttpMethod.GET, "/api/products/pricing", null, admin).getBody();
        int at = list.indexOf(name);
        assertTrue(at > 0, list);
        String row = list.substring(list.lastIndexOf("{", at), list.indexOf("}", at) + 1);
        assertTrue(call(HttpMethod.GET, "/api/products/pricing", null, sales).getBody().contains(name), "Sales sees hidden products too");
        assertTrue(row.contains("\"agentPrice\":null") && row.contains("\"salesmanPrice\":null"), row);
        assertTrue(row.contains("\"active\":false"), row);
        // Never offered to salesmen or agents.
        assertFalse(call(HttpMethod.GET, "/api/sales/products", null, salesman).getBody().contains(name));
        assertFalse(call(HttpMethod.GET, "/api/sales/products-with-images", null, salesman).getBody().contains(name));

        // Switching on is refused while the prices are not set.
        ResponseEntity<String> refused = call(HttpMethod.PUT, "/api/products/" + product.getId() + "/active", Map.of("active", true), admin);
        assertEquals(HttpStatus.BAD_REQUEST, refused.getStatusCode(), refused.getBody());
        assertTrue(refused.getBody().contains("Enter both the agent price and the salesman price"), refused.getBody());

        // Only Admin may switch: Sales sees the status (above) and edits prices, but cannot switch.
        for (String role : List.of("SALES", "AGENT", "SALESMAN_LOCAL", "SALESMAN_LMT", "HR")) {
            String t = tokenProvider.generateToken(9L, "PRICE_" + role, role);
            assertEquals(HttpStatus.FORBIDDEN,
                    call(HttpMethod.PUT, "/api/products/" + product.getId() + "/active", Map.of("active", true), t).getStatusCode(), role);
        }

        // 0 is a real price: entered as 0 and 0, the product can be switched on.
        assertEquals(HttpStatus.OK, call(HttpMethod.PUT, "/api/products/" + product.getId() + "/pricing",
                Map.of("agentPrice", 0.0, "salesmanPrice", 0.0), sales).getStatusCode());
        ResponseEntity<String> on = call(HttpMethod.PUT, "/api/products/" + product.getId() + "/active", Map.of("active", true), admin);
        assertEquals(HttpStatus.OK, on.getStatusCode(), on.getBody());
        assertTrue(call(HttpMethod.GET, "/api/sales/products", null, salesman).getBody().contains(name), "now offered");

        // A saved price cannot be cleared back to "Not set".
        Map<String, Object> clear = new HashMap<>();
        clear.put("agentPrice", null);
        clear.put("salesmanPrice", 10.0);
        assertEquals(HttpStatus.BAD_REQUEST,
                call(HttpMethod.PUT, "/api/products/" + product.getId() + "/pricing", clear, admin).getStatusCode());

        // Switched off again: hidden again.
        assertEquals(HttpStatus.OK,
                call(HttpMethod.PUT, "/api/products/" + product.getId() + "/active", Map.of("active", false), admin).getStatusCode());
        assertFalse(call(HttpMethod.GET, "/api/sales/products", null, salesman).getBody().contains(name));
        assertEquals(HttpStatus.BAD_REQUEST,
                call(HttpMethod.PUT, "/api/products/" + product.getId() + "/active", Map.of("active", "yes"), admin).getStatusCode());
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
