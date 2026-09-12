package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.*;
import com.dawnbread.attendance.repository.*;
import com.dawnbread.attendance.security.TenantContext;
import com.dawnbread.attendance.security.TokenProvider;
import com.dawnbread.attendance.service.SalesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 2 (LMT sales-table extension): the new /sales/shop-visit endpoint's
 * role-gating, shop resolution, buffered geofence hard-gate, mixed
 * SALE/RETURN/UNSOLD handling, the multi-shop-same-day fix itself, and —
 * the test that matters most — proof that the -1 sentinel on
 * sale_items.customer_shop_id in V19's widened index still lets the
 * untouched legacy /entry-with-images path correctly reject a genuine
 * concurrent duplicate, exactly as V17 did before this migration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShopVisitTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private SalesService salesService;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private CustomerShopRepository customerShopRepository;

    @Autowired
    private SaleItemRepository saleItemRepository;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Long tenantId() {
        return TenantTestHelper.defaultTenantId(tenantRepository);
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
        product.setName("Shop Visit Test Bread " + System.nanoTime());
        product.setPrice(50.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private CustomerShop seedShop(String code, Double lat, Double lon, Double radius, boolean geofenceEnabled) {
        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("Shop Visit Test Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode(code);
        shop.setShopName("Shop " + code);
        shop.setArea(area);
        shop.setLatitude(lat);
        shop.setLongitude(lon);
        shop.setRadius(radius);
        shop.setGeoFencingEnabled(geofenceEnabled);
        shop.setCreatedAt(LocalDateTime.now());
        shop.setIsActive(true);
        return customerShopRepository.save(shop);
    }

    private HttpEntity<Map<String, Object>> entityWithToken(Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> shopVisitBody(Long agentId, String shopCode, double lat, double lon, List<Map<String, Object>> items) {
        Map<String, Object> body = new HashMap<>();
        body.put("agentId", agentId);
        body.put("shopCode", shopCode);
        body.put("latitude", lat);
        body.put("longitude", lon);
        body.put("items", items);
        return body;
    }

    private Map<String, Object> item(Long productId, int quantity, String type) {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId);
        item.put("quantity", quantity);
        item.put("transactionType", type);
        return item;
    }

    // ===== Role gating =====

    @Test
    void salesmanCanSubmitForSelfButNotForAnotherAgent() {
        Agent salesman = seedAgent("SV_SALESMAN_1", "SALESMAN_LMT");
        Agent otherAgent = seedAgent("SV_SALESMAN_2", "SALESMAN_LMT");
        Product product = seedProduct();
        CustomerShop shop = seedShop("SV-SHOP-SELF", null, null, null, false); // no geofence configured, gate skipped

        String salesmanToken = tokenProvider.generateToken(salesman.getId(), salesman.getAgentId(), "SALESMAN_LMT");

        Map<String, Object> ownBody = shopVisitBody(salesman.getId(), shop.getShopCode(), 0, 0,
                List.of(item(product.getId(), 5, "SALE")));
        ResponseEntity<String> ownResponse = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(ownBody, salesmanToken), String.class);
        assertEquals(HttpStatus.OK, ownResponse.getStatusCode(), "A SALESMAN_LMT must be able to submit for themselves");

        Map<String, Object> otherBody = shopVisitBody(otherAgent.getId(), shop.getShopCode(), 0, 0,
                List.of(item(product.getId(), 5, "SALE")));
        ResponseEntity<String> otherResponse = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(otherBody, salesmanToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, otherResponse.getStatusCode(), "A SALESMAN_LMT must not submit on another agent's behalf");
    }

    @Test
    void adminCanSubmitForAnyAgentButPlainAgentCannotEvenForSelf() {
        Agent salesman = seedAgent("SV_SALESMAN_3", "SALESMAN_LMT");
        Agent regularAgent = seedAgent("SV_AGENT_1", "AGENT");
        Product product = seedProduct();
        CustomerShop shop = seedShop("SV-SHOP-ADMIN", null, null, null, false);

        String adminToken = tokenProvider.generateToken(999L, "SV_ADMIN", "ADMIN");
        Map<String, Object> adminBody = shopVisitBody(salesman.getId(), shop.getShopCode(), 0, 0,
                List.of(item(product.getId(), 3, "SALE")));
        ResponseEntity<String> adminResponse = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(adminBody, adminToken), String.class);
        assertEquals(HttpStatus.OK, adminResponse.getStatusCode(), "ADMIN must be able to submit on any agent's behalf");

        // Critical: isSelfOrRole's plain "id matches" would have let this
        // through even though the caller's role is AGENT, not SALESMAN_LMT.
        String agentToken = tokenProvider.generateToken(regularAgent.getId(), regularAgent.getAgentId(), "AGENT");
        Map<String, Object> agentBody = shopVisitBody(regularAgent.getId(), shop.getShopCode(), 0, 0,
                List.of(item(product.getId(), 3, "SALE")));
        ResponseEntity<String> agentResponse = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(agentBody, agentToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, agentResponse.getStatusCode(),
                "A plain AGENT submitting for themselves must still be forbidden — only SALESMAN_LMT or ADMIN may use this endpoint");
    }

    // ===== Shop resolution =====

    @Test
    void unknownOrInactiveShopCodeIsRejected() {
        Agent salesman = seedAgent("SV_SALESMAN_4", "SALESMAN_LMT");
        Product product = seedProduct();
        String token = tokenProvider.generateToken(salesman.getId(), salesman.getAgentId(), "SALESMAN_LMT");

        Map<String, Object> body = shopVisitBody(salesman.getId(), "NO-SUCH-SHOP-CODE", 0, 0,
                List.of(item(product.getId(), 1, "SALE")));
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(body, token), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        CustomerShop inactiveShop = seedShop("SV-SHOP-INACTIVE", null, null, null, false);
        inactiveShop.setIsActive(false);
        customerShopRepository.save(inactiveShop);
        Map<String, Object> inactiveBody = shopVisitBody(salesman.getId(), inactiveShop.getShopCode(), 0, 0,
                List.of(item(product.getId(), 1, "SALE")));
        ResponseEntity<String> inactiveResponse = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(inactiveBody, token), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, inactiveResponse.getStatusCode());
    }

    // ===== Buffered geofence hard-gate =====

    @Test
    void withinShopRadiusPlusBufferIsAllowedButBeyondItIsBlocked() {
        Agent salesman = seedAgent("SV_SALESMAN_5", "SALESMAN_LMT");
        Product product = seedProduct();
        // Shop at (0,0), 100m radius. Default buffer is 50m (Stage 1 seed), so up to ~150m should pass.
        CustomerShop shop = seedShop("SV-SHOP-GEOFENCE", 0.0, 0.0, 100.0, true);
        String token = tokenProvider.generateToken(salesman.getId(), salesman.getAgentId(), "SALESMAN_LMT");

        // ~0m away — well within radius+buffer.
        Map<String, Object> closeBody = shopVisitBody(salesman.getId(), shop.getShopCode(), 0.0, 0.0,
                List.of(item(product.getId(), 1, "SALE")));
        ResponseEntity<String> closeResponse = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(closeBody, token), String.class);
        assertEquals(HttpStatus.OK, closeResponse.getStatusCode());
        assertTrue(customerShopRepository.findByShopCode(shop.getShopCode()).isPresent());
        SalesRecord saved = salesRecordRepository.findByAgentIdAndSaleDate(salesman.getId(), LocalDate.now())
                .stream().filter(r -> r.getCustomerShop() != null && r.getCustomerShop().getId().equals(shop.getId()))
                .findFirst().orElseThrow();
        assertNotNull(saved.getDistanceFromShopMeters(), "Distance must always be logged when the shop has a geofence configured");
        assertTrue(saved.getDistanceFromShopMeters() < 150.0);

        // ~1 degree of longitude at the equator is roughly 111km — far beyond 150m allowed.
        Product farProduct = seedProduct();
        Map<String, Object> farBody = shopVisitBody(salesman.getId(), shop.getShopCode(), 0.0, 1.0,
                List.of(item(farProduct.getId(), 1, "SALE")));
        ResponseEntity<String> farResponse = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(farBody, token), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, farResponse.getStatusCode());
        assertTrue(farResponse.getBody().toLowerCase().contains("too far") || farResponse.getBody().contains("m away"),
                "Error should explain the distance, not just reject silently");
    }

    @Test
    void shopWithGeoFencingDisabledSkipsTheHardGateEntirely() {
        Agent salesman = seedAgent("SV_SALESMAN_6", "SALESMAN_LMT");
        Product product = seedProduct();
        // Same coordinates as the "far" case above, but geofencing disabled on this shop.
        CustomerShop shop = seedShop("SV-SHOP-NOGATE", 0.0, 0.0, 100.0, false);
        String token = tokenProvider.generateToken(salesman.getId(), salesman.getAgentId(), "SALESMAN_LMT");

        Map<String, Object> farBody = shopVisitBody(salesman.getId(), shop.getShopCode(), 0.0, 1.0,
                List.of(item(product.getId(), 1, "SALE")));
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(farBody, token), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "geoFencingEnabled=false must skip the hard-gate entirely");
    }

    // ===== Mixed transaction types + the multi-shop-same-day fix =====

    @Test
    void oneVisitCanMixSaleReturnAndUnsoldAndOnlySaleCountsTowardRevenue() {
        Agent salesman = seedAgent("SV_SALESMAN_7", "SALESMAN_LMT");
        Product saleProduct = seedProduct();
        Product returnProduct = seedProduct();
        Product unsoldProduct = seedProduct();
        CustomerShop shop = seedShop("SV-SHOP-MIXED", null, null, null, false);
        String token = tokenProvider.generateToken(salesman.getId(), salesman.getAgentId(), "SALESMAN_LMT");

        Map<String, Object> body = shopVisitBody(salesman.getId(), shop.getShopCode(), 0, 0, List.of(
                item(saleProduct.getId(), 10, "SALE"),
                item(returnProduct.getId(), 2, "RETURN"),
                item(unsoldProduct.getId(), 3, "UNSOLD")
        ));
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(body, token), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        SalesRecord saved = salesRecordRepository.findByAgentIdAndSaleDate(salesman.getId(), LocalDate.now())
                .stream().filter(r -> r.getCustomerShop() != null && r.getCustomerShop().getId().equals(shop.getId()))
                .findFirst().orElseThrow();
        assertEquals(50.0 * 10, saved.getTotalAmount(), 0.001, "totalAmount must reflect only the SALE line, not RETURN or UNSOLD");
        // saved.getItems() is a lazy collection and this repository call ran
        // outside any session by the time we're back here — count via
        // SaleItemRepository instead (getSalesRecord().getId() on a lazy
        // @ManyToOne proxy is safe without a session; only lazy
        // *collections* need one).
        long itemCount = saleItemRepository.findAll().stream()
                .filter(i -> i.getSalesRecord().getId().equals(saved.getId()))
                .count();
        assertEquals(3, itemCount);
    }

    @Test
    void sameAgentAndProductCanBeSoldToTwoDifferentShopsOnTheSameDay() {
        Agent salesman = seedAgent("SV_SALESMAN_8", "SALESMAN_LMT");
        Product product = seedProduct();
        CustomerShop shopA = seedShop("SV-SHOP-A", null, null, null, false);
        CustomerShop shopB = seedShop("SV-SHOP-B", null, null, null, false);
        String token = tokenProvider.generateToken(salesman.getId(), salesman.getAgentId(), "SALESMAN_LMT");

        Map<String, Object> bodyA = shopVisitBody(salesman.getId(), shopA.getShopCode(), 0, 0, List.of(item(product.getId(), 4, "SALE")));
        ResponseEntity<String> responseA = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(bodyA, token), String.class);
        assertEquals(HttpStatus.OK, responseA.getStatusCode(), "First shop's sale must succeed");

        // Under the old V17 (agent, product, date) key, this second call would have been
        // wrongly rejected as a duplicate — this is the bug this whole stage exists to fix.
        Map<String, Object> bodyB = shopVisitBody(salesman.getId(), shopB.getShopCode(), 0, 0, List.of(item(product.getId(), 6, "SALE")));
        ResponseEntity<String> responseB = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(bodyB, token), String.class);
        assertEquals(HttpStatus.OK, responseB.getStatusCode(),
                "Same agent+product+day but a DIFFERENT shop must be allowed — this is the exact bug V19 fixes");
    }

    @Test
    void sameShopSameProductSameTypeSameDayIsStillRejectedAsADuplicate() {
        Agent salesman = seedAgent("SV_SALESMAN_9", "SALESMAN_LMT");
        Product product = seedProduct();
        CustomerShop shop = seedShop("SV-SHOP-DUP", null, null, null, false);
        String token = tokenProvider.generateToken(salesman.getId(), salesman.getAgentId(), "SALESMAN_LMT");

        Map<String, Object> body = shopVisitBody(salesman.getId(), shop.getShopCode(), 0, 0, List.of(item(product.getId(), 4, "SALE")));
        ResponseEntity<String> first = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(body, token), String.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());

        ResponseEntity<String> second = restTemplate.exchange(
                url("/api/sales/shop-visit"), HttpMethod.POST, entityWithToken(body, token), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, second.getStatusCode(),
                "Same agent+product+shop+type+day must still be rejected as a duplicate");
    }

    // ===== The test that matters most: legacy path's V17 guarantee, post-V19 =====

    @Test
    void concurrentLegacySubmissionsForSameAgentProductAndDateStillCollide() throws Exception {
        Agent agent = seedAgent("SV_LEGACY_CONCURRENT", "AGENT");
        Product product = seedProduct();

        com.dawnbread.attendance.dto.SalesRequest request1 = new com.dawnbread.attendance.dto.SalesRequest(
                agent.getId(), "Concurrent Test Store",
                List.of(new com.dawnbread.attendance.dto.SaleItemRequest(product.getId(), 2)));
        com.dawnbread.attendance.dto.SalesRequest request2 = new com.dawnbread.attendance.dto.SalesRequest(
                agent.getId(), "Concurrent Test Store",
                List.of(new com.dawnbread.attendance.dto.SaleItemRequest(product.getId(), 3)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch goLatch = new CountDownLatch(1);

        // TenantContext is a ThreadLocal set by SecurityInterceptor per HTTP
        // request — calling the service directly from raw executor threads
        // (to get genuine concurrency rather than sequential HTTP calls)
        // bypasses that entirely, so each worker thread must set it itself.
        Long testTenantId = tenantId();

        Callable<Boolean> task1 = () -> {
            TenantContext.setTenantId(testTenantId);
            try {
                readyLatch.countDown();
                goLatch.await();
                salesService.addSalesWithImages(request1);
                return true;
            } catch (Exception e) {
                System.out.println("[task1] " + e.getClass().getSimpleName() + ": " + e.getMessage());
                return false;
            } finally {
                TenantContext.clear();
            }
        };
        Callable<Boolean> task2 = () -> {
            TenantContext.setTenantId(testTenantId);
            try {
                readyLatch.countDown();
                goLatch.await();
                salesService.addSalesWithImages(request2);
                return true;
            } catch (Exception e) {
                System.out.println("[task2] " + e.getClass().getSimpleName() + ": " + e.getMessage());
                return false;
            } finally {
                TenantContext.clear();
            }
        };

        Future<Boolean> f1 = executor.submit(task1);
        Future<Boolean> f2 = executor.submit(task2);
        readyLatch.await(5, TimeUnit.SECONDS);
        goLatch.countDown(); // release both threads as close to simultaneously as possible

        boolean result1 = f1.get(15, TimeUnit.SECONDS);
        boolean result2 = f2.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        int successCount = (result1 ? 1 : 0) + (result2 ? 1 : 0);
        assertEquals(1, successCount,
                "Exactly one of two concurrent legacy submissions for the same agent/product/day must succeed — "
                        + "the widened V19 index's -1 sentinel on customer_shop_id must still let the DB constraint "
                        + "catch the race for the untouched legacy flow, exactly as V17 did.");

        // Avoid SalesRecord.items (a lazy collection) outside a session —
        // same reasoning as the fix earlier in this file.
        long totalItemsForProduct = saleItemRepository.findAll().stream()
                .filter(i -> i.getAgentId().equals(agent.getId()) && i.getProduct().getId().equals(product.getId()))
                .count();
        assertEquals(1, totalItemsForProduct, "Only one of the two concurrent submissions' items may have actually persisted");
    }
}
