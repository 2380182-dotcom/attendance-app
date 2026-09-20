package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.AgentRegistrationDTO;
import com.dawnbread.attendance.dto.ReportDTO;
import java.time.LocalDate;
import com.dawnbread.attendance.dto.SaleItemRequest;
import com.dawnbread.attendance.dto.SalesRequest;
import com.dawnbread.attendance.dto.ShopProductPricesUpdateDTO;
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
import com.dawnbread.attendance.entity.ShopProductPrice;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
import com.dawnbread.attendance.repository.MartRepository;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.ShopProductDiscountRepository;
import com.dawnbread.attendance.repository.ShopProductPriceRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SALESMAN_LOCAL (L1): no check-in gate, but the shop's geofence is
 * mandatory and enforced; uses the same per-shop pricing as LMT; face
 * verification is fully off at creation. The LMT-regression tests prove
 * LMT still needs a check-in and Agent creation defaults are unchanged.
 */
@SpringBootTest
@org.springframework.transaction.annotation.Transactional
class LocalSalesmanTest {

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
    private ShopProductPriceRepository shopProductPriceRepository;

    @Autowired
    private CustomerShopService customerShopService;

    @Autowired
    private SalesService salesService;

    @Autowired
    private AdminService adminService;

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

    private Agent seedSalesman(String agentId, String role) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole(role);
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private Agent seedLmt(String agentId) {
        return seedSalesman(agentId, "SALESMAN_LMT");
    }

    private Agent seedLocal(String agentId) {
        return seedSalesman(agentId, "SALESMAN_LOCAL");
    }

    private Product seedProduct(double salesmanPrice) {
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("Shop Pricing Test Bread " + System.nanoTime());
        product.setAgentPrice(55.0);
        product.setSalesmanPrice(salesmanPrice);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private CustomerShop seedShop(Double shopDiscountPercent) {
        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("Shop Pricing Test Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode("PRC-" + System.nanoTime());
        shop.setShopName("Shop Pricing Test Shop");
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

    /** A shop with a real geofence: lat 24.86 / lon 67.0, radius 50m. */
    private CustomerShop seedGeoShop(Double discount) {
        CustomerShop shop = seedShop(discount);
        shop.setGeoFencingEnabled(true);
        shop.setLatitude(24.86);
        shop.setLongitude(67.0);
        shop.setRadius(50.0);
        return customerShopRepository.save(shop);
    }

    private SalesRecord visit(Agent who, CustomerShop shop, Product product, int qty, String type, double lat, double lon) {
        ShopVisitRequest request = new ShopVisitRequest();
        request.setAgentId(who.getId());
        request.setShopCode(shop.getShopCode());
        request.setLatitude(lat);
        request.setLongitude(lon);
        ShopVisitItemRequest item = new ShopVisitItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(qty);
        item.setTransactionType(type);
        request.setItems(List.of(item));
        return salesService.submitShopVisit(request);
    }

    private void seedShopPrice(CustomerShop shop, Product product, double price) {
        ShopProductPrice row = new ShopProductPrice();
        row.setTenantId(tenantId());
        row.setCustomerShop(shop);
        row.setProduct(product);
        row.setPrice(price);
        shopProductPriceRepository.save(row);
    }

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
    void localSalesmanCanRecordASaleWithoutAnyCheckIn() {
        Product product = seedProduct(100.0);
        CustomerShop shop = seedGeoShop(null);
        Agent local = seedLocal("LOCAL_NO_CHECKIN"); // deliberately NO seedCompanyCheckIn

        SalesRecord saved = visit(local, shop, product, 4, "SALE", 24.86, 67.0);

        assertEquals(400.0, saved.getTotalAmount(), 0.001);
        assertEquals(shop.getId(), saved.getCustomerShop().getId(), "the sale is tagged with its shop");
        assertEquals(product.getId(), saved.getItems().get(0).getProduct().getId(), "and its product");
    }

    @Test
    void localSalesmanUsesShopPriceThenDiscountLikeLmt() {
        Product product = seedProduct(100.0);
        CustomerShop shop = seedGeoShop(10.0);
        seedShopPrice(shop, product, 200.0);
        Agent local = seedLocal("LOCAL_PRICING");

        SalesRecord saved = visit(local, shop, product, 2, "SALE", 24.86, 67.0);

        assertEquals(360.0, saved.getTotalAmount(), 0.001, "200 x 2 x (1 - 0.10)");
        assertEquals(200.0, saved.getItems().get(0).getUnitPrice(), 0.001);
        assertEquals(10.0, saved.getItems().get(0).getDiscountPercent(), 0.001);
    }

    @Test
    void localSalesmanCanRecordAReturn() {
        Product product = seedProduct(100.0);
        CustomerShop shop = seedGeoShop(50.0);
        Agent local = seedLocal("LOCAL_RETURN");

        SalesRecord saved = visit(local, shop, product, 3, "RETURN", 24.86, 67.0);

        assertEquals(0.0, saved.getTotalAmount(), 0.001, "returns never count as revenue");
        assertEquals("RETURN", saved.getItems().get(0).getTransactionType().name());
        assertEquals(300.0, saved.getItems().get(0).getTotalPrice(), 0.001, "valued at the price, undiscounted");
    }

    @Test
    void localSalesmanMustPhysicallyBeAtTheShop() {
        Product product = seedProduct(100.0);
        CustomerShop shop = seedGeoShop(null);
        Agent local = seedLocal("LOCAL_TOO_FAR");

        // ~1.1 km north of the shop, far outside 50m radius + buffer
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> visit(local, shop, product, 1, "SALE", 24.87, 67.0));
        assertTrue(ex.getMessage().contains("Too far"), ex.getMessage());
    }

    @Test
    void localSalesmanCannotRecordAtAShopWithNoLocationSetUp() {
        Product product = seedProduct(100.0);
        CustomerShop shop = seedShop(null); // geoFencingEnabled = false
        Agent local = seedLocal("LOCAL_NO_GEOFENCE");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> visit(local, shop, product, 1, "SALE", 24.86, 67.0));
        assertTrue(ex.getMessage().contains("no location set up"), ex.getMessage());
    }

    @Test
    void lmtStillRequiresACompanyCheckInExactlyAsBefore() {
        Product product = seedProduct(100.0);
        CustomerShop shop = seedGeoShop(null);
        Agent lmt = seedLmt("LMT_STILL_GATED"); // no check-in

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> visit(lmt, shop, product, 1, "SALE", 24.86, 67.0));
        assertTrue(ex.getMessage().contains("check in at the company"), ex.getMessage());

        seedCompanyCheckIn(lmt);
        assertEquals(100.0, visit(lmt, shop, product, 1, "SALE", 24.86, 67.0).getTotalAmount(), 0.001);
    }

    @Test
    void lmtAtAShopWithNoGeofenceIsStillAllowedAsBefore() {
        // The "geofence is mandatory" rule is local-only — LMT behavior is unchanged.
        Product product = seedProduct(100.0);
        CustomerShop shop = seedShop(null);
        Agent lmt = seedLmt("LMT_NO_GEOFENCE_OK");
        seedCompanyCheckIn(lmt);

        assertEquals(100.0, visit(lmt, shop, product, 1, "SALE", 24.86, 67.0).getTotalAmount(), 0.001);
    }

    @Test
    void localSalesmanReportsAreScopedByRoleAndCountOnlySales() {
        Product sold = seedProduct(100.0);
        Product returned = seedProduct(100.0);
        CustomerShop shop = seedGeoShop(null);
        Agent local = seedLocal("LOCAL_REPORT");
        visit(local, shop, sold, 6, "SALE", 24.86, 67.0);
        visit(local, shop, returned, 2, "RETURN", 24.86, 67.0);

        ReportDTO report = salesService.generateDailyReport(LocalDate.now(), "SALESMAN_LOCAL");

        assertEquals(6, report.getTotalUnits(), "6 sold; the 2 returned are not sold units");
        assertTrue(report.getAgentSummaries().stream().anyMatch(s -> s.getEmployeeId().equals("LOCAL_REPORT")));
        // and the LMT report must not contain the local salesman at all
        assertTrue(salesService.generateDailyReport(LocalDate.now(), "SALESMAN_LMT").getAgentSummaries().stream()
                .noneMatch(s -> s.getEmployeeId().equals("LOCAL_REPORT")));
    }

    private AgentRegistrationDTO registration(String agentId, String role) {
        AgentRegistrationDTO dto = new AgentRegistrationDTO();
        dto.setAgentId(agentId);
        dto.setName("Created " + agentId);
        dto.setEmail(agentId.toLowerCase() + "@example.com");
        dto.setPassword("Passw0rd!x");
        dto.setRole(role);
        return dto;
    }

    @Test
    void creatingALocalSalesmanTurnsFaceVerificationFullyOff() {
        Agent local = adminService.createAgent(registration("LOCAL_CREATED", "SALESMAN_LOCAL"));

        assertEquals("SALESMAN_LOCAL", local.getRole());
        assertEquals(false, local.getFaceVerificationEnabled());
        assertEquals(false, local.getFaceVerifyOnCheckIn());
        assertEquals(false, local.getFaceVerifyOnCheckOut());
        assertEquals(false, local.getFaceVerifyAnytime());
        assertEquals(0, local.getFaceVerificationFrequency());
    }

    @Test
    void creatingLmtAndAgentUsersIsExactlyAsBefore() {
        Agent lmt = adminService.createAgent(registration("LMT_CREATED", "SALESMAN_LMT"));
        assertEquals(true, lmt.getFaceVerifyOnCheckIn(), "LMT still verifies face once at check-in");
        assertEquals(0, lmt.getFaceVerificationFrequency());
        assertEquals(false, lmt.getFaceVerifyAnytime());

        Agent agent = adminService.createAgent(registration("AGENT_CREATED", "AGENT"));
        assertEquals(true, agent.getFaceVerificationEnabled(), "regular agents keep face verification on by default");
        assertEquals(2, agent.getFaceVerificationFrequency());
        assertEquals(true, agent.getFaceVerifyOnCheckIn());
    }
}
