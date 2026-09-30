package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QR shop-visit flow (Q1) over real HTTP: role gating on
 * POST /api/lmt/shop-visits/scan, and the "a rejected scan is still a 200
 * with the reason in the body, but a precondition failure is a real 400"
 * distinction.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShopVisitScanControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AgentRepository agentRepository;

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

    private CustomerShop seedGeoShop() {
        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("Scan Controller Test Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);
        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode("SCT-" + System.nanoTime());
        shop.setShopName("Scan Controller Test Shop");
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

    private Map<String, Object> scanBody(Agent who, String code, double lat, double lon) {
        Map<String, Object> body = new HashMap<>();
        body.put("agentId", who.getId());
        body.put("scannedCode", code);
        body.put("latitude", lat);
        body.put("longitude", lon);
        return body;
    }

    private String tokenFor(Agent a) {
        return tokenProvider.generateToken(a.getId(), a.getAgentId(), a.getRole(), a.getTenantId());
    }

    @Test
    void localSalesmanCanScanForThemselves() {
        Agent local = seedAgent("SCAN_HTTP_LOCAL_1", "SALESMAN_LOCAL");
        CustomerShop shop = seedGeoShop();
        ResponseEntity<String> response = restTemplate.exchange(url("/api/lmt/shop-visits/scan"), HttpMethod.POST,
                withToken(scanBody(local, shop.getShopCode(), 24.86, 67.0), tokenFor(local)), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        assertTrue(response.getBody().contains("\"visitStatus\":\"SUCCESS\""), response.getBody());
    }

    @Test
    void aPlainAgentCannotScan() {
        Agent agent = seedAgent("SCAN_HTTP_AGENT", "AGENT");
        CustomerShop shop = seedGeoShop();
        ResponseEntity<String> response = restTemplate.exchange(url("/api/lmt/shop-visits/scan"), HttpMethod.POST,
                withToken(scanBody(agent, shop.getShopCode(), 24.86, 67.0), tokenFor(agent)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void aSalesmanCannotScanOnBehalfOfSomeoneElse() {
        Agent local = seedAgent("SCAN_HTTP_LOCAL_2", "SALESMAN_LOCAL");
        Agent other = seedAgent("SCAN_HTTP_LOCAL_3", "SALESMAN_LOCAL");
        CustomerShop shop = seedGeoShop();
        ResponseEntity<String> response = restTemplate.exchange(url("/api/lmt/shop-visits/scan"), HttpMethod.POST,
                withToken(scanBody(other, shop.getShopCode(), 24.86, 67.0), tokenFor(local)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void anInvalidCodeIsA200WithTheRejectionReasonInTheBody() {
        Agent local = seedAgent("SCAN_HTTP_INVALID", "SALESMAN_LOCAL");
        ResponseEntity<String> response = restTemplate.exchange(url("/api/lmt/shop-visits/scan"), HttpMethod.POST,
                withToken(scanBody(local, "NO-SUCH-CODE", 24.86, 67.0), tokenFor(local)), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        assertTrue(response.getBody().contains("\"visitStatus\":\"INVALID_CODE\""), response.getBody());
    }

    @Test
    void lmtWithoutCheckInGetsARealErrorNotARecordedRejection() {
        Agent lmt = seedAgent("SCAN_HTTP_LMT_NO_CHECKIN", "SALESMAN_LMT"); // deliberately no check-in
        CustomerShop shop = seedGeoShop();
        ResponseEntity<String> response = restTemplate.exchange(url("/api/lmt/shop-visits/scan"), HttpMethod.POST,
                withToken(scanBody(lmt, shop.getShopCode(), 24.86, 67.0), tokenFor(lmt)), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("check in"), response.getBody());
    }

    @Test
    void adminCanScanOnBehalfOfAnySalesman() {
        Agent admin = seedAgent("SCAN_HTTP_ADMIN", "ADMIN");
        Agent local = seedAgent("SCAN_HTTP_LOCAL_4", "SALESMAN_LOCAL");
        CustomerShop shop = seedGeoShop();
        ResponseEntity<String> response = restTemplate.exchange(url("/api/lmt/shop-visits/scan"), HttpMethod.POST,
                withToken(scanBody(local, shop.getShopCode(), 24.86, 67.0), tokenFor(admin)), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
    }

    // ===== Q2: the admin report and per-salesman summary =====

    @Test
    void aSalesmanCannotViewTheAdminReport() {
        Agent local = seedAgent("SCAN_HTTP_REPORT_LOCAL", "SALESMAN_LOCAL");
        ResponseEntity<String> response = restTemplate.exchange(url("/api/lmt/shop-visits"), HttpMethod.GET,
                withToken(null, tokenFor(local)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void adminCanViewTheReport() {
        Agent admin = seedAgent("SCAN_HTTP_REPORT_ADMIN", "ADMIN");
        Agent local = seedAgent("SCAN_HTTP_REPORT_LOCAL_2", "SALESMAN_LOCAL");
        CustomerShop shop = seedGeoShop();
        restTemplate.exchange(url("/api/lmt/shop-visits/scan"), HttpMethod.POST,
                withToken(scanBody(local, shop.getShopCode(), 24.86, 67.0), tokenFor(local)), String.class);

        ResponseEntity<String> response = restTemplate.exchange(url("/api/lmt/shop-visits"), HttpMethod.GET,
                withToken(null, tokenFor(admin)), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        assertTrue(response.getBody().contains(shop.getShopCode()), response.getBody());
    }

    /**
     * Task 3: "QR Scanned Shops" moved to its own Admin-sidebar item and
     * became admin-only per the user's explicit decision — HR/SALES could
     * view this report before that change, now they can't.
     */
    @Test
    void hrAndSalesCanNoLongerViewTheReport() {
        Agent hr = seedAgent("SCAN_HTTP_REPORT_HR", "HR");
        Agent sales = seedAgent("SCAN_HTTP_REPORT_SALES", "SALES");
        assertEquals(HttpStatus.FORBIDDEN, restTemplate.exchange(url("/api/lmt/shop-visits"), HttpMethod.GET,
                withToken(null, tokenFor(hr)), String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, restTemplate.exchange(url("/api/lmt/shop-visits"), HttpMethod.GET,
                withToken(null, tokenFor(sales)), String.class).getStatusCode());
    }

    @Test
    void aSalesmanCannotViewTheDaySummary() {
        Agent local = seedAgent("SCAN_HTTP_SUMMARY_LOCAL", "SALESMAN_LOCAL");
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/lmt/shop-visits/summary?agentId=" + local.getId()), HttpMethod.GET,
                withToken(null, tokenFor(local)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void adminCanViewTheDaySummary() {
        Agent admin = seedAgent("SCAN_HTTP_SUMMARY_ADMIN", "ADMIN");
        Agent local = seedAgent("SCAN_HTTP_SUMMARY_LOCAL_2", "SALESMAN_LOCAL");
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/lmt/shop-visits/summary?agentId=" + local.getId()), HttpMethod.GET,
                withToken(null, tokenFor(admin)), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        assertTrue(response.getBody().contains("\"totalVisits\":0"), response.getBody());
    }
}
