package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.SaleItemRequest;
import com.dawnbread.attendance.dto.SalesRequest;
import com.dawnbread.attendance.dto.ShopVisitItemRequest;
import com.dawnbread.attendance.dto.ShopVisitRequest;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.entity.MartType;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
import com.dawnbread.attendance.repository.MartRepository;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Role-based pricing (P2) — the load-bearing safety test for the 25 live
 * Agents. Proves two things, with a product whose agentPrice and
 * salesmanPrice are DELIBERATELY different (so a bug that used the wrong
 * one would fail loudly, not silently pass by coincidence):
 *
 * 1. The legacy AGENT flow computes revenue using agentPrice only, exactly
 *    as it always computed revenue using the single `price` field before
 *    this feature — byte-for-byte identical to what a pre-migration agent
 *    sale would have produced (proven by the V22 migration's backfill:
 *    agentPrice starts equal to the old price for every existing
 *    product, and this test's own agentPrice value plays the role of
 *    "the old price" directly).
 * 2. The LMT shop-visit flow computes revenue using salesmanPrice — the
 *    one intentional divergence introduced in P2 — never agentPrice.
 */
@SpringBootTest
class RoleBasedPricingTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private CustomerShopRepository customerShopRepository;

    @Autowired
    private MartRepository martRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private SalesService salesService;

    @BeforeEach
    void setTenantContext() {
        TenantContext.setTenantId(tenantId());
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
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

    /** agentPrice and salesmanPrice deliberately differ — a wrong-field bug would fail loudly. */
    private Product seedProduct(double agentPrice, double salesmanPrice) {
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("Role Pricing Test Bread " + System.nanoTime());
        product.setAgentPrice(agentPrice);
        product.setSalesmanPrice(salesmanPrice);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    /** D2's company check-in gate — submitShopVisit requires this before it'll accept anything. */
    private void seedCompanyCheckIn(Agent agent) {
        Mart companyMart = new Mart();
        companyMart.setTenantId(tenantId());
        companyMart.setName("Pricing Test Depot " + System.nanoTime());
        companyMart.setLatitude(0.0);
        companyMart.setLongitude(0.0);
        companyMart.setRadius(100.0);
        companyMart.setGeoFencingEnabled(true);
        companyMart.setIsActive(true);
        companyMart.setMartType(MartType.COMPANY);
        companyMart.setCreatedAt(LocalDateTime.now());
        companyMart = martRepository.save(companyMart);

        Attendance attendance = new Attendance();
        attendance.setTenantId(tenantId());
        attendance.setAgent(agent);
        attendance.setMart(companyMart);
        attendance.setCheckInTime(LocalDateTime.now());
        attendance.setStatus("IN");
        attendanceRepository.save(attendance);
    }

    private CustomerShop seedShop(String code) {
        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("Pricing Test Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode(code);
        shop.setShopName("Pricing Test Shop " + code);
        shop.setArea(area);
        shop.setGeoFencingEnabled(false); // no geofence configured — gate skipped, not the concern of this test
        shop.setIsActive(true);
        shop.setCreatedAt(LocalDateTime.now());
        return customerShopRepository.save(shop);
    }

    @Test
    void agentSaleComputesRevenueUsingAgentPriceOnly() {
        // agentPrice = 75.0 plays the role of "the old, pre-migration
        // price" — this is exactly what an Agent's sale would have
        // computed before role-based pricing existed, and must still
        // compute after it, byte-for-byte.
        Product product = seedProduct(75.0, 999.0);
        Agent agent = seedAgent("PRICING_AGENT_1", "AGENT");

        SalesRequest request = new SalesRequest(agent.getId(), "Pricing Test Store",
                List.of(new SaleItemRequest(product.getId(), 4)));
        SalesRecord saved = salesService.addSalesWithImages(request);

        assertEquals(300.0, saved.getTotalAmount(), 0.001,
                "AGENT sale must compute 4 x agentPrice(75.0) = 300.0 — completely unaffected by salesmanPrice");
        assertEquals(1, saved.getItems().size());
        assertEquals(75.0, saved.getItems().get(0).getUnitPrice(), 0.001,
                "The stored unitPrice on the AGENT's SaleItem must be agentPrice, never salesmanPrice");
    }

    @Test
    void lmtShopVisitComputesRevenueUsingSalesmanPriceOnly() {
        // Same two distinct prices, opposite assertion — proves the LMT
        // path actually diverges to salesmanPrice, not a coincidence of
        // both flows reading the same field.
        Product product = seedProduct(75.0, 999.0);
        Agent lmt = seedAgent("PRICING_LMT_1", "SALESMAN_LMT");
        CustomerShop shop = seedShop("PRICING-SHOP-1");
        seedCompanyCheckIn(lmt);

        ShopVisitRequest request = new ShopVisitRequest();
        request.setAgentId(lmt.getId());
        request.setShopCode(shop.getShopCode());
        request.setLatitude(0.0);
        request.setLongitude(0.0);
        ShopVisitItemRequest item = new ShopVisitItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(3);
        item.setTransactionType("SALE");
        request.setItems(List.of(item));

        SalesRecord saved = salesService.submitShopVisit(request);

        assertEquals(2997.0, saved.getTotalAmount(), 0.001,
                "LMT shop-visit sale must compute 3 x salesmanPrice(999.0) = 2997.0 — never agentPrice(75.0)");
        assertEquals(999.0, saved.getItems().get(0).getUnitPrice(), 0.001,
                "The stored unitPrice on the LMT's SaleItem must be salesmanPrice, never agentPrice");
    }
}
