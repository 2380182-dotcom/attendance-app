package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
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

/**
 * SALESMAN_LOCAL (L1) over real HTTP: a local salesman may record a shop
 * visit for themselves (no check-in), an AGENT may not, a local salesman may
 * not record for someone else, and none of the LMT stock/reconciliation
 * endpoints are open to the local role.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LocalSalesmanControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private CustomerShopRepository customerShopRepository;

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

    private Agent seedAgent(String agentId, String role) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole(role);
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private Product seedProduct() {
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("Local Controller Test Bread " + System.nanoTime());
        product.setAgentPrice(50.0);
        product.setSalesmanPrice(60.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private CustomerShop seedGeoShop() {
        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("Local Controller Test Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);
        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode("LCT-" + System.nanoTime());
        shop.setShopName("Local Controller Test Shop");
        shop.setArea(area);
        shop.setGeoFencingEnabled(true);
        shop.setLatitude(24.86);
        shop.setLongitude(67.0);
        shop.setRadius(50.0);
        shop.setIsActive(true);
        shop.setCreatedAt(LocalDateTime.now());
        return customerShopRepository.save(shop);
    }

    private HttpEntity<Map<String, Object>> withToken(Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> visitBody(Agent who, CustomerShop shop, Product product) {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", product.getId());
        item.put("quantity", 2);
        item.put("transactionType", "SALE");
        Map<String, Object> body = new HashMap<>();
        body.put("agentId", who.getId());
        body.put("shopCode", shop.getShopCode());
        body.put("latitude", 24.86);
        body.put("longitude", 67.0);
        body.put("items", List.of(item));
        return body;
    }

    private String tokenFor(Agent a) {
        return tokenProvider.generateToken(a.getId(), a.getAgentId(), a.getRole(), a.getTenantId());
    }

    @Test
    void localSalesmanCanRecordAVisitForThemselves() {
        Agent local = seedAgent("HTTP_LOCAL_1", "SALESMAN_LOCAL");
        ResponseEntity<String> response = restTemplate.exchange(url("/api/sales/shop-visit"), HttpMethod.POST,
                withToken(visitBody(local, seedGeoShop(), seedProduct()), tokenFor(local)), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
    }

    @Test
    void aPlainAgentStillCannotRecordAShopVisit() {
        Agent agent = seedAgent("HTTP_AGENT_1", "AGENT");
        ResponseEntity<String> response = restTemplate.exchange(url("/api/sales/shop-visit"), HttpMethod.POST,
                withToken(visitBody(agent, seedGeoShop(), seedProduct()), tokenFor(agent)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void aLocalSalesmanCannotRecordAVisitForSomeoneElse() {
        Agent local = seedAgent("HTTP_LOCAL_2", "SALESMAN_LOCAL");
        Agent other = seedAgent("HTTP_LOCAL_3", "SALESMAN_LOCAL");
        ResponseEntity<String> response = restTemplate.exchange(url("/api/sales/shop-visit"), HttpMethod.POST,
                withToken(visitBody(other, seedGeoShop(), seedProduct()), tokenFor(local)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void lmtStockAndReconciliationEndpointsAreClosedToTheLocalRole() {
        Agent local = seedAgent("HTTP_LOCAL_4", "SALESMAN_LOCAL");
        String token = tokenFor(local);

        Map<String, Object> morning = new HashMap<>();
        morning.put("agentId", local.getId());
        Map<String, Object> stockItem = new HashMap<>();
        stockItem.put("productId", seedProduct().getId());
        stockItem.put("openingStock", 10);
        morning.put("items", List.of(stockItem)); // a VALID body, so it is the role check being tested, not validation
        assertEquals(HttpStatus.FORBIDDEN, restTemplate.exchange(url("/api/lmt/stock/morning"), HttpMethod.POST,
                withToken(morning, token), String.class).getStatusCode(), "no stock entry for local");

        assertEquals(HttpStatus.FORBIDDEN, restTemplate.exchange(url("/api/lmt/stock/today?agentId=" + local.getId()),
                HttpMethod.GET, withToken(null, token), String.class).getStatusCode());

        assertEquals(HttpStatus.FORBIDDEN, restTemplate.exchange(url("/api/lmt/stock/reconciliation"),
                HttpMethod.GET, withToken(null, token), String.class).getStatusCode(), "no reconciliation for local");
    }
}
