package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ShopVisitItemRequest;
import com.dawnbread.attendance.dto.ShopVisitRequest;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 4 — Local/LMT Sales Voucher sections: DB-computed shop/voucher
 * totals (never Java-side summing), the role-snapshot that keeps an
 * existing voucher in its original section even if the agent's role
 * changes later, and the tenant-safe 404 on the detail/PDF endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SalesVoucherTest {

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
    private TenantRepository tenantRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private MartRepository martRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Long tenantId() {
        return TenantTestHelper.defaultTenantId(tenantRepository);
    }

    private Agent seedAgent(Long tenantId, String agentId, String role) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId);
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "-" + tenantId + "@example.com");
        agent.setRole(role);
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private Product seedProduct(Long tenantId) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setName("Voucher Test Bread " + System.nanoTime());
        product.setAgentPrice(50.0);
        product.setSalesmanPrice(50.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private CustomerShop seedShop(Long tenantId, String code) {
        Area area = new Area();
        area.setTenantId(tenantId);
        area.setName("Voucher Test Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId);
        shop.setShopCode(code);
        shop.setShopName("Shop " + code);
        shop.setArea(area);
        // Geofence configured at (0,0) with a generous radius so both LMT
        // (gate optional) and Local (gate mandatory) submissions at (0,0)
        // in submitVisit() pass cleanly.
        shop.setLatitude(0.0);
        shop.setLongitude(0.0);
        shop.setRadius(1000.0);
        shop.setGeoFencingEnabled(true);
        shop.setCreatedAt(LocalDateTime.now());
        shop.setIsActive(true);
        return customerShopRepository.save(shop);
    }

    private void seedCompanyCheckIn(Long tenantId, Agent agent) {
        Mart companyMart = new Mart();
        companyMart.setTenantId(tenantId);
        companyMart.setName("Voucher Test Company Depot " + System.nanoTime());
        companyMart.setLatitude(0.0);
        companyMart.setLongitude(0.0);
        companyMart.setRadius(100.0);
        companyMart.setGeoFencingEnabled(true);
        companyMart.setIsActive(true);
        companyMart.setMartType(MartType.COMPANY);
        companyMart.setCreatedAt(LocalDateTime.now());
        companyMart = martRepository.save(companyMart);

        Attendance attendance = new Attendance();
        attendance.setTenantId(tenantId);
        attendance.setAgent(agent);
        attendance.setMart(companyMart);
        attendance.setCheckInTime(LocalDateTime.now());
        attendance.setStatus("IN");
        attendanceRepository.save(attendance);
    }

    /** Submits a shop visit with one SALE line and one RETURN line, directly through the real service (not HTTP), matching ShopVisitTest's own established pattern for tests that don't need to exercise controller-level gating. */
    private Long submitVisit(Long tenantId, Agent agent, CustomerShop shop, Product product, int saleQty, int returnQty) {
        TenantContext.setTenantId(tenantId);
        try {
            if (!"SALESMAN_LOCAL".equals(agent.getRole())) {
                seedCompanyCheckIn(tenantId, agent);
            }
            ShopVisitRequest req = new ShopVisitRequest();
            req.setAgentId(agent.getId());
            req.setShopCode(shop.getShopCode());
            req.setLatitude(0.0);
            req.setLongitude(0.0);

            ShopVisitItemRequest saleItem = new ShopVisitItemRequest();
            saleItem.setProductId(product.getId());
            saleItem.setQuantity(saleQty);
            saleItem.setTransactionType("SALE");

            ShopVisitItemRequest returnItem = new ShopVisitItemRequest();
            returnItem.setProductId(product.getId());
            returnItem.setQuantity(returnQty);
            returnItem.setTransactionType("RETURN");

            req.setItems(List.of(saleItem, returnItem));
            return salesService.submitShopVisit(req).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    @Test
    void shopListAndVoucherListTotalsAreComputedByTheDatabaseNotJava() {
        Long tid = tenantId();
        Agent lmtAgent = seedAgent(tid, "SVT_LMT_1", "SALESMAN_LMT");
        Product product = seedProduct(tid); // salesmanPrice = 50.0
        CustomerShop shop = seedShop(tid, "SVT-SHOP-1");

        // SALE 4 units (=200.0), RETURN 1 unit (=50.0) -> net 150.0
        Long voucherId = submitVisit(tid, lmtAgent, shop, product, 4, 1);

        String adminToken = tokenProvider.generateToken(999L, "SVT_ADMIN_1", "ADMIN");

        ResponseEntity<String> shopsResponse = restTemplate.exchange(
                url("/api/sales/vouchers/lmt/shops?shopSearch=SVT-SHOP-1"), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, shopsResponse.getStatusCode());
        String body = shopsResponse.getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"voucherCount\":1"), "Expected exactly one voucher for this shop: " + body);
        assertTrue(body.contains("\"saleAmount\":200.0"), "Sale amount must be the DB-aggregated 4x50: " + body);
        assertTrue(body.contains("\"returnAmount\":50.0"), "Return amount must be the DB-aggregated 1x50: " + body);

        ResponseEntity<String> voucherListResponse = restTemplate.exchange(
                url("/api/sales/vouchers/lmt/shops/" + shop.getId()), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, voucherListResponse.getStatusCode());
        String listBody = voucherListResponse.getBody();
        assertNotNull(listBody);
        assertTrue(listBody.contains("\"voucherId\":" + voucherId), "The voucher must appear in its shop's list: " + listBody);
        assertTrue(listBody.contains("\"netAmount\":150.0"), "Net must be 200 sale - 50 return: " + listBody);

        ResponseEntity<String> detailResponse = restTemplate.exchange(
                url("/api/sales/vouchers/" + voucherId), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, detailResponse.getStatusCode());
        assertTrue(detailResponse.getBody().contains("SVT-SHOP-1"), "Detail must include the shop: " + detailResponse.getBody());
    }

    @Test
    void voucherStaysInItsOriginalSectionEvenAfterTheAgentsRoleLaterChanges() {
        Long tid = tenantId();
        Agent localAgent = seedAgent(tid, "SVT_LOCAL_1", "SALESMAN_LOCAL");
        Product product = seedProduct(tid);
        CustomerShop shop = seedShop(tid, "SVT-SHOP-2");

        Long voucherId = submitVisit(tid, localAgent, shop, product, 2, 0);

        // The agent is later reassigned to LMT — their OLD voucher must not jump sections.
        localAgent.setRole("SALESMAN_LMT");
        agentRepository.save(localAgent);

        String adminToken = tokenProvider.generateToken(999L, "SVT_ADMIN_2", "ADMIN");

        ResponseEntity<String> localList = restTemplate.exchange(
                url("/api/sales/vouchers/local/shops/" + shop.getId()), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, localList.getStatusCode());
        assertTrue(localList.getBody().contains("\"voucherId\":" + voucherId),
                "Voucher must still show under Local (the role AT SALE TIME), even though the agent is now LMT: " + localList.getBody());

        ResponseEntity<String> lmtList = restTemplate.exchange(
                url("/api/sales/vouchers/lmt/shops/" + shop.getId()), HttpMethod.GET, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, lmtList.getStatusCode());
        assertFalse(lmtList.getBody().contains("\"voucherId\":" + voucherId),
                "Voucher must NOT have jumped to LMT just because the agent's role changed later: " + lmtList.getBody());
    }

    private Tenant seedTenant(String companyCode, String name) {
        Tenant tenant = new Tenant();
        tenant.setCompanyCode(companyCode);
        tenant.setName(name);
        tenant.setIsActive(true);
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setCreatedBy("TEST");
        return tenantRepository.save(tenant);
    }

    @Test
    void voucherDetailAndPdfReturn404ForAnotherTenantsVoucherNeverLeakingData() {
        Tenant tenantA = seedTenant("SVTA", "Voucher Tenant A");
        Tenant tenantB = seedTenant("SVTB", "Voucher Tenant B");

        Agent adminA = seedAgent(tenantA.getId(), "SVT_ADMIN_A", "ADMIN");
        Agent lmtAgentB = seedAgent(tenantB.getId(), "SVT_LMT_B", "SALESMAN_LMT");
        Product productB = seedProduct(tenantB.getId());
        CustomerShop shopB = seedShop(tenantB.getId(), "SVT-SHOP-TENANT-B");

        Long voucherIdInTenantB = submitVisit(tenantB.getId(), lmtAgentB, shopB, productB, 3, 0);

        String tokenA = tokenProvider.generateToken(adminA.getId(), adminA.getAgentId(), "ADMIN", tenantA.getId());

        ResponseEntity<String> crossTenantDetail = restTemplate.exchange(
                url("/api/sales/vouchers/" + voucherIdInTenantB), HttpMethod.GET, withToken(tokenA), String.class);
        assertEquals(HttpStatus.NOT_FOUND, crossTenantDetail.getStatusCode(),
                "Tenant A's admin requesting Tenant B's real voucher id must 404, not leak the data or 403: " + crossTenantDetail.getBody());

        ResponseEntity<byte[]> crossTenantPdf = restTemplate.exchange(
                url("/api/sales/vouchers/" + voucherIdInTenantB + "/pdf"), HttpMethod.GET, withToken(tokenA), byte[].class);
        assertEquals(HttpStatus.NOT_FOUND, crossTenantPdf.getStatusCode(),
                "The PDF endpoint must apply the exact same tenant-safe 404, never generating a PDF for another tenant's voucher");

        // Tenant B's own admin can still see it fine.
        String tokenB = tokenProvider.generateToken(999L, "SVT_ADMIN_B", "ADMIN", tenantB.getId());
        ResponseEntity<String> ownDetail = restTemplate.exchange(
                url("/api/sales/vouchers/" + voucherIdInTenantB), HttpMethod.GET, withToken(tokenB), String.class);
        assertEquals(HttpStatus.OK, ownDetail.getStatusCode(), "Tenant B reading its own voucher must still work: " + ownDetail.getBody());

        ResponseEntity<byte[]> ownPdf = restTemplate.exchange(
                url("/api/sales/vouchers/" + voucherIdInTenantB + "/pdf"), HttpMethod.GET, withToken(tokenB), byte[].class);
        assertEquals(HttpStatus.OK, ownPdf.getStatusCode(), "Tenant B generating its own voucher's PDF must still work");
        assertTrue(ownPdf.getBody().length > 0, "PDF body must not be empty");
    }

    /**
     * Task 4 correction: moved to the Sales sidebar, ADMIN + SALES now —
     * a salesman (either role) and HR must still be blocked on every
     * endpoint, including the PDF.
     */
    @Test
    void salesmanAndHrCannotAccessVoucherEndpoints() {
        Long tid = tenantId();
        Agent lmtAgent = seedAgent(tid, "SVT_LMT_NOADMIN", "SALESMAN_LMT");
        Agent localAgent = seedAgent(tid, "SVT_LOCAL_NOADMIN", "SALESMAN_LOCAL");
        Agent hrAgent = seedAgent(tid, "SVT_HR_NOADMIN", "HR");
        String salesmanToken = tokenProvider.generateToken(lmtAgent.getId(), lmtAgent.getAgentId(), "SALESMAN_LMT");
        String localToken = tokenProvider.generateToken(localAgent.getId(), localAgent.getAgentId(), "SALESMAN_LOCAL");
        String hrToken = tokenProvider.generateToken(hrAgent.getId(), hrAgent.getAgentId(), "HR");

        for (String token : List.of(salesmanToken, localToken, hrToken)) {
            assertEquals(HttpStatus.FORBIDDEN, restTemplate.exchange(
                    url("/api/sales/vouchers/lmt/shops"), HttpMethod.GET, withToken(token), String.class).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN, restTemplate.exchange(
                    url("/api/sales/vouchers/local/shops"), HttpMethod.GET, withToken(token), String.class).getStatusCode());
        }
    }

    /** A SALES-role admin-panel user must be able to open all three pages and download the PDF — the whole point of moving this under the Sales sidebar. */
    @Test
    void salesRoleCanOpenAllThreePagesAndDownloadThePdf() {
        Long tid = tenantId();
        Agent salesUser = seedAgent(tid, "SVT_SALES_ROLE", "SALES");
        Agent lmtAgent = seedAgent(tid, "SVT_LMT_FOR_SALES_ROLE", "SALESMAN_LMT");
        Product product = seedProduct(tid);
        CustomerShop shop = seedShop(tid, "SVT-SHOP-SALES-ROLE");
        Long voucherId = submitVisit(tid, lmtAgent, shop, product, 2, 0);

        String salesToken = tokenProvider.generateToken(salesUser.getId(), salesUser.getAgentId(), "SALES");

        ResponseEntity<String> shopsResponse = restTemplate.exchange(
                url("/api/sales/vouchers/lmt/shops"), HttpMethod.GET, withToken(salesToken), String.class);
        assertEquals(HttpStatus.OK, shopsResponse.getStatusCode(), "SALES must be able to open the shop-list page: " + shopsResponse.getBody());

        ResponseEntity<String> listResponse = restTemplate.exchange(
                url("/api/sales/vouchers/lmt/shops/" + shop.getId()), HttpMethod.GET, withToken(salesToken), String.class);
        assertEquals(HttpStatus.OK, listResponse.getStatusCode(), "SALES must be able to open a shop's voucher list: " + listResponse.getBody());

        ResponseEntity<String> detailResponse = restTemplate.exchange(
                url("/api/sales/vouchers/" + voucherId), HttpMethod.GET, withToken(salesToken), String.class);
        assertEquals(HttpStatus.OK, detailResponse.getStatusCode(), "SALES must be able to open the voucher detail page: " + detailResponse.getBody());

        ResponseEntity<byte[]> pdfResponse = restTemplate.exchange(
                url("/api/sales/vouchers/" + voucherId + "/pdf"), HttpMethod.GET, withToken(salesToken), byte[].class);
        assertEquals(HttpStatus.OK, pdfResponse.getStatusCode(), "SALES must be able to download the voucher PDF");
        assertTrue(pdfResponse.getBody().length > 0, "PDF body must not be empty");
    }
}
