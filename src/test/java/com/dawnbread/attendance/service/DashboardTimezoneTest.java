package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.ShopVisitItemRequest;
import com.dawnbread.attendance.dto.ShopVisitRequest;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task 3 audit finding: SalesRecord.saleDate is now correctly written in
 * Karachi time (SalesService), but DashboardService.getRealtimeSalesDashboard
 * still computed "today" via the server's UTC clock — between midnight-5am
 * Pakistan time the live dashboard would have shown yesterday's (correctly-
 * attributed) sales as missing from "today's" revenue. Own file/Spring
 * context with its own fixed clock (a date far from TimezoneAttributionTest's
 * 2026-01-16) specifically because getRealtimeSalesDashboard aggregates
 * ACROSS every agent/shop for the day — sharing a date with another test
 * class's seeded data would make an exact revenue assertion flaky.
 */
@SpringBootTest
@Import(DashboardTimezoneTest.FixedClockConfig.class)
class DashboardTimezoneTest {

    // 2026-03-10T20:00:00Z = 2026-03-11 01:00 Asia/Karachi (UTC+5) — 1am the next calendar day.
    private static final Instant ONE_AM_KARACHI_INSTANT = Instant.parse("2026-03-10T20:00:00Z");
    private static final LocalDate EXPECTED_KARACHI_DAY = LocalDate.of(2026, 3, 11);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testFixedClock() {
            return Clock.fixed(ONE_AM_KARACHI_INSTANT, ZoneId.of("Asia/Karachi"));
        }
    }

    @Autowired private SalesService salesService;
    @Autowired private DashboardService dashboardService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AreaRepository areaRepository;
    @Autowired private CustomerShopRepository customerShopRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private MartRepository martRepository;

    private Long tenantId() {
        return tenantRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setCompanyCode("DASHTZTEST");
                    t.setName("Dashboard TZ Test Tenant");
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

    @Test
    void realtimeSalesDashboardShowsATwoAmKarachiSaleAsTodayNotYesterday() {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId("DASH_TZ_1");
        agent.setName("Dashboard TZ Agent");
        agent.setRole("SALESMAN_LMT");
        agent.setCreatedAt(LocalDateTime.now());
        agent = agentRepository.save(agent);

        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("Dashboard TZ Bread " + System.nanoTime());
        product.setAgentPrice(80.0);
        product.setSalesmanPrice(80.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        product = productRepository.save(product);

        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("Dashboard TZ Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode("DASH-TZ-" + System.nanoTime());
        shop.setShopName("Dashboard TZ Shop");
        shop.setArea(area);
        shop.setGeoFencingEnabled(false);
        shop.setIsActive(true);
        shop.setCreatedAt(LocalDateTime.now());
        shop = customerShopRepository.save(shop);

        Mart companyMart = new Mart();
        companyMart.setTenantId(tenantId());
        companyMart.setName("Dashboard TZ Depot " + System.nanoTime());
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

        ShopVisitRequest request = new ShopVisitRequest();
        request.setAgentId(agent.getId());
        request.setShopCode(shop.getShopCode());
        request.setLatitude(0.0);
        request.setLongitude(0.0);
        ShopVisitItemRequest item = new ShopVisitItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(1);
        item.setTransactionType("SALE");
        request.setItems(List.of(item));

        SalesRecord saved = salesService.submitShopVisit(request);
        assertEquals(EXPECTED_KARACHI_DAY, saved.getSaleDate(), "Sanity check: the sale itself must be Karachi-attributed");

        var todayDashboard = dashboardService.getRealtimeSalesDashboard();

        assertEquals(80.0, todayDashboard.getTodayTotalRevenue(), 0.01,
                "A sale at 1am Karachi time (8pm UTC the previous day) must count toward the live dashboard's "
                        + "\"today\" revenue — using the server's UTC day would have missed it entirely");
    }
}
