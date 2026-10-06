package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.ShopFeedbackRequest;
import com.dawnbread.attendance.dto.ShopFeedbackResponseDTO;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.entity.MartType;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
import com.dawnbread.attendance.repository.MartRepository;
import com.dawnbread.attendance.repository.SalesRecordRepository;
import com.dawnbread.attendance.repository.ShopFeedbackRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Salesman updates" feature: a salesman reached a shop but couldn't make
 * a sale, so he records feedback instead — no QR scan, no voucher, but the
 * same GPS/geofence and company check-in rules as a real visit.
 */
@SpringBootTest
@org.springframework.transaction.annotation.Transactional
class ShopFeedbackServiceTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private CustomerShopRepository customerShopRepository;

    @Autowired
    private MartRepository martRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    @Autowired
    private ShopFeedbackRepository shopFeedbackRepository;

    @Autowired
    private ShopFeedbackService shopFeedbackService;

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

    /** lat 24.86 / lon 67.0, radius 50m — matches ShopVisitScanTest's coordinates. */
    private CustomerShop seedShop() {
        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("Feedback Test Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode("FB-" + System.nanoTime());
        shop.setShopName("Feedback Test Shop");
        shop.setArea(area);
        shop.setGeoFencingEnabled(true);
        shop.setLatitude(24.86);
        shop.setLongitude(67.0);
        shop.setRadius(50.0);
        shop.setIsActive(true);
        shop.setCreatedAt(LocalDateTime.now());
        return customerShopRepository.save(shop);
    }

    private void seedCompanyCheckIn(Agent agent) {
        Mart companyMart = new Mart();
        companyMart.setTenantId(tenantId());
        companyMart.setName("Feedback Test Depot " + System.nanoTime());
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

    private ShopFeedbackRequest request(Long agentId, String shopCode, String reason, String note,
                                         double lat, double lon, String requestId) {
        ShopFeedbackRequest req = new ShopFeedbackRequest();
        req.setAgentId(agentId);
        req.setShopCode(shopCode);
        req.setReason(reason);
        req.setNote(note);
        req.setLatitude(lat);
        req.setLongitude(lon);
        req.setRequestId(requestId);
        return req;
    }

    @Test
    void feedbackIsSavedAndCreatesNoVoucher() {
        Agent lmt = seedSalesman("FB_SAVE_1", "SALESMAN_LMT");
        seedCompanyCheckIn(lmt);
        CustomerShop shop = seedShop();

        ShopFeedbackResponseDTO result = shopFeedbackService.recordFeedback(
                request(lmt.getId(), shop.getShopCode(), "SHOP_CLOSED", null, 24.86, 67.0, null));

        assertEquals(shop.getShopCode(), result.getShopCode());
        assertEquals("SHOP_CLOSED", result.getReason());
        assertTrue(salesRecordRepository.findByAgentIdAndSaleDate(lmt.getId(), LocalDate.now()).isEmpty(),
                "Feedback must never create a SalesRecord/voucher");
    }

    @Test
    void otherReasonRequiresANote() {
        Agent lmt = seedSalesman("FB_OTHER_1", "SALESMAN_LMT");
        seedCompanyCheckIn(lmt);
        CustomerShop shop = seedShop();

        assertThrows(IllegalArgumentException.class, () -> shopFeedbackService.recordFeedback(
                request(lmt.getId(), shop.getShopCode(), "OTHER", null, 24.86, 67.0, null)));

        ShopFeedbackResponseDTO result = shopFeedbackService.recordFeedback(
                request(lmt.getId(), shop.getShopCode(), "OTHER", "Shop was being renovated", 24.86, 67.0, null));
        assertEquals("Shop was being renovated", result.getNote());
    }

    @Test
    void feedbackIsBlockedByTheSameGeofenceGateAsARealVisit() {
        Agent lmt = seedSalesman("FB_GEOFENCE_1", "SALESMAN_LMT");
        seedCompanyCheckIn(lmt);
        CustomerShop shop = seedShop();

        // ~1.1km away — same "too far" coordinates established in ShopVisitScanTest/LocalSalesmanTest.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> shopFeedbackService.recordFeedback(
                request(lmt.getId(), shop.getShopCode(), "SHOP_CLOSED", null, 24.87, 67.0, null)));
        assertTrue(ex.getMessage().contains("Too far"), "The geofence gate must block feedback the same way it blocks a real visit: " + ex.getMessage());
    }

    @Test
    void localSalesmanNeedsNoCompanyCheckInForFeedback() {
        Agent local = seedSalesman("FB_LOCAL_1", "SALESMAN_LOCAL");
        // No company check-in — SALESMAN_LOCAL has no duty concept, same as submitShopVisit.
        CustomerShop shop = seedShop();

        ShopFeedbackResponseDTO result = shopFeedbackService.recordFeedback(
                request(local.getId(), shop.getShopCode(), "STOCK_FULL", null, 24.86, 67.0, null));

        assertEquals("STOCK_FULL", result.getReason());
    }

    @Test
    void lmtWithoutCompanyCheckInIsBlocked() {
        Agent lmt = seedSalesman("FB_NOCHECKIN_1", "SALESMAN_LMT");
        // Deliberately no check-in.
        CustomerShop shop = seedShop();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> shopFeedbackService.recordFeedback(
                request(lmt.getId(), shop.getShopCode(), "SHOP_CLOSED", null, 24.86, 67.0, null)));
        assertTrue(ex.getMessage().contains("check in"), ex.getMessage());
    }

    /** Same idempotency protection as SalesService.submitShopVisit's requestId. */
    @Test
    void sameRequestIdReturnsTheSameFeedbackWithoutSavingTwice() {
        Agent lmt = seedSalesman("FB_IDEMPOTENT_1", "SALESMAN_LMT");
        seedCompanyCheckIn(lmt);
        CustomerShop shop = seedShop();
        String requestId = "fb-req-" + System.nanoTime();

        ShopFeedbackResponseDTO first = shopFeedbackService.recordFeedback(
                request(lmt.getId(), shop.getShopCode(), "OWNER_NOT_AVAILABLE", null, 24.86, 67.0, requestId));
        ShopFeedbackResponseDTO second = shopFeedbackService.recordFeedback(
                request(lmt.getId(), shop.getShopCode(), "OWNER_NOT_AVAILABLE", null, 24.86, 67.0, requestId));

        assertEquals(first.getId(), second.getId(), "A reused requestId must return the SAME saved feedback, not create a second one");
        assertEquals(1, shopFeedbackRepository.findShopIdsForAgentAndDate(lmt.getId(), LocalDate.now()).size());
    }

    @Test
    void feedbackMarksTheShopAsVisitedForNotVisitedPurposes() {
        Agent lmt = seedSalesman("FB_VISITED_1", "SALESMAN_LMT");
        seedCompanyCheckIn(lmt);
        CustomerShop shop = seedShop();
        shop.setAssignedAgent(lmt);
        customerShopRepository.save(shop);

        shopFeedbackService.recordFeedback(
                request(lmt.getId(), shop.getShopCode(), "SHOP_CLOSED", null, 24.86, 67.0, null));

        assertTrue(shopFeedbackRepository.findShopIdsForAgentAndDate(lmt.getId(), LocalDate.now()).contains(shop.getId()),
                "The shop must show up as feedback-visited for this agent/date, which ShopVisitScanService unions into its Not-Visited check");
    }
}
