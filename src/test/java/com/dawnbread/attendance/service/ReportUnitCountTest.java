package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.CustomerShopCreateDTO;
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
import com.dawnbread.attendance.repository.SalesRecordRepository;
import com.dawnbread.attendance.repository.ShopProductPriceRepository;
import com.dawnbread.attendance.dto.ReportDTO;
import java.time.LocalDate;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Role-scoped sales reports must count only SALE lines as sold units — LMT
 * visits also carry RETURN lines whose quantity is not "sold". The Agent
 * role report must be exactly what it was (every Agent line is a SALE).
 * Rolled back per test so the shared in-memory DB isn't polluted.
 */
@SpringBootTest
@org.springframework.transaction.annotation.Transactional
class ReportUnitCountTest {

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
    private SalesRecordRepository salesRecordRepository;

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

    private SalesRecord submitMixedVisit(Agent lmt, CustomerShop shop, Product sold, Product returned, int soldQty, int returnQty) {
        ShopVisitRequest request = new ShopVisitRequest();
        request.setAgentId(lmt.getId());
        request.setShopCode(shop.getShopCode());
        request.setLatitude(0.0);
        request.setLongitude(0.0);
        ShopVisitItemRequest saleLine = new ShopVisitItemRequest();
        saleLine.setProductId(sold.getId());
        saleLine.setQuantity(soldQty);
        saleLine.setTransactionType("SALE");
        ShopVisitItemRequest returnLine = new ShopVisitItemRequest();
        returnLine.setProductId(returned.getId());
        returnLine.setQuantity(returnQty);
        returnLine.setTransactionType("RETURN");
        request.setItems(List.of(saleLine, returnLine));
        return salesService.submitShopVisit(request);
    }

    private int allLineUnits(List<SalesRecord> records) {
        return records.stream().flatMap(r -> r.getItems().stream()).mapToInt(SaleItem::getQuantity).sum();
    }

    private int saleLineUnits(List<SalesRecord> records) {
        return records.stream().flatMap(r -> r.getItems().stream())
                .filter(i -> i.getTransactionType() == null || "SALE".equals(i.getTransactionType().name()))
                .mapToInt(SaleItem::getQuantity).sum();
    }

    @Test
    void lmtRoleReportsCountOnlySaleLinesAsSoldUnits() {
        Product sold = seedProduct(100.0);
        Product returned = seedProduct(100.0);
        CustomerShop shop = seedShop(null);
        Agent lmt = seedLmt("UNITS_LMT");
        seedCompanyCheckIn(lmt);
        submitMixedVisit(lmt, shop, sold, returned, 5, 3);

        LocalDate today = LocalDate.now();
        List<SalesRecord> lmtRecords = salesRecordRepository.findBySaleDateAndAgentRole(today, "SALESMAN_LMT");
        int expectedSold = saleLineUnits(lmtRecords);
        assertTrue(allLineUnits(lmtRecords) >= expectedSold + 3, "the test data must actually contain returned units");

        ReportDTO daily = salesService.generateDailyReport(today, "SALESMAN_LMT");
        assertEquals(expectedSold, daily.getTotalUnits(), "daily LMT units must exclude the 3 returned");
        assertEquals(expectedSold, daily.getAgentSummaries().stream().mapToInt(ReportDTO.AgentReportSummary::getTotalUnits).sum(),
                "per-salesman units must agree with the total");
        assertEquals(expectedSold, daily.getProductPerformance().stream().mapToInt(ReportDTO.ProductPerformanceDetail::getQuantitySold).sum(),
                "product-wise sold must exclude returned lines");

        ReportDTO weekly = salesService.generateWeeklyReport(today, "SALESMAN_LMT");
        assertEquals(expectedSold, weekly.getTotalUnits());
        ReportDTO monthly = salesService.generateMonthlyReport(today, "SALESMAN_LMT");
        assertEquals(saleLineUnits(salesRecordRepository.findBySaleDateBetweenAndAgentRole(
                today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()), "SALESMAN_LMT")), monthly.getTotalUnits());

        // the returned product must not show up as "sold" at all
        assertTrue(daily.getProductPerformance().stream().noneMatch(p -> p.getProductName().equals(returned.getName())),
                "a product that was only returned has no sold quantity");
    }

    @Test
    void agentRoleReportIsExactlyWhatItWasBefore() {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId("UNITS_AGENT");
        agent.setName("Seed UNITS_AGENT");
        agent.setEmail("units_agent@example.com");
        agent.setRole("AGENT");
        agent.setCreatedAt(LocalDateTime.now());
        agent = agentRepository.save(agent);
        Product p = seedProduct(100.0);
        salesService.addSalesWithImages(new SalesRequest(agent.getId(), "Units Store", List.of(new SaleItemRequest(p.getId(), 7))));

        LocalDate today = LocalDate.now();
        List<SalesRecord> agentRecords = salesRecordRepository.findBySaleDateAndAgentRole(today, "AGENT");
        // "before" behavior = sum of EVERY line's quantity; for agents every line is a SALE, so this must be identical
        assertEquals(allLineUnits(agentRecords), saleLineUnits(agentRecords), "every Agent line is a SALE");
        ReportDTO daily = salesService.generateDailyReport(today, "AGENT");
        assertEquals(allLineUnits(agentRecords), daily.getTotalUnits(), "AGENT role units are unchanged by the fix");
        assertEquals(allLineUnits(agentRecords),
                daily.getProductPerformance().stream().mapToInt(ReportDTO.ProductPerformanceDetail::getQuantitySold).sum());
    }

    @Test
    void unfilteredReportPathIsUntouched() {
        // role == null still delegates to the original code, which sums every line — deliberately not changed here.
        Product sold = seedProduct(100.0);
        Product returned = seedProduct(100.0);
        CustomerShop shop = seedShop(null);
        Agent lmt = seedLmt("UNITS_UNFILTERED");
        seedCompanyCheckIn(lmt);
        submitMixedVisit(lmt, shop, sold, returned, 5, 3);

        LocalDate today = LocalDate.now();
        ReportDTO unfiltered = salesService.generateDailyReport(today, null);
        assertEquals(allLineUnits(salesRecordRepository.findBySaleDate(today)), unfiltered.getTotalUnits());
    }
}
