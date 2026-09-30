package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.ShopVisitDaySummaryDTO;
import com.dawnbread.attendance.dto.ShopVisitItemRequest;
import com.dawnbread.attendance.dto.ShopVisitRequest;
import com.dawnbread.attendance.dto.ShopVisitScanRecordDTO;
import com.dawnbread.attendance.dto.ShopVisitScanRequest;
import com.dawnbread.attendance.dto.ShopVisitScanResponseDTO;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.GeofenceStatus;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.entity.MartType;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.entity.ShopVisitScan;
import com.dawnbread.attendance.entity.ShopVisitStatus;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
import com.dawnbread.attendance.repository.MartRepository;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.ShopVisitScanRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QR shop-visit flow (Q1): per-shop QR requirement enforced in
 * submitShopVisit, the tenant-wide master overrides (geofenceMode/qrMode)
 * resolved by ShopRequirementService, and ShopVisitScanService.recordScan
 * itself. A shop near 24.86/67.0 with a 50m radius is "inside"; ~1.1km
 * north (24.87) is "outside" — the same coordinates LocalSalesmanTest uses.
 */
@SpringBootTest
@org.springframework.transaction.annotation.Transactional
class ShopVisitScanTest {

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
    private ShopVisitScanRepository shopVisitScanRepository;

    @Autowired
    private SalesService salesService;

    @Autowired
    private ShopVisitScanService shopVisitScanService;

    @Autowired
    private LmtSettingsService lmtSettingsService;

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

    private Product seedProduct() {
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("Scan Test Bread " + System.nanoTime());
        product.setAgentPrice(55.0);
        product.setSalesmanPrice(100.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    /** lat 24.86 / lon 67.0, radius 50m — matches LocalSalesmanTest's coordinates. */
    private CustomerShop seedShop(boolean geoFencingEnabled, boolean qrRequired) {
        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("Scan Test Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode("QR-" + System.nanoTime());
        shop.setShopName("Scan Test Shop");
        shop.setArea(area);
        shop.setGeoFencingEnabled(geoFencingEnabled);
        shop.setLatitude(24.86);
        shop.setLongitude(67.0);
        shop.setRadius(50.0);
        shop.setIsActive(true);
        shop.setQrRequired(qrRequired);
        shop.setCreatedAt(LocalDateTime.now());
        return customerShopRepository.save(shop);
    }

    private void seedCompanyCheckIn(Agent agent) {
        Mart companyMart = new Mart();
        companyMart.setTenantId(tenantId());
        companyMart.setName("Scan Test Depot " + System.nanoTime());
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

    private SalesRecord submitSale(Agent who, CustomerShop shop, Product product, double lat, double lon) {
        ShopVisitRequest request = new ShopVisitRequest();
        request.setAgentId(who.getId());
        request.setShopCode(shop.getShopCode());
        request.setLatitude(lat);
        request.setLongitude(lon);
        ShopVisitItemRequest item = new ShopVisitItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(1);
        item.setTransactionType("SALE");
        request.setItems(List.of(item));
        return salesService.submitShopVisit(request);
    }

    private void seedScan(Agent who, CustomerShop shop, LocalDate date, ShopVisitStatus status) {
        ShopVisitScan scan = new ShopVisitScan();
        scan.setTenantId(tenantId());
        scan.setAgentId(who.getId());
        scan.setCustomerShop(shop);
        scan.setScannedCode(shop.getShopCode());
        scan.setScanDate(date);
        scan.setScanTime(LocalTime.now());
        scan.setGpsLatitude(24.86);
        scan.setGpsLongitude(67.0);
        scan.setGeofenceStatus(GeofenceStatus.INSIDE);
        scan.setVisitStatus(status);
        scan.setCreatedAt(LocalDateTime.now());
        shopVisitScanRepository.save(scan);
    }

    /** A scan with no resolvable shop (INVALID_CODE) — Q2's report must still include it, with null shop fields. */
    private void seedInvalidScan(Agent who, String code, LocalDate date) {
        ShopVisitScan scan = new ShopVisitScan();
        scan.setTenantId(tenantId());
        scan.setAgentId(who.getId());
        scan.setScannedCode(code);
        scan.setScanDate(date);
        scan.setScanTime(LocalTime.now());
        scan.setGpsLatitude(24.86);
        scan.setGpsLongitude(67.0);
        scan.setGeofenceStatus(GeofenceStatus.NOT_EVALUATED);
        scan.setVisitStatus(ShopVisitStatus.INVALID_CODE);
        scan.setCreatedAt(LocalDateTime.now());
        shopVisitScanRepository.save(scan);
    }

    private ShopVisitScanRequest scanRequest(Agent who, String code, double lat, double lon) {
        ShopVisitScanRequest req = new ShopVisitScanRequest();
        req.setAgentId(who.getId());
        req.setScannedCode(code);
        req.setLatitude(lat);
        req.setLongitude(lon);
        return req;
    }

    // ===== Section A: QR-required enforcement in submitShopVisit =====

    @Test
    void shopRequiringQrBlocksSaleWithoutPriorScan() {
        Product product = seedProduct();
        CustomerShop shop = seedShop(true, true);
        Agent lmt = seedLmt("QR_NO_SCAN");
        seedCompanyCheckIn(lmt);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> submitSale(lmt, shop, product, 24.86, 67.0));
        assertTrue(ex.getMessage().contains("requires a QR scan"), ex.getMessage());
    }

    @Test
    void shopRequiringQrAllowsSaleAfterSuccessfulScanToday() {
        Product product = seedProduct();
        CustomerShop shop = seedShop(true, true);
        Agent lmt = seedLmt("QR_SCANNED_TODAY");
        seedCompanyCheckIn(lmt);
        seedScan(lmt, shop, LocalDate.now(), ShopVisitStatus.SUCCESS);

        SalesRecord saved = submitSale(lmt, shop, product, 24.86, 67.0);
        assertEquals(100.0, saved.getTotalAmount(), 0.001);
    }

    @Test
    void aScanByADifferentAgentDoesNotSatisfyTheRequirement() {
        Product product = seedProduct();
        CustomerShop shop = seedShop(true, true);
        Agent lmt = seedLmt("QR_AGENT_A");
        Agent otherLmt = seedLmt("QR_AGENT_B");
        seedCompanyCheckIn(lmt);
        seedScan(otherLmt, shop, LocalDate.now(), ShopVisitStatus.SUCCESS);

        assertThrows(IllegalArgumentException.class, () -> submitSale(lmt, shop, product, 24.86, 67.0));
    }

    @Test
    void aFailedScanDoesNotSatisfyTheRequirement() {
        Product product = seedProduct();
        CustomerShop shop = seedShop(true, true);
        Agent lmt = seedLmt("QR_FAILED_SCAN");
        seedCompanyCheckIn(lmt);
        seedScan(lmt, shop, LocalDate.now(), ShopVisitStatus.OUTSIDE_GEOFENCE);

        assertThrows(IllegalArgumentException.class, () -> submitSale(lmt, shop, product, 24.86, 67.0));
    }

    @Test
    void yesterdaysScanDoesNotSatisfyTodaysRequirement() {
        Product product = seedProduct();
        CustomerShop shop = seedShop(true, true);
        Agent lmt = seedLmt("QR_YESTERDAY");
        seedCompanyCheckIn(lmt);
        seedScan(lmt, shop, LocalDate.now().minusDays(1), ShopVisitStatus.SUCCESS);

        assertThrows(IllegalArgumentException.class, () -> submitSale(lmt, shop, product, 24.86, 67.0));
    }

    @Test
    void shopNotRequiringQrNeedsNoScanAtAll() {
        Product product = seedProduct();
        CustomerShop shop = seedShop(true, false);
        Agent lmt = seedLmt("QR_NOT_REQUIRED");
        seedCompanyCheckIn(lmt);

        assertEquals(100.0, submitSale(lmt, shop, product, 24.86, 67.0).getTotalAmount(), 0.001);
    }

    // ===== Section B: tenant-wide master overrides =====

    @Test
    void globalQrForceOffBypassesAPerShopRequirement() {
        Product product = seedProduct();
        CustomerShop shop = seedShop(true, true);
        Agent lmt = seedLmt("QR_GLOBAL_OFF");
        seedCompanyCheckIn(lmt);
        lmtSettingsService.updateModes(null, "FORCE_OFF");

        assertEquals(100.0, submitSale(lmt, shop, product, 24.86, 67.0).getTotalAmount(), 0.001);
    }

    @Test
    void globalQrForceOnRequiresScanEvenWhenShopDoesNotRequireIt() {
        Product product = seedProduct();
        CustomerShop shop = seedShop(true, false);
        Agent lmt = seedLmt("QR_GLOBAL_ON");
        seedCompanyCheckIn(lmt);
        lmtSettingsService.updateModes(null, "FORCE_ON");

        assertThrows(IllegalArgumentException.class, () -> submitSale(lmt, shop, product, 24.86, 67.0));

        seedScan(lmt, shop, LocalDate.now(), ShopVisitStatus.SUCCESS);
        assertEquals(100.0, submitSale(lmt, shop, product, 24.86, 67.0).getTotalAmount(), 0.001);
    }

    @Test
    void globalGeofenceForceOffBypassesAnIndividuallyOnShop() {
        Product product = seedProduct();
        CustomerShop shop = seedShop(true, false);
        Agent lmt = seedLmt("GEO_GLOBAL_OFF");
        seedCompanyCheckIn(lmt);
        lmtSettingsService.updateModes("FORCE_OFF", null);

        // Far from the shop — would normally be rejected.
        assertEquals(100.0, submitSale(lmt, shop, product, 24.87, 67.0).getTotalAmount(), 0.001);
    }

    @Test
    void globalGeofenceForceOnEnforcesEvenOnAnIndividuallyOffShop() {
        Product product = seedProduct();
        CustomerShop shop = seedShop(false, false); // toggle off, but coordinates ARE configured
        Agent lmt = seedLmt("GEO_GLOBAL_ON");
        seedCompanyCheckIn(lmt);
        lmtSettingsService.updateModes("FORCE_ON", null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> submitSale(lmt, shop, product, 24.87, 67.0));
        assertTrue(ex.getMessage().contains("Too far"), ex.getMessage());

        assertEquals(100.0, submitSale(lmt, shop, product, 24.86, 67.0).getTotalAmount(), 0.001);
    }

    @Test
    void switchingBackToPerShopRestoresEachShopsOwnSetting() {
        Product product = seedProduct();
        CustomerShop shop = seedShop(true, false);
        Agent lmt = seedLmt("GEO_RESTORE");
        seedCompanyCheckIn(lmt);

        lmtSettingsService.updateModes("FORCE_OFF", null);
        assertEquals(100.0, submitSale(lmt, shop, product, 24.87, 67.0).getTotalAmount(), 0.001,
                "far away, but FORCE_OFF bypasses it");

        lmtSettingsService.updateModes("PER_SHOP", null);
        Product product2 = seedProduct();
        assertThrows(IllegalArgumentException.class, () -> submitSale(lmt, shop, product2, 24.87, 67.0),
                "back to PER_SHOP — the shop's own geoFencingEnabled=true is enforced again");
    }

    // ===== Section C: ShopVisitScanService.recordScan =====

    @Test
    void scanningAValidShopInsideGeofenceSucceedsAndIsRecorded() {
        CustomerShop shop = seedShop(true, false);
        Agent lmt = seedLmt("SCAN_SUCCESS");
        seedCompanyCheckIn(lmt);

        ShopVisitScanResponseDTO result = shopVisitScanService.recordScan(scanRequest(lmt, shop.getShopCode(), 24.86, 67.0));

        assertEquals("SUCCESS", result.getVisitStatus());
        assertEquals("INSIDE", result.getGeofenceStatus());
        assertEquals(shop.getId(), result.getShop().getId());
        assertTrue(shopVisitScanRepository.existsByAgentIdAndCustomerShopIdAndScanDateAndVisitStatus(
                lmt.getId(), shop.getId(), LocalDate.now(), ShopVisitStatus.SUCCESS));
    }

    @Test
    void scanningAnInvalidCodeIsRejectedAndRecorded() {
        Agent lmt = seedLmt("SCAN_INVALID");
        seedCompanyCheckIn(lmt);

        ShopVisitScanResponseDTO result = shopVisitScanService.recordScan(scanRequest(lmt, "NO-SUCH-CODE", 24.86, 67.0));

        assertEquals("INVALID_CODE", result.getVisitStatus());
        assertNull(result.getShop());
        assertEquals(1L, shopVisitScanRepository.findByAgentIdAndScanDateOrderByScanTimeDesc(lmt.getId(), LocalDate.now()).size());
    }

    @Test
    void scanningAnInactiveShopIsRejectedAndRecorded() {
        CustomerShop shop = seedShop(true, false);
        shop.setIsActive(false);
        customerShopRepository.save(shop);
        Agent lmt = seedLmt("SCAN_INACTIVE");
        seedCompanyCheckIn(lmt);

        ShopVisitScanResponseDTO result = shopVisitScanService.recordScan(scanRequest(lmt, shop.getShopCode(), 24.86, 67.0));

        assertEquals("SHOP_INACTIVE", result.getVisitStatus());
        assertTrue(result.getMessage().contains("not active"), result.getMessage());
    }

    @Test
    void scanningOutsideGeofenceIsRejectedAndRecorded() {
        CustomerShop shop = seedShop(true, false);
        Agent lmt = seedLmt("SCAN_OUTSIDE");
        seedCompanyCheckIn(lmt);

        ShopVisitScanResponseDTO result = shopVisitScanService.recordScan(scanRequest(lmt, shop.getShopCode(), 24.87, 67.0));

        assertEquals("OUTSIDE_GEOFENCE", result.getVisitStatus());
        assertEquals("OUTSIDE", result.getGeofenceStatus());
        assertTrue(result.getDistanceMeters() > 50);
    }

    @Test
    void localSalesmanScanningWithNoCheckInStillWorks() {
        CustomerShop shop = seedShop(true, false);
        Agent local = seedLocal("SCAN_LOCAL_NO_CHECKIN"); // deliberately no check-in

        ShopVisitScanResponseDTO result = shopVisitScanService.recordScan(scanRequest(local, shop.getShopCode(), 24.86, 67.0));

        assertEquals("SUCCESS", result.getVisitStatus());
    }

    @Test
    void lmtScanningWithoutCheckInThrowsRatherThanRecordingAFailure() {
        CustomerShop shop = seedShop(true, false);
        Agent lmt = seedLmt("SCAN_LMT_NO_CHECKIN"); // deliberately no check-in

        assertThrows(IllegalArgumentException.class,
                () -> shopVisitScanService.recordScan(scanRequest(lmt, shop.getShopCode(), 24.86, 67.0)));
        assertTrue(shopVisitScanRepository.findByAgentIdAndScanDateOrderByScanTimeDesc(lmt.getId(), LocalDate.now()).isEmpty(),
                "a precondition failure (not checked in) is never recorded as a scan attempt");
    }

    @Test
    void distanceAndGeofenceStatusAreRecordedEvenWhenNotRequired() {
        // geofencing OFF for this shop, but it has coordinates — the scan
        // should still report Inside/Outside for the admin's information,
        // just never block the scan on it.
        CustomerShop shop = seedShop(false, false);
        Agent lmt = seedLmt("SCAN_INFO_ONLY");
        seedCompanyCheckIn(lmt);

        ShopVisitScanResponseDTO result = shopVisitScanService.recordScan(scanRequest(lmt, shop.getShopCode(), 24.87, 67.0));

        assertEquals("SUCCESS", result.getVisitStatus(), "not required, so a far-away scan still succeeds");
        assertEquals("OUTSIDE", result.getGeofenceStatus(), "but the true GPS fact is still recorded");
        assertFalse(result.getDistanceMeters() == null);
    }

    // ===== Section D: Q2 admin report =====

    @Test
    void reportReturnsEveryScanAcrossAllSalesmenInTheDateRange() {
        CustomerShop shop = seedShop(true, false);
        Agent lmt = seedLmt("REPORT_LMT");
        Agent local = seedLocal("REPORT_LOCAL");
        LocalDate today = LocalDate.now();
        seedScan(lmt, shop, today, ShopVisitStatus.SUCCESS);
        seedScan(local, shop, today, ShopVisitStatus.OUTSIDE_GEOFENCE);

        List<ShopVisitScanRecordDTO> report = shopVisitScanService.getPagedReport(
                today, today, null, null, null, false, org.springframework.data.domain.PageRequest.of(0, 50)).getContent();

        assertEquals(2, report.size());
        assertTrue(report.stream().anyMatch(r -> r.getAgentId().equals(lmt.getId()) && "SUCCESS".equals(r.getVisitStatus())));
        assertTrue(report.stream().anyMatch(r -> r.getAgentId().equals(local.getId()) && "OUTSIDE_GEOFENCE".equals(r.getVisitStatus())));
    }

    @Test
    void reportCanBeScopedToOneSalesman() {
        CustomerShop shop = seedShop(true, false);
        Agent lmt = seedLmt("REPORT_SCOPED_A");
        Agent other = seedLmt("REPORT_SCOPED_B");
        LocalDate today = LocalDate.now();
        seedScan(lmt, shop, today, ShopVisitStatus.SUCCESS);
        seedScan(other, shop, today, ShopVisitStatus.SUCCESS);

        List<ShopVisitScanRecordDTO> report = shopVisitScanService.getPagedReport(
                today, today, lmt.getId(), null, null, false, org.springframework.data.domain.PageRequest.of(0, 50)).getContent();

        assertEquals(1, report.size());
        assertEquals(lmt.getId(), report.get(0).getAgentId());
    }

    @Test
    void reportIncludesShopDetailsAndParsesTheCityFromTheShopCode() {
        CustomerShop shop = seedShop(true, false); // "QR-<nanos>" — no city prefix by default
        shop.setShopCode("ISB-I14-005");
        customerShopRepository.save(shop);
        Agent lmt = seedLmt("REPORT_CITY");
        LocalDate today = LocalDate.now();
        seedScan(lmt, shop, today, ShopVisitStatus.SUCCESS);

        ShopVisitScanRecordDTO row = shopVisitScanService.getPagedReport(
                today, today, lmt.getId(), null, null, false, org.springframework.data.domain.PageRequest.of(0, 50)).getContent().get(0);

        assertEquals(shop.getId(), row.getShopId());
        assertEquals("ISB-I14-005", row.getShopCode());
        assertEquals(shop.getShopName(), row.getShopName());
        assertEquals("ISB", row.getCity());
    }

    @Test
    void reportShowsAnInvalidCodeScanWithNullShopFieldsRatherThanOmittingIt() {
        Agent lmt = seedLmt("REPORT_INVALID");
        LocalDate today = LocalDate.now();
        seedInvalidScan(lmt, "BOGUS-CODE", today);

        List<ShopVisitScanRecordDTO> report = shopVisitScanService.getPagedReport(
                today, today, lmt.getId(), null, null, false, org.springframework.data.domain.PageRequest.of(0, 50)).getContent();

        assertEquals(1, report.size());
        assertEquals("INVALID_CODE", report.get(0).getVisitStatus());
        assertEquals("BOGUS-CODE", report.get(0).getScannedCode());
        assertNull(report.get(0).getShopId());
        assertNull(report.get(0).getCity());
    }

    @Test
    void reportExcludesScansOutsideTheRequestedDateRange() {
        CustomerShop shop = seedShop(true, false);
        Agent lmt = seedLmt("REPORT_OUT_OF_RANGE");
        seedScan(lmt, shop, LocalDate.now().minusDays(10), ShopVisitStatus.SUCCESS);

        List<ShopVisitScanRecordDTO> report = shopVisitScanService.getPagedReport(
                LocalDate.now(), LocalDate.now(), lmt.getId(), null, null, false, org.springframework.data.domain.PageRequest.of(0, 50)).getContent();

        assertTrue(report.isEmpty());
    }

    @Test
    void daySummaryCountsSuccessfulFailedAndUniqueShopsCorrectly() {
        CustomerShop shopA = seedShop(true, false);
        CustomerShop shopB = seedShop(true, false);
        Agent lmt = seedLmt("SUMMARY_COUNTS");
        LocalDate today = LocalDate.now();
        seedScan(lmt, shopA, today, ShopVisitStatus.SUCCESS);
        seedScan(lmt, shopA, today, ShopVisitStatus.SUCCESS); // same shop again — must not double-count as a second unique shop
        seedScan(lmt, shopB, today, ShopVisitStatus.OUTSIDE_GEOFENCE);
        seedInvalidScan(lmt, "BOGUS", today); // no shop at all — must not count toward unique shops

        ShopVisitDaySummaryDTO summary = shopVisitScanService.getDaySummary(lmt.getId(), today);

        assertEquals(4, summary.getTotalVisits());
        assertEquals(2, summary.getSuccessfulVisits());
        assertEquals(2, summary.getFailedVisits());
        assertEquals(2, summary.getUniqueShopsVisited(), "shopA (visited twice) + shopB = 2 unique shops; the invalid scan has no shop");
        assertEquals(4, summary.getVisits().size());
    }

    @Test
    void daySummaryIsScopedToOneDayOnly() {
        CustomerShop shop = seedShop(true, false);
        Agent lmt = seedLmt("SUMMARY_ONE_DAY");
        seedScan(lmt, shop, LocalDate.now().minusDays(1), ShopVisitStatus.SUCCESS);

        ShopVisitDaySummaryDTO summary = shopVisitScanService.getDaySummary(lmt.getId(), LocalDate.now());

        assertEquals(0, summary.getTotalVisits());
    }

    // ===== Section D: Task 3 — Not Visited (LMT assignment vs Local), summary counts, voucher-without-scan =====

    private void assignShop(CustomerShop shop, Agent agent) {
        shop.setAssignedAgent(agent);
        customerShopRepository.save(shop);
    }

    @Test
    void lmtNotVisitedShowsOnlyThisSalesmansAssignedButUnscannedShops() {
        Agent lmt = seedLmt("NV_LMT_1");
        Agent otherLmt = seedLmt("NV_LMT_2");
        CustomerShop assignedScanned = seedShop(true, false);
        CustomerShop assignedUnscanned = seedShop(true, false);
        CustomerShop notAssignedToThisAgent = seedShop(true, false);
        assignShop(assignedScanned, lmt);
        assignShop(assignedUnscanned, lmt);
        assignShop(notAssignedToThisAgent, otherLmt);
        LocalDate today = LocalDate.now();
        seedScan(lmt, assignedScanned, today, ShopVisitStatus.SUCCESS);

        var page = shopVisitScanService.getNotVisited(today, "SALESMAN_LMT", lmt.getId(), null,
                org.springframework.data.domain.PageRequest.of(0, 50));

        assertEquals(1, page.getTotalElements(), "Only the assigned-but-unscanned shop should appear — not the scanned one, not another salesman's shop");
        assertEquals(assignedUnscanned.getId(), page.getContent().get(0).getShopId());
        assertEquals(lmt.getId(), page.getContent().get(0).getAssignedAgentId());
    }

    @Test
    void lmtNotVisitedAcrossAllSalesmenGroupsCorrectlyPerAgent() {
        Agent lmtA = seedLmt("NV_LMT_ALL_A");
        Agent lmtB = seedLmt("NV_LMT_ALL_B");
        CustomerShop shopA = seedShop(true, false);
        CustomerShop shopB = seedShop(true, false);
        assignShop(shopA, lmtA);
        assignShop(shopB, lmtB);
        LocalDate today = LocalDate.now();
        seedScan(lmtA, shopA, today, ShopVisitStatus.SUCCESS); // A's shop IS scanned
        // B's shop is never scanned

        var page = shopVisitScanService.getNotVisited(today, "SALESMAN_LMT", null, null,
                org.springframework.data.domain.PageRequest.of(0, 50));

        assertEquals(1, page.getTotalElements());
        assertEquals(shopB.getId(), page.getContent().get(0).getShopId());
        assertEquals(lmtB.getId(), page.getContent().get(0).getAssignedAgentId());
    }

    @Test
    void unassignedShopsNeverAppearInLmtNotVisited() {
        Agent lmt = seedLmt("NV_LMT_UNASSIGNED");
        seedShop(true, false); // never assigned to anyone

        var page = shopVisitScanService.getNotVisited(LocalDate.now(), "SALESMAN_LMT", lmt.getId(), null,
                org.springframework.data.domain.PageRequest.of(0, 50));

        assertEquals(0, page.getTotalElements());
    }

    @Test
    void localNotVisitedShowsEveryActiveUnscannedShopRegardlessOfAssignment() {
        Agent local = seedLocal("NV_LOCAL_1");
        CustomerShop scanned = seedShop(true, false);
        CustomerShop unscanned = seedShop(true, false);
        LocalDate today = LocalDate.now();
        seedScan(local, scanned, today, ShopVisitStatus.SUCCESS);

        // Not asserting an exact total: the full suite shares one DB across
        // test classes, so other tests' own active shops for "today" are
        // also legitimately present here. Assert containment instead.
        var page = shopVisitScanService.getNotVisited(today, "SALESMAN_LOCAL", null, null,
                org.springframework.data.domain.PageRequest.of(0, 1000));

        assertTrue(page.getContent().stream().anyMatch(d -> d.getShopId().equals(unscanned.getId())),
                "The unscanned shop must appear in Not Visited");
        assertTrue(page.getContent().stream().noneMatch(d -> d.getShopId().equals(scanned.getId())),
                "The scanned shop must NOT appear in Not Visited");
        var unscannedRow = page.getContent().stream().filter(d -> d.getShopId().equals(unscanned.getId())).findFirst().orElseThrow();
        assertNull(unscannedRow.getAssignedAgentId(), "Local rows are never assigned to anyone, even if the shop happens to have an LMT assignment");
    }

    @Test
    void notVisitedShopSearchFiltersByNameOrCode() {
        Agent local = seedLocal("NV_SEARCH");
        CustomerShop shop = seedShop(true, false);
        shop.setShopName("Findable Bakery");
        customerShopRepository.save(shop);
        seedShop(true, false); // a second, unrelated unscanned shop

        var page = shopVisitScanService.getNotVisited(LocalDate.now(), "SALESMAN_LOCAL", null, "findable",
                org.springframework.data.domain.PageRequest.of(0, 50));

        assertEquals(1, page.getTotalElements());
        assertEquals(shop.getId(), page.getContent().get(0).getShopId());
    }

    @Test
    void notVisitedIsPaginatedCorrectly() {
        // Scoped to one freshly-created LMT salesman's own assignments —
        // unlike the Local report (global, no agentId scoping possible),
        // this is fully isolated from other tests' leftover data in the
        // shared full-suite database, so an exact count is safe here.
        Agent lmt = seedLmt("NV_PAGINATION");
        for (int i = 0; i < 5; i++) {
            assignShop(seedShop(true, false), lmt);
        }

        var firstPage = shopVisitScanService.getNotVisited(LocalDate.now(), "SALESMAN_LMT", lmt.getId(), null,
                org.springframework.data.domain.PageRequest.of(0, 2));

        assertEquals(5, firstPage.getTotalElements());
        assertEquals(2, firstPage.getContent().size());
        assertEquals(3, firstPage.getTotalPages());
    }

    @Test
    void summaryCountsAddUpCorrectlyForLmt() {
        Agent lmt = seedLmt("SUMMARY_COUNTS_LMT");
        CustomerShop visited = seedShop(true, false);
        CustomerShop notVisited = seedShop(true, false);
        assignShop(visited, lmt);
        assignShop(notVisited, lmt);
        LocalDate today = LocalDate.now();
        seedScan(lmt, visited, today, ShopVisitStatus.SUCCESS);

        var summary = shopVisitScanService.getSummaryCounts(today, "SALESMAN_LMT", lmt.getId());

        assertEquals(2, summary.getTotalShops());
        assertEquals(1, summary.getVisitedShops());
        assertEquals(1, summary.getNotVisitedShops());
    }

    @Test
    void voucherWithoutScanDetectsASaleWithNoMatchingSuccessfulScan() {
        Product product = seedProduct();
        Agent lmt = seedLmt("VWS_1");
        CustomerShop shop = seedShop(false, false); // geofence disabled so submitSale doesn't need a real scan/position
        seedCompanyCheckIn(lmt);

        SalesRecord saved = submitSale(lmt, shop, product, 24.86, 67.0);
        // No scan recorded for this agent/shop/day at all.

        // Scoped to this test's own agentId — isolated from other tests'
        // leftover vouchers in the shared full-suite database.
        var page = shopVisitScanService.getVouchersWithoutScan(LocalDate.now(), LocalDate.now(), lmt.getId(), null,
                org.springframework.data.domain.PageRequest.of(0, 50));

        assertEquals(1, page.getTotalElements());
        assertEquals(saved.getId(), page.getContent().get(0).getSalesRecordId());
    }

    @Test
    void voucherWithoutScanExcludesAVoucherThatDoesHaveAMatchingSuccessfulScan() {
        Product product = seedProduct();
        Agent lmt = seedLmt("VWS_2");
        CustomerShop shop = seedShop(false, false);
        seedCompanyCheckIn(lmt);
        LocalDate today = LocalDate.now();
        seedScan(lmt, shop, today, ShopVisitStatus.SUCCESS);

        submitSale(lmt, shop, product, 24.86, 67.0);

        var page = shopVisitScanService.getVouchersWithoutScan(today, today, lmt.getId(), null,
                org.springframework.data.domain.PageRequest.of(0, 50));

        assertEquals(0, page.getTotalElements());
    }

    @Test
    void pagedReportVoucherCreatedColumnReflectsWhetherASaleExistsForThatAgentShopDay() {
        Product product = seedProduct();
        Agent lmt = seedLmt("VC_COLUMN");
        CustomerShop shop = seedShop(false, false);
        seedCompanyCheckIn(lmt);
        LocalDate today = LocalDate.now();
        seedScan(lmt, shop, today, ShopVisitStatus.SUCCESS);
        submitSale(lmt, shop, product, 24.86, 67.0);

        var page = shopVisitScanService.getPagedReport(today, today, lmt.getId(), null, null, false,
                org.springframework.data.domain.PageRequest.of(0, 50));

        assertEquals(1, page.getTotalElements());
        assertTrue(page.getContent().get(0).getVoucherCreated());
    }

    @Test
    void pagedReportFailedOnlyFilterExcludesSuccessfulScans() {
        Agent lmt = seedLmt("FAILED_ONLY");
        CustomerShop shop = seedShop(true, false);
        LocalDate today = LocalDate.now();
        seedScan(lmt, shop, today, ShopVisitStatus.SUCCESS);
        seedScan(lmt, shop, today, ShopVisitStatus.OUTSIDE_GEOFENCE);

        var page = shopVisitScanService.getPagedReport(today, today, lmt.getId(), null, null, true,
                org.springframework.data.domain.PageRequest.of(0, 50));

        assertEquals(1, page.getTotalElements());
        assertEquals("OUTSIDE_GEOFENCE", page.getContent().get(0).getVisitStatus());
    }

    @Test
    void pagedReportRoleFilterScopesToOneRoleOnly() {
        Agent lmt = seedLmt("ROLE_FILTER_LMT");
        Agent local = seedLocal("ROLE_FILTER_LOCAL");
        CustomerShop shop = seedShop(true, false);
        LocalDate today = LocalDate.now();
        seedScan(lmt, shop, today, ShopVisitStatus.SUCCESS);
        seedScan(local, shop, today, ShopVisitStatus.SUCCESS);

        var page = shopVisitScanService.getPagedReport(today, today, null, "SALESMAN_LMT", null, false,
                org.springframework.data.domain.PageRequest.of(0, 50));

        assertEquals(1, page.getTotalElements());
        assertEquals(lmt.getId(), page.getContent().get(0).getAgentId());
    }
}
