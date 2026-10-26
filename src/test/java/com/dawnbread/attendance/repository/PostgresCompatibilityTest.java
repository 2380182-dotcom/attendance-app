package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.dto.cockpit.CockpitFilter;
import com.dawnbread.attendance.entity.*;
import com.dawnbread.attendance.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real production outage (2026-10-01): every optional-search query using
 * the "(:param IS NULL OR LOWER(...) LIKE ...)" pattern crashed on Postgres
 * 15 with "function lower(bytea) does not exist" whenever the param was
 * actually null — Postgres can't resolve the type of an untyped null bound
 * inside LOWER(CONCAT(...)) and defaults to bytea. H2 (every other test in
 * this suite) accepts the untyped null fine and never caught this. This
 * class runs the exact same queries against a REAL Postgres 15 container —
 * the only way to trust this bug class is fixed and stays fixed. Hibernate
 * creates the schema directly here (ddl-auto=create-drop) rather than
 * running Flyway — this test is about JPQL-to-SQL type inference on real
 * Postgres, not about migration script content (which stays untouched,
 * per instruction, now that V26-V30 are live).
 */
@Testcontainers
@SpringBootTest
class PostgresCompatibilityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private CustomerShopRepository customerShopRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    @Autowired
    private ShopVisitScanRepository shopVisitScanRepository;

    @Autowired
    private ShopFeedbackRepository shopFeedbackRepository;

    private Long tenantId;

    @BeforeEach
    void seedTenant() {
        Tenant tenant = new Tenant();
        tenant.setCompanyCode("PGTEST");
        tenant.setName("Postgres Compat Test Co");
        tenant.setIsActive(true);
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setCreatedBy("TEST");
        tenantId = tenantRepository.save(tenant).getId();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private Agent seedAgent(String agentId, String role) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId);
        agent.setAgentId(agentId);
        agent.setName("Findable Agent " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole(role);
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private CustomerShop seedShop(String code, String name) {
        Area area = new Area();
        area.setTenantId(tenantId);
        area.setName("PG Test Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId);
        shop.setShopCode(code);
        shop.setShopName(name);
        shop.setArea(area);
        shop.setIsActive(true);
        shop.setCreatedAt(LocalDateTime.now());
        return customerShopRepository.save(shop);
    }

    private Product seedProduct() {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setName("PG Test Bread " + System.nanoTime());
        product.setAgentPrice(50.0);
        product.setSalesmanPrice(50.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private SalesRecord seedShopVisitRecord(Agent agent, CustomerShop shop, Product product, String role) {
        SalesRecord record = new SalesRecord();
        record.setTenantId(tenantId);
        record.setAgent(agent);
        record.setCustomerShop(shop);
        record.setTotalAmount(100.0);
        record.setSaleDate(LocalDate.now(com.dawnbread.attendance.util.KarachiTime.ZONE));
        record.setSaleTime(LocalTime.now(com.dawnbread.attendance.util.KarachiTime.ZONE));
        record.setCreatedAt(LocalDateTime.now());
        record.setStatus("PENDING");
        record.setAgentRoleAtSale(role);

        SaleItem item = new SaleItem();
        item.setTenantId(tenantId);
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(50.0);
        item.setTotalPrice(100.0);
        item.setAgentId(agent.getId());
        item.setSaleDate(record.getSaleDate());
        item.setCustomerShopId(shop.getId());
        item.setTransactionType(TransactionType.SALE);
        record.addItem(item);

        return salesRecordRepository.save(record);
    }

    // ===== SalesRecordRepository.searchSales =====

    @Test
    void searchSalesWithBlankFiltersDoesNotThrowOnPostgres() {
        Agent agent = seedAgent("PG_SEARCH_1", "AGENT");
        CustomerShop shop = seedShop("PG-SHOP-1", "PG Shop One");
        Product product = seedProduct();
        seedShopVisitRecord(agent, shop, product, "AGENT");

        // The exact failure mode: blank/absent agentName+storeName used to
        // bind an untyped null into "LOWER(CONCAT('%', :param, '%'))" and
        // crash with "function lower(bytea) does not exist" on real
        // Postgres — never on H2, which is why this stayed hidden.
        List<SalesRecord> results = assertDoesNotThrow(
                () -> salesRecordRepository.searchSales("", null, ""));
        assertTrue(results.size() >= 1);
    }

    @Test
    void searchSalesWithRealFiltersMatchesOnPostgres() {
        Agent agent = seedAgent("PG_SEARCH_2", "AGENT");
        CustomerShop shop = seedShop("PG-SHOP-2", "PG Shop Two");
        Product product = seedProduct();
        seedShopVisitRecord(agent, shop, product, "AGENT");

        List<SalesRecord> results = assertDoesNotThrow(
                () -> salesRecordRepository.searchSales("findable", null, ""));
        assertTrue(results.stream().anyMatch(r -> r.getAgent().getId().equals(agent.getId())));
    }

    // ===== SalesRecordRepository.findShopVoucherSummaries =====

    @Test
    void findShopVoucherSummariesWithBlankSearchDoesNotThrowOnPostgres() {
        Agent lmt = seedAgent("PG_VOUCHER_1", "SALESMAN_LMT");
        CustomerShop shop = seedShop("PG-SHOP-3", "PG Shop Three");
        Product product = seedProduct();
        seedShopVisitRecord(lmt, shop, product, "SALESMAN_LMT");

        Page<com.dawnbread.attendance.dto.VoucherShopSummaryDTO> page = assertDoesNotThrow(
                () -> salesRecordRepository.findShopVoucherSummaries("SALESMAN_LMT", "", PageRequest.of(0, 25)));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void findShopVoucherSummariesWithRealSearchMatchesOnPostgres() {
        Agent lmt = seedAgent("PG_VOUCHER_2", "SALESMAN_LMT");
        CustomerShop shop = seedShop("PG-SHOP-4", "Findable Bakery");
        Product product = seedProduct();
        seedShopVisitRecord(lmt, shop, product, "SALESMAN_LMT");

        Page<com.dawnbread.attendance.dto.VoucherShopSummaryDTO> page = assertDoesNotThrow(
                () -> salesRecordRepository.findShopVoucherSummaries("SALESMAN_LMT", "findable", PageRequest.of(0, 25)));
        assertEquals(1, page.getTotalElements());
        assertEquals(shop.getId(), page.getContent().get(0).getShopId());
    }

    // ===== ShopVisitScanRepository.findFiltered =====

    private ShopVisitScan seedScan(Agent agent, CustomerShop shop) {
        ShopVisitScan scan = new ShopVisitScan();
        scan.setTenantId(tenantId);
        scan.setAgentId(agent.getId());
        scan.setCustomerShop(shop);
        scan.setScannedCode(shop.getShopCode());
        scan.setScanDate(LocalDate.now(com.dawnbread.attendance.util.KarachiTime.ZONE));
        scan.setScanTime(LocalTime.now(com.dawnbread.attendance.util.KarachiTime.ZONE));
        scan.setGpsLatitude(0.0);
        scan.setGpsLongitude(0.0);
        scan.setGeofenceStatus(GeofenceStatus.INSIDE);
        scan.setVisitStatus(ShopVisitStatus.SUCCESS);
        scan.setCreatedAt(LocalDateTime.now());
        return shopVisitScanRepository.save(scan);
    }

    @Test
    void findFilteredWithEveryOptionalParamBlankOrNullDoesNotThrowOnPostgres() {
        Agent lmt = seedAgent("PG_SCAN_1", "SALESMAN_LMT");
        CustomerShop shop = seedShop("PG-SHOP-5", "PG Shop Five");
        seedScan(lmt, shop);
        LocalDate today = LocalDate.now(com.dawnbread.attendance.util.KarachiTime.ZONE);

        // role="" and shopSearch="" (the fix), agentId=null (the Long
        // param — confirms the "IS NULL OR ..." pattern is genuinely fine
        // on Postgres for a typed Long, unlike the untyped String case).
        Page<ShopVisitScan> page = assertDoesNotThrow(
                () -> shopVisitScanRepository.findFiltered(today, today, null, "", "", false, PageRequest.of(0, 25)));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void findFilteredWithRealRoleAndSearchMatchesOnPostgres() {
        Agent lmt = seedAgent("PG_SCAN_2", "SALESMAN_LMT");
        CustomerShop shop = seedShop("PG-SHOP-6", "Findable Outlet");
        seedScan(lmt, shop);
        LocalDate today = LocalDate.now(com.dawnbread.attendance.util.KarachiTime.ZONE);

        Page<ShopVisitScan> page = assertDoesNotThrow(
                () -> shopVisitScanRepository.findFiltered(today, today, lmt.getId(), "SALESMAN_LMT", "findable", false, PageRequest.of(0, 25)));
        assertEquals(1, page.getTotalElements());
    }

    // ===== ShopFeedbackRepository.findFiltered ("salesman updates" feature) =====

    @Test
    void shopFeedbackFindFilteredWithBlankRoleDoesNotThrowOnPostgres() {
        Agent lmt = seedAgent("PG_FEEDBACK_1", "SALESMAN_LMT");
        CustomerShop shop = seedShop("PG-SHOP-7", "PG Shop Seven");

        ShopFeedback feedback = new ShopFeedback();
        feedback.setTenantId(tenantId);
        feedback.setAgentId(lmt.getId());
        feedback.setCustomerShop(shop);
        feedback.setReason(ShopFeedbackReason.SHOP_CLOSED);
        feedback.setGpsLatitude(0.0);
        feedback.setGpsLongitude(0.0);
        feedback.setGeofenceStatus(GeofenceStatus.INSIDE);
        feedback.setVisitDate(LocalDate.now(com.dawnbread.attendance.util.KarachiTime.ZONE));
        feedback.setVisitTime(LocalTime.now(com.dawnbread.attendance.util.KarachiTime.ZONE));
        feedback.setCreatedAt(LocalDateTime.now());
        shopFeedbackRepository.save(feedback);

        LocalDate today = LocalDate.now(com.dawnbread.attendance.util.KarachiTime.ZONE);
        // role="" (the Postgres-safe fix) — the exact pattern that crashed
        // production for ShopVisitScanRepository.findFiltered, now reused
        // here for the new feedback query.
        List<ShopFeedback> result = assertDoesNotThrow(
                () -> shopFeedbackRepository.findFiltered(today, today, null, ""));
        assertEquals(1, result.size());
    }

    // ===== Sales cockpit (SalesCockpitQueries) — every query, with and without filters, empty and seeded =====

    @Autowired
    private SalesCockpitQueries cockpitQueries;

    private void runEveryCockpitQuery(CockpitFilter f) {
        LocalDate from = f.startDate();
        LocalDate to = f.endDate();
        assertDoesNotThrow(() -> cockpitQueries.dailySales(f, from, to));
        assertDoesNotThrow(() -> cockpitQueries.salesBySegment(f));
        assertDoesNotThrow(() -> cockpitQueries.salesByAgent(f));
        assertDoesNotThrow(() -> cockpitQueries.salesByArea(f));
        assertDoesNotThrow(() -> cockpitQueries.topProducts(f, 15));
        assertDoesNotThrow(() -> cockpitQueries.shopsAboveReturnPercent(f, 15.0));
        assertDoesNotThrow(() -> cockpitQueries.productsAboveReturnPercent(f, 15.0));
        assertDoesNotThrow(() -> cockpitQueries.vouchers(f, null, false, 0, 25));
        assertDoesNotThrow(() -> cockpitQueries.vouchers(f, 1L, true, 0, 25));
        assertDoesNotThrow(() -> cockpitQueries.countVouchers(f, 1L, false));
        assertDoesNotThrow(() -> cockpitQueries.dailyOffLocationAttempts(f, from, to));
        assertDoesNotThrow(() -> cockpitQueries.offLocationAttemptsByAgent(f, from, to));
        assertDoesNotThrow(() -> cockpitQueries.dailyLocationNotChecked(f, from, to));
        assertDoesNotThrow(() -> cockpitQueries.dailyVouchersWithoutScan(f, from, to));
        assertDoesNotThrow(() -> cockpitQueries.vouchersWithoutScanByAgent(f, from, to));
        assertDoesNotThrow(() -> cockpitQueries.dailyFeedback(f, from, to));
        assertDoesNotThrow(() -> cockpitQueries.dailyLmtMissing(f, from, to));
        assertDoesNotThrow(() -> cockpitQueries.lmtMissingByAgent(f, from, to));
        assertDoesNotThrow(() -> cockpitQueries.unreconciledByAgent(f, from, to));
        assertDoesNotThrow(() -> cockpitQueries.countAssignedShops(f));
        assertDoesNotThrow(() -> cockpitQueries.countVisitedAssignedShops(f, to));
        assertDoesNotThrow(() -> cockpitQueries.countShopsNotVisitedSince(f, to.minusDays(3)));
        assertDoesNotThrow(() -> cockpitQueries.shopsNotVisitedSince(f, to.minusDays(3), 10));
        assertDoesNotThrow(() -> cockpitQueries.countSalesmen(f));
        assertDoesNotThrow(() -> cockpitQueries.countActiveSalesmen(f, to));
        assertDoesNotThrow(() -> cockpitQueries.salesmenWithoutVisit(f, to));
        assertDoesNotThrow(() -> cockpitQueries.activeAreas());
        assertDoesNotThrow(() -> cockpitQueries.activeSalesmen());
    }

    @Test
    void everyCockpitQueryRunsOnPostgresWithAndWithoutFiltersAndWithNoData() {
        // A fixed past date, so this scan never shows up in other tests that count today's scans.
        LocalDate today = LocalDate.of(2025, 1, 15);
        CockpitFilter none = new CockpitFilter(today.minusDays(29), today, "ALL", null, null);
        CockpitFilter all = new CockpitFilter(today.minusDays(29), today, "LMT", 1L, 1L);
        runEveryCockpitQuery(none);   // empty database
        runEveryCockpitQuery(all);

        Agent lmt = seedAgent("PG_COCKPIT_1", "SALESMAN_LMT");
        CustomerShop shop = seedShop("PG-SHOP-CK", "PG Cockpit Shop");
        ShopVisitScan scan = new ShopVisitScan();
        scan.setTenantId(tenantId);
        scan.setAgentId(lmt.getId());
        scan.setCustomerShop(shop);
        scan.setScannedCode("PG-SHOP-CK");
        scan.setScanDate(today);
        scan.setScanTime(LocalTime.now(com.dawnbread.attendance.util.KarachiTime.ZONE));
        scan.setGpsLatitude(0.0);
        scan.setGpsLongitude(0.0);
        scan.setGeofenceStatus(GeofenceStatus.OUTSIDE);
        scan.setVisitStatus(ShopVisitStatus.OUTSIDE_GEOFENCE);
        scan.setCreatedAt(LocalDateTime.now());
        shopVisitScanRepository.save(scan);

        runEveryCockpitQuery(none);   // seeded
        runEveryCockpitQuery(new CockpitFilter(today.minusDays(29), today, "LOCAL", shop.getArea().getId(), lmt.getId()));
        assertEquals(1L, cockpitQueries.dailyOffLocationAttempts(none, today, today).size());
    }

    // ===== V34 absences + V35 holidays =====

    @Autowired
    private AgentAbsenceRepository agentAbsenceRepository;

    @Autowired
    private CompanyHolidayRepository companyHolidayRepository;

    @Test
    void absenceAndHolidayQueriesRunOnPostgres() {
        Agent agent = seedAgent("PG_ABSENT_1", "AGENT");
        LocalDate day = LocalDate.of(2026, 3, 2);
        AgentAbsence absence = new AgentAbsence();
        absence.setTenantId(tenantId);
        absence.setAgentId(agent.getId());
        absence.setAbsenceDate(day);
        absence.setCreatedAt(LocalDateTime.now());
        agentAbsenceRepository.saveAndFlush(absence);

        assertTrue(agentAbsenceRepository.existsByTenantIdAndAgentIdAndAbsenceDate(tenantId, agent.getId(), day));
        assertEquals(1, agentAbsenceRepository.findInRange(day, day).size());
        assertEquals(1, agentAbsenceRepository.findInRangeForAgent(day, day, agent.getId()).size());
        assertDoesNotThrow(() -> companyHolidayRepository.existsByTenantIdAndHolidayDate(tenantId, day));
        assertDoesNotThrow(() -> companyHolidayRepository.findByTenantIdOrderByHolidayDateDesc(tenantId));
    }
}
