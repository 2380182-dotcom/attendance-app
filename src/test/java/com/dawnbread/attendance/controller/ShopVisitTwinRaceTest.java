package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ShopVisitItemRequest;
import com.dawnbread.attendance.dto.ShopVisitRequest;
import com.dawnbread.attendance.entity.*;
import com.dawnbread.attendance.repository.*;
import com.dawnbread.attendance.security.TenantContext;
import com.dawnbread.attendance.service.SalesService;
import com.dawnbread.attendance.service.ShopVisitIdempotencyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;

/**
 * Deterministic test of the voucher-idempotency race seen in CI on
 * 2026-10-27: a twin of the SAME request (same requestId) commits AFTER this
 * request's requestId check but BEFORE its per-product duplicate check.
 * The first requestId lookup is forced to miss (as it would in that window);
 * every later lookup is real. Before the fix this answered "Duplicate entry";
 * now it must hand back the twin's voucher.
 */
@SpringBootTest
class ShopVisitTwinRaceTest {

    @MockitoSpyBean
    private ShopVisitIdempotencyService idempotency;

    @Autowired
    private SalesService salesService;
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

    private Long tenantId;
    private Agent salesman;
    private CustomerShop shop;

    @BeforeEach
    void seed() {
        tenantId = TenantTestHelper.defaultTenantId(tenantRepository);
        TenantContext.setTenantId(tenantId);

        salesman = new Agent();
        salesman.setTenantId(tenantId);
        salesman.setAgentId("TWIN_" + System.nanoTime());
        salesman.setName("Twin Salesman");
        salesman.setEmail(salesman.getAgentId().toLowerCase() + "@example.com");
        salesman.setRole("SALESMAN_LMT");
        salesman.setCreatedAt(LocalDateTime.now());
        salesman = agentRepository.save(salesman);

        Mart depot = new Mart();
        depot.setTenantId(tenantId);
        depot.setName("Twin Depot " + System.nanoTime());
        depot.setLatitude(0.0);
        depot.setLongitude(0.0);
        depot.setRadius(100.0);
        depot.setGeoFencingEnabled(true);
        depot.setIsActive(true);
        depot.setMartType(MartType.COMPANY);
        depot.setCreatedAt(LocalDateTime.now());
        depot = martRepository.save(depot);
        Attendance checkIn = new Attendance();
        checkIn.setTenantId(tenantId);
        checkIn.setAgent(salesman);
        checkIn.setMart(depot);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setStatus("IN");
        attendanceRepository.save(checkIn);

        Area area = new Area();
        area.setTenantId(tenantId);
        area.setName("Twin Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);
        shop = new CustomerShop();
        shop.setTenantId(tenantId);
        shop.setShopCode("TWIN-" + System.nanoTime());
        shop.setShopName("Twin Shop");
        shop.setArea(area);
        shop.setGeoFencingEnabled(false);
        shop.setIsActive(true);
        shop.setCreatedAt(LocalDateTime.now());
        shop = customerShopRepository.save(shop);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private Product product() {
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setName("Twin Bread " + System.nanoTime());
        p.setAgentPrice(50.0);
        p.setSalesmanPrice(50.0);
        p.setIsActive(true);
        p.setCreatedAt(LocalDateTime.now());
        return productRepository.save(p);
    }

    private ShopVisitRequest request(Product p, String requestId) {
        ShopVisitRequest req = new ShopVisitRequest();
        req.setAgentId(salesman.getId());
        req.setShopCode(shop.getShopCode());
        req.setLatitude(0.0);
        req.setLongitude(0.0);
        req.setRequestId(requestId);
        ShopVisitItemRequest item = new ShopVisitItemRequest();
        item.setProductId(p.getId());
        item.setQuantity(4);
        item.setTransactionType("SALE");
        req.setItems(List.of(item));
        return req;
    }

    @Test
    void twinThatCommittedAfterTheRequestIdCheckGetsTheSameVoucherNotADuplicateError() {
        Product p = product();
        String requestId = "twin-" + System.nanoTime();
        Long winnerId = salesService.submitShopVisit(request(p, requestId)).getId(); // the twin commits first

        // The loser's FIRST requestId lookup ran before the twin committed: it sees nothing.
        doReturn(null).doCallRealMethod().when(idempotency).findByRequestIdOrNull(requestId);

        Long loserId = salesService.submitShopVisit(request(p, requestId)).getId();
        assertEquals(winnerId, loserId, "the retry must get the twin's voucher, not 'Duplicate entry'");
    }

    @Test
    void aGenuinelyDifferentDuplicateStillGetsTheDuplicateError() {
        Product p = product();
        salesService.submitShopVisit(request(p, "first-" + System.nanoTime()));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> salesService.submitShopVisit(request(p, "second-" + System.nanoTime())));
        assertTrue(e.getMessage().startsWith("Duplicate entry"), e.getMessage());

        IllegalArgumentException noId = assertThrows(IllegalArgumentException.class,
                () -> salesService.submitShopVisit(request(p, null)));
        assertTrue(noId.getMessage().startsWith("Duplicate entry"), "old app versions (no requestId) unchanged");
        doCallRealMethod().when(idempotency).findByRequestIdOrNull(org.mockito.ArgumentMatchers.anyString());
    }
}
