package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.ShopVisitItemRequest;
import com.dawnbread.attendance.dto.ShopVisitRequest;
import com.dawnbread.attendance.dto.ShopVisitScanRequest;
import com.dawnbread.attendance.entity.*;
import com.dawnbread.attendance.repository.*;
import com.dawnbread.attendance.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the Task 3 timezone fix: a scan or a sale at ~2am Pakistan time
 * must land on Pakistan's calendar day, not UTC's (the server runs on UTC
 * on Render). Uses a fixed Clock (TimeConfig's real bean overridden here)
 * rather than depending on when this test happens to run — 2am Karachi is
 * only a few hours a day, so a wall-clock-based test would be flaky/mostly
 * skipped.
 *
 * The chosen instant, 2026-01-15T21:30:00Z, is 2026-01-16 02:30 in
 * Asia/Karachi (UTC+5) — 2:30am the NEXT calendar day. Before this fix,
 * LocalDate.now() (server/UTC) would have filed this under 2026-01-15;
 * Karachi correctly sees it as 2026-01-16.
 */
@SpringBootTest
@Import(TimezoneAttributionTest.FixedClockConfig.class)
class TimezoneAttributionTest {

    private static final Instant TWO_AM_KARACHI_INSTANT = Instant.parse("2026-01-15T21:30:00Z");
    private static final LocalDate EXPECTED_KARACHI_DAY = LocalDate.of(2026, 1, 16);
    private static final LocalDate WRONG_UTC_DAY = LocalDate.of(2026, 1, 15);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testFixedClock() {
            return Clock.fixed(TWO_AM_KARACHI_INSTANT, ZoneId.of("Asia/Karachi"));
        }
    }

    @Autowired private ShopVisitScanService shopVisitScanService;
    @Autowired private SalesService salesService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AreaRepository areaRepository;
    @Autowired private CustomerShopRepository customerShopRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private MartRepository martRepository;
    @Autowired private ShopVisitScanRepository shopVisitScanRepository;

    private Long tenantId() {
        return tenantRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setCompanyCode("TZTEST");
                    t.setName("TZ Test Tenant");
                    t.setIsActive(true);
                    t.setCreatedAt(LocalDateTime.now());
                    t.setCreatedBy("TEST");
                    return tenantRepository.save(t);
                })
                .getId();
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Agent seedAgent(String agentId, String role) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId(agentId);
        agent.setName("TZ " + agentId);
        agent.setRole(role);
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private Product seedProduct() {
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("TZ Test Bread " + System.nanoTime());
        product.setAgentPrice(50.0);
        product.setSalesmanPrice(50.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private CustomerShop seedShop(String code) {
        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("TZ Test Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode(code);
        shop.setShopName("Shop " + code);
        shop.setArea(area);
        shop.setGeoFencingEnabled(false);
        shop.setIsActive(true);
        shop.setCreatedAt(LocalDateTime.now());
        return customerShopRepository.save(shop);
    }

    private void seedCompanyCheckIn(Agent agent) {
        Mart companyMart = new Mart();
        companyMart.setTenantId(tenantId());
        companyMart.setName("TZ Company Depot " + System.nanoTime());
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

    @Test
    void aScanAtTwoAmPakistanTimeLandsOnTheCorrectKarachiDayNotTheUtcDay() {
        Agent salesman = seedAgent("TZ_SCAN_LMT", "SALESMAN_LMT");
        CustomerShop shop = seedShop("TZ-SHOP-SCAN");
        seedCompanyCheckIn(salesman);

        ShopVisitScanRequest request = new ShopVisitScanRequest();
        request.setAgentId(salesman.getId());
        request.setScannedCode(shop.getShopCode());
        request.setLatitude(0.0);
        request.setLongitude(0.0);

        var response = shopVisitScanService.recordScan(request);
        ShopVisitScan saved = shopVisitScanRepository.findById(response.getVisitId()).orElseThrow();

        assertEquals(EXPECTED_KARACHI_DAY, saved.getScanDate(),
                "A scan at 2:30am Karachi time (9:30pm UTC the previous day) must be filed under Karachi's calendar day");
        org.junit.jupiter.api.Assertions.assertNotEquals(WRONG_UTC_DAY, saved.getScanDate(),
                "Must NOT be filed under the server's UTC calendar day, which is one day behind at this instant");
    }

    @Test
    void aSaleAtTwoAmPakistanTimeLandsOnTheCorrectKarachiDayNotTheUtcDay() {
        Agent salesman = seedAgent("TZ_SALE_LMT", "SALESMAN_LMT");
        Product product = seedProduct();
        CustomerShop shop = seedShop("TZ-SHOP-SALE");
        seedCompanyCheckIn(salesman);

        ShopVisitRequest request = new ShopVisitRequest();
        request.setAgentId(salesman.getId());
        request.setShopCode(shop.getShopCode());
        request.setLatitude(0.0);
        request.setLongitude(0.0);
        ShopVisitItemRequest item = new ShopVisitItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(2);
        item.setTransactionType("SALE");
        request.setItems(java.util.List.of(item));

        SalesRecord saved = salesService.submitShopVisit(request);

        assertEquals(EXPECTED_KARACHI_DAY, saved.getSaleDate(),
                "A sale at 2:30am Karachi time (9:30pm UTC the previous day) must be filed under Karachi's calendar day");
        org.junit.jupiter.api.Assertions.assertNotEquals(WRONG_UTC_DAY, saved.getSaleDate(),
                "Must NOT be filed under the server's UTC calendar day, which is one day behind at this instant");
    }

    /**
     * The actual Task 3 correctness this all exists for: a scan and a sale
     * at the shop, both at 2:30am Karachi time, must cross-reference onto
     * the SAME calendar day — the QR-required-scanned-today gate in
     * SalesService.submitShopVisit depends on exactly this.
     */
    @Test
    void aTwoAmScanSatisfiesTheSameDayQrRequirementForATwoAmSale() {
        Agent salesman = seedAgent("TZ_CROSS_LMT", "SALESMAN_LMT");
        Product product = seedProduct();
        CustomerShop shop = seedShop("TZ-SHOP-CROSS");
        shop.setQrRequired(true);
        customerShopRepository.save(shop);
        seedCompanyCheckIn(salesman);

        ShopVisitScanRequest scanRequest = new ShopVisitScanRequest();
        scanRequest.setAgentId(salesman.getId());
        scanRequest.setScannedCode(shop.getShopCode());
        scanRequest.setLatitude(0.0);
        scanRequest.setLongitude(0.0);
        var scanResponse = shopVisitScanService.recordScan(scanRequest);
        assertEquals("SUCCESS", scanResponse.getVisitStatus());

        ShopVisitRequest saleRequest = new ShopVisitRequest();
        saleRequest.setAgentId(salesman.getId());
        saleRequest.setShopCode(shop.getShopCode());
        saleRequest.setLatitude(0.0);
        saleRequest.setLongitude(0.0);
        ShopVisitItemRequest item = new ShopVisitItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(1);
        item.setTransactionType("SALE");
        saleRequest.setItems(java.util.List.of(item));

        // Must NOT throw "requires a QR scan before recording sales" —
        // it would, before this fix, if the scan's day and the sale's day
        // were computed in different zones at this exact instant.
        SalesRecord saved = salesService.submitShopVisit(saleRequest);
        assertEquals(EXPECTED_KARACHI_DAY, saved.getSaleDate());
    }
}
