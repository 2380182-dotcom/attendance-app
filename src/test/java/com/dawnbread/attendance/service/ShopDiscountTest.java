package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.ShopVisitItemRequest;
import com.dawnbread.attendance.dto.ShopVisitRequest;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.entity.MartType;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.SaleItem;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.entity.ShopProductDiscount;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
import com.dawnbread.attendance.repository.MartRepository;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.ShopProductDiscountRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Per-shop discounts (Feature 2, P5) — resolution order (SKU override ->
 * shop overall -> 0), applied as salesmanPrice first then discount, SALE
 * lines only. The zero-discount test is the regression safety gate: an
 * LMT sale at a shop with no discount configured at all must compute the
 * exact same total it did before this feature existed.
 */
@SpringBootTest
class ShopDiscountTest {

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
    private ShopProductDiscountRepository shopProductDiscountRepository;

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

    private Agent seedLmt(String agentId) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole("SALESMAN_LMT");
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private Product seedProduct(double salesmanPrice) {
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("Shop Discount Test Bread " + System.nanoTime());
        product.setAgentPrice(salesmanPrice);
        product.setSalesmanPrice(salesmanPrice);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private CustomerShop seedShop(Double shopDiscountPercent) {
        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("Shop Discount Test Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode("DISC-" + System.nanoTime());
        shop.setShopName("Shop Discount Test Shop");
        shop.setArea(area);
        shop.setGeoFencingEnabled(false); // no geofence — not the concern of this test
        shop.setIsActive(true);
        shop.setDiscountPercent(shopDiscountPercent);
        shop.setCreatedAt(LocalDateTime.now());
        return customerShopRepository.save(shop);
    }

    private void seedSkuOverride(CustomerShop shop, Product product, double discountPercent) {
        ShopProductDiscount override = new ShopProductDiscount();
        override.setTenantId(tenantId());
        override.setCustomerShop(shop);
        override.setProduct(product);
        override.setDiscountPercent(discountPercent);
        shopProductDiscountRepository.save(override);
    }

    private void seedCompanyCheckIn(Agent agent) {
        Mart companyMart = new Mart();
        companyMart.setTenantId(tenantId());
        companyMart.setName("Discount Test Depot " + System.nanoTime());
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

    private SalesRecord submitSale(Agent lmt, CustomerShop shop, Product product, int quantity, String transactionType) {
        ShopVisitRequest request = new ShopVisitRequest();
        request.setAgentId(lmt.getId());
        request.setShopCode(shop.getShopCode());
        request.setLatitude(0.0);
        request.setLongitude(0.0);
        ShopVisitItemRequest item = new ShopVisitItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(quantity);
        item.setTransactionType(transactionType);
        request.setItems(List.of(item));
        return salesService.submitShopVisit(request);
    }

    @Test
    void skuOverrideWinsOverShopLevelDiscount() {
        // Shop-level 10%, SKU override 25% for this specific product — the
        // override must win, never both, never averaged.
        Product product = seedProduct(100.0);
        CustomerShop shop = seedShop(10.0);
        seedSkuOverride(shop, product, 25.0);
        Agent lmt = seedLmt("DISCOUNT_SKU_WINS");
        seedCompanyCheckIn(lmt);

        SalesRecord saved = submitSale(lmt, shop, product, 2, "SALE");

        // 100.0 * 2 * (1 - 0.25) = 150.0 — NOT the 10% shop rate (180.0).
        assertEquals(150.0, saved.getTotalAmount(), 0.001,
                "The SKU override (25%) must win over the shop's overall discount (10%)");
        SaleItem item = saved.getItems().get(0);
        assertEquals(25.0, item.getDiscountPercent(), 0.001);
    }

    @Test
    void shopLevelDiscountAppliesWhenNoSkuOverrideExists() {
        Product product = seedProduct(100.0);
        CustomerShop shop = seedShop(20.0);
        // No SKU override seeded for this product.
        Agent lmt = seedLmt("DISCOUNT_SHOP_LEVEL");
        seedCompanyCheckIn(lmt);

        SalesRecord saved = submitSale(lmt, shop, product, 3, "SALE");

        // 100.0 * 3 * (1 - 0.20) = 240.0
        assertEquals(240.0, saved.getTotalAmount(), 0.001,
                "The shop's overall discount must apply when no per-product override exists");
        assertEquals(20.0, saved.getItems().get(0).getDiscountPercent(), 0.001);
    }

    @Test
    void zeroDiscountProducesIdenticalTotalsToPreFeatureBehavior() {
        // No shop discount configured at all (null) and no SKU override —
        // this must compute EXACTLY what P2 alone would have computed, the
        // regression safety gate for every existing/typical LMT sale.
        Product product = seedProduct(100.0);
        CustomerShop shop = seedShop(null);
        Agent lmt = seedLmt("DISCOUNT_ZERO");
        seedCompanyCheckIn(lmt);

        SalesRecord saved = submitSale(lmt, shop, product, 5, "SALE");

        assertEquals(500.0, saved.getTotalAmount(), 0.001,
                "With no discount configured, revenue must be exactly salesmanPrice x qty — unchanged from before this feature");
        SaleItem item = saved.getItems().get(0);
        assertEquals(100.0, item.getUnitPrice(), 0.001);
        assertEquals(500.0, item.getTotalPrice(), 0.001);
        assertEquals(0.0, item.getDiscountPercent(), 0.001,
                "Discount percent should resolve to 0, not null, when nothing is configured");
    }

    @Test
    void returnLinesAreNeverDiscounted() {
        // Shop has a steep 50% discount configured — a RETURN line must
        // completely ignore it, per the confirmed recommendation.
        Product product = seedProduct(100.0);
        CustomerShop shop = seedShop(50.0);
        Agent lmt = seedLmt("DISCOUNT_RETURN_UNAFFECTED");
        seedCompanyCheckIn(lmt);

        SalesRecord saved = submitSale(lmt, shop, product, 4, "RETURN");

        SaleItem item = saved.getItems().get(0);
        assertEquals(400.0, item.getTotalPrice(), 0.001,
                "RETURN lines must use plain salesmanPrice x qty, completely unaffected by the shop's discount");
        assertNull(item.getDiscountPercent(), "RETURN lines must never carry a discount percent");
        assertEquals(0.0, saved.getTotalAmount(), 0.001,
                "RETURN never contributes to totalAmount — unchanged from before this feature");
    }
}
