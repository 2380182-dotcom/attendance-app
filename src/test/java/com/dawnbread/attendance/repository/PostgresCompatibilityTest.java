package com.dawnbread.attendance.repository;

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
        record.setSaleDate(LocalDate.now());
        record.setSaleTime(LocalTime.now());
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
        scan.setScanDate(LocalDate.now());
        scan.setScanTime(LocalTime.now());
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
        LocalDate today = LocalDate.now();

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
        LocalDate today = LocalDate.now();

        Page<ShopVisitScan> page = assertDoesNotThrow(
                () -> shopVisitScanRepository.findFiltered(today, today, lmt.getId(), "SALESMAN_LMT", "findable", false, PageRequest.of(0, 25)));
        assertEquals(1, page.getTotalElements());
    }
}
