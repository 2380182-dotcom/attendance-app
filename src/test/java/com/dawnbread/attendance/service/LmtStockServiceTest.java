package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.LmtDailyStockDTO;
import com.dawnbread.attendance.dto.LmtDailyStockItemDTO;
import com.dawnbread.attendance.dto.LmtMorningStockRequest;
import com.dawnbread.attendance.dto.LmtReconcileItemRequest;
import com.dawnbread.attendance.dto.LmtReconcileRequest;
import com.dawnbread.attendance.dto.LmtStockItemRequest;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.SaleItem;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.entity.TransactionType;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.SaleItemRepository;
import com.dawnbread.attendance.repository.SalesRecordRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LMT Phase C — proves the Sold/Missing computation in
 * LmtStockService.reconcile() against the exact edge cases flagged during
 * planning: summing across multiple shops for the same product/day (must
 * not double-count, must not under-count), a product with zero sales, and
 * Missing going negative when Sold exceeds the declared opening stock
 * (must be recorded, never rejected).
 */
@SpringBootTest
class LmtStockServiceTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    @Autowired
    private SaleItemRepository saleItemRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private CustomerShopRepository customerShopRepository;

    @Autowired
    private LmtStockService lmtStockService;

    // TenantContext is a ThreadLocal normally set by SecurityInterceptor
    // per HTTP request — calling the service directly here bypasses that,
    // so the test sets it itself, same pattern as ShopVisitTest's
    // concurrency test.
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

    private Product seedProduct() {
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("LMT Stock Test Bread " + System.nanoTime());
        product.setAgentPrice(50.0);
        product.setSalesmanPrice(50.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    /** A SALE row for the given agent/product/date/shop — mirrors a real shop-visit sale. */
    private void seedSale(Agent agent, Product product, LocalDate saleDate, Long shopId, int quantity) {
        seedSaleItem(agent, product, saleDate, shopId, quantity, TransactionType.SALE);
    }

    /** A RETURN row — mirrors the per-shop RETURN line the shop-visit flow now also submits. */
    private void seedReturn(Agent agent, Product product, LocalDate saleDate, Long shopId, int quantity) {
        seedSaleItem(agent, product, saleDate, shopId, quantity, TransactionType.RETURN, null);
    }

    /**
     * A RETURN row backed by a REAL CustomerShop entity attached to the
     * parent SalesRecord — required for the per-shop breakdown query,
     * which joins through SalesRecord.customerShop (SaleItem's own
     * customerShopId is just a denormalized Long, no FK — see SaleItem's
     * field comment). Returns the created shop so tests can assert on its id.
     */
    private CustomerShop seedReturnAtShop(Agent agent, Product product, LocalDate saleDate, int quantity) {
        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("LMT Stock Test Area " + System.nanoTime());
        area.setIsActive(true);
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode("LMTSTOCK_" + System.nanoTime());
        shop.setShopName("LMT Stock Test Shop");
        shop.setArea(area);
        shop.setIsActive(true);
        shop.setCreatedAt(LocalDateTime.now());
        shop = customerShopRepository.save(shop);

        seedSaleItem(agent, product, saleDate, shop.getId(), quantity, TransactionType.RETURN, shop);
        return shop;
    }

    private void seedSaleItem(Agent agent, Product product, LocalDate saleDate, Long shopId, int quantity, TransactionType type) {
        seedSaleItem(agent, product, saleDate, shopId, quantity, type, null);
    }

    private void seedSaleItem(Agent agent, Product product, LocalDate saleDate, Long shopId, int quantity,
                               TransactionType type, CustomerShop customerShop) {
        SalesRecord record = new SalesRecord();
        record.setTenantId(tenantId());
        record.setAgent(agent);
        record.setStoreName("Test Shop " + shopId);
        record.setCustomerShop(customerShop);
        record.setTotalAmount(quantity * 50.0);
        record.setTotalUnits(quantity);
        record.setSaleDate(saleDate);
        record.setSaleTime(LocalTime.now());
        record.setSubmittedAt(LocalDateTime.now());
        record.setLocation("Test Location");
        record.setCreatedAt(LocalDateTime.now());
        record = salesRecordRepository.save(record);

        SaleItem item = new SaleItem(product, quantity, 50.0, quantity * 50.0, null);
        item.setTenantId(tenantId());
        item.setSalesRecord(record);
        item.setAgentId(agent.getId());
        item.setSaleDate(saleDate);
        item.setCustomerShopId(shopId);
        item.setTransactionType(type);
        saleItemRepository.saveAndFlush(item);
    }

    private LmtDailyStockItemDTO itemFor(LmtDailyStockDTO dto, Long productId) {
        return dto.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void soldIsSummedAcrossMultipleShopsForTheSameProductAndDay() {
        Agent lmt = seedLmt("LMT_STOCK_MULTISHOP");
        Product product = seedProduct();
        LocalDate today = LocalDate.now();

        LmtMorningStockRequest morning = new LmtMorningStockRequest();
        morning.setAgentId(lmt.getId());
        LmtStockItemRequest morningItem = new LmtStockItemRequest();
        morningItem.setProductId(product.getId());
        morningItem.setOpeningStock(50);
        morning.setItems(List.of(morningItem));
        lmtStockService.enterMorningStock(morning);

        // Same product sold at two different shops the same day — two
        // separate SaleItem rows (distinct customer_shop_id keeps each row
        // legal under the V19 unique index), must sum to 30, not collide
        // or overwrite each other.
        seedSale(lmt, product, today, 201L, 10);
        seedSale(lmt, product, today, 202L, 20);
        // Returned is also per-shop now — one RETURN row at a third shop.
        seedReturn(lmt, product, today, 203L, 5);

        LmtReconcileRequest reconcile = new LmtReconcileRequest();
        reconcile.setAgentId(lmt.getId());
        LmtReconcileItemRequest reconcileItem = new LmtReconcileItemRequest();
        reconcileItem.setProductId(product.getId());
        reconcileItem.setUnsoldQty(10);
        reconcile.setItems(List.of(reconcileItem));

        LmtDailyStockDTO result = lmtStockService.reconcile(reconcile);
        LmtDailyStockItemDTO item = itemFor(result, product.getId());

        assertEquals(30, item.getSoldQty(), "Sold must be the sum across both shops (10 + 20), not double-counted or missed");
        assertEquals(5, item.getReturnedQty(), "Returned must be computed from the per-shop RETURN row, not entered manually");
        assertEquals(5, item.getMissingQty(), "Missing = 50 opening - 30 sold - 5 returned - 10 unsold = 5");
        assertEquals("RECONCILED", result.getStatus());
    }

    @Test
    void zeroSalesProductComputesMissingFromOpeningStockAlone() {
        Agent lmt = seedLmt("LMT_STOCK_ZEROSALES");
        Product product = seedProduct();

        LmtMorningStockRequest morning = new LmtMorningStockRequest();
        morning.setAgentId(lmt.getId());
        LmtStockItemRequest morningItem = new LmtStockItemRequest();
        morningItem.setProductId(product.getId());
        morningItem.setOpeningStock(20);
        morning.setItems(List.of(morningItem));
        lmtStockService.enterMorningStock(morning);

        // No SALE rows, but one RETURN row — Returned is computed the same
        // way Sold is, independently.
        seedReturn(lmt, product, LocalDate.now(), 401L, 5);

        LmtReconcileRequest reconcile = new LmtReconcileRequest();
        reconcile.setAgentId(lmt.getId());
        LmtReconcileItemRequest reconcileItem = new LmtReconcileItemRequest();
        reconcileItem.setProductId(product.getId());
        reconcileItem.setUnsoldQty(15);
        reconcile.setItems(List.of(reconcileItem));

        LmtDailyStockDTO result = lmtStockService.reconcile(reconcile);
        LmtDailyStockItemDTO item = itemFor(result, product.getId());

        assertEquals(0, item.getSoldQty(), "A product with no SaleItem SALE rows must compute Sold as 0, not null or an error");
        assertEquals(5, item.getReturnedQty(), "Returned must reflect the seeded RETURN row even with zero Sold");
        assertEquals(0, item.getMissingQty(), "Missing = 20 - 0 sold - 5 returned - 15 unsold = 0");
    }

    @Test
    void missingGoesNegativeWhenSoldExceedsOpeningStockAndIsNotRejected() {
        Agent lmt = seedLmt("LMT_STOCK_OVERSOLD");
        Product product = seedProduct();
        LocalDate today = LocalDate.now();

        LmtMorningStockRequest morning = new LmtMorningStockRequest();
        morning.setAgentId(lmt.getId());
        LmtStockItemRequest morningItem = new LmtStockItemRequest();
        morningItem.setProductId(product.getId());
        morningItem.setOpeningStock(10);
        morning.setItems(List.of(morningItem));
        lmtStockService.enterMorningStock(morning);

        // Sold more than the declared opening stock — e.g. an undeclared
        // restock or an underreported morning count. Must not throw.
        seedSale(lmt, product, today, 301L, 15);

        LmtReconcileRequest reconcile = new LmtReconcileRequest();
        reconcile.setAgentId(lmt.getId());
        LmtReconcileItemRequest reconcileItem = new LmtReconcileItemRequest();
        reconcileItem.setProductId(product.getId());
        reconcileItem.setUnsoldQty(0);
        reconcile.setItems(List.of(reconcileItem));

        LmtDailyStockDTO result = lmtStockService.reconcile(reconcile);
        LmtDailyStockItemDTO item = itemFor(result, product.getId());

        assertEquals(15, item.getSoldQty());
        assertEquals(-5, item.getMissingQty(), "Missing = 10 - 15 sold - 0 - 0 = -5, recorded as a real oversold discrepancy, never clamped or rejected");
    }

    @Test
    void reconciliationDefaultsOmittedProductsToZeroReturnedAndUnsold() {
        Agent lmt = seedLmt("LMT_STOCK_PARTIAL");
        Product product = seedProduct();

        LmtMorningStockRequest morning = new LmtMorningStockRequest();
        morning.setAgentId(lmt.getId());
        LmtStockItemRequest morningItem = new LmtStockItemRequest();
        morningItem.setProductId(product.getId());
        morningItem.setOpeningStock(40);
        morning.setItems(List.of(morningItem));
        lmtStockService.enterMorningStock(morning);

        // Reconciliation request names no products at all — must still
        // complete (recorded, not blocked), defaulting this product's
        // returned/unsold to 0.
        LmtReconcileRequest reconcile = new LmtReconcileRequest();
        reconcile.setAgentId(lmt.getId());
        reconcile.setItems(Collections.emptyList());

        LmtDailyStockDTO result = lmtStockService.reconcile(reconcile);
        LmtDailyStockItemDTO item = itemFor(result, product.getId());

        assertEquals(0, item.getReturnedQty());
        assertEquals(0, item.getUnsoldQty());
        assertEquals(0, item.getSoldQty());
        assertEquals(40, item.getMissingQty(), "Missing = 40 - 0 - 0 - 0 = 40, the entire day unaccounted for");
    }

    @Test
    void enteringMorningStockTwiceForTheSameDayIsRejected() {
        Agent lmt = seedLmt("LMT_STOCK_DUPLICATE");
        Product product = seedProduct();

        LmtMorningStockRequest morning = new LmtMorningStockRequest();
        morning.setAgentId(lmt.getId());
        LmtStockItemRequest morningItem = new LmtStockItemRequest();
        morningItem.setProductId(product.getId());
        morningItem.setOpeningStock(25);
        morning.setItems(List.of(morningItem));
        lmtStockService.enterMorningStock(morning);

        assertThrows(IllegalArgumentException.class, () -> lmtStockService.enterMorningStock(morning),
                "A second morning-stock submission for the same agent/day must be rejected, not overwrite the first");
    }

    @Test
    void getTodayReturnsEmptyWhenNoStockEnteredYet() {
        Agent lmt = seedLmt("LMT_STOCK_NONE_YET");
        Optional<LmtDailyStockDTO> result = lmtStockService.getToday(lmt.getId());
        assertTrue(result.isEmpty());
    }

    // ===== Phase D (C6): reconciliation report =====

    @Test
    void reconciliationReportIncludesBothOpenAndReconciledRowsForTheDateRange() {
        Agent lmt = seedLmt("LMT_STOCK_REPORT_MIXED");
        Product openProduct = seedProduct();
        Product reconciledProduct = seedProduct();

        // One LMT, two separate agents so each gets its own LmtDailyStock —
        // one left OPEN, one fully reconciled.
        Agent lmtOpen = seedLmt("LMT_STOCK_REPORT_OPEN");
        LmtMorningStockRequest openMorning = new LmtMorningStockRequest();
        openMorning.setAgentId(lmtOpen.getId());
        LmtStockItemRequest openItem = new LmtStockItemRequest();
        openItem.setProductId(openProduct.getId());
        openItem.setOpeningStock(10);
        openMorning.setItems(List.of(openItem));
        lmtStockService.enterMorningStock(openMorning);

        LmtMorningStockRequest reconciledMorning = new LmtMorningStockRequest();
        reconciledMorning.setAgentId(lmt.getId());
        LmtStockItemRequest reconciledItem = new LmtStockItemRequest();
        reconciledItem.setProductId(reconciledProduct.getId());
        reconciledItem.setOpeningStock(20);
        reconciledMorning.setItems(List.of(reconciledItem));
        lmtStockService.enterMorningStock(reconciledMorning);

        LmtReconcileRequest reconcile = new LmtReconcileRequest();
        reconcile.setAgentId(lmt.getId());
        reconcile.setItems(Collections.emptyList());
        lmtStockService.reconcile(reconcile);

        LocalDate today = LocalDate.now();
        List<LmtDailyStockDTO> report = lmtStockService.getReconciliationReport(today, today, null);

        assertTrue(report.stream().anyMatch(r -> r.getAgentId().equals(lmtOpen.getId()) && "OPEN".equals(r.getStatus())),
                "The never-reconciled LMT's stock must appear in the report, still flagged OPEN — not hidden");
        assertTrue(report.stream().anyMatch(r -> r.getAgentId().equals(lmt.getId()) && "RECONCILED".equals(r.getStatus())),
                "The reconciled LMT's stock must appear in the report as RECONCILED");
    }

    @Test
    void reconciliationReportFiltersToOneAgentWhenGiven() {
        Agent lmtA = seedLmt("LMT_STOCK_REPORT_FILTER_A");
        Agent lmtB = seedLmt("LMT_STOCK_REPORT_FILTER_B");
        Product product = seedProduct();

        for (Agent lmt : List.of(lmtA, lmtB)) {
            LmtMorningStockRequest morning = new LmtMorningStockRequest();
            morning.setAgentId(lmt.getId());
            LmtStockItemRequest item = new LmtStockItemRequest();
            item.setProductId(product.getId());
            item.setOpeningStock(15);
            morning.setItems(List.of(item));
            lmtStockService.enterMorningStock(morning);
        }

        LocalDate today = LocalDate.now();
        List<LmtDailyStockDTO> reportForA = lmtStockService.getReconciliationReport(today, today, lmtA.getId());

        assertTrue(reportForA.stream().allMatch(r -> r.getAgentId().equals(lmtA.getId())),
                "Filtering by agentId must exclude every other LMT's rows");
        assertTrue(reportForA.stream().anyMatch(r -> r.getAgentId().equals(lmtA.getId())),
                "Filtering by agentId must still include that agent's own row");
    }

    // ===== LMT flow refinement: Returned is per-shop, not a manual day-level entry =====

    @Test
    void returnedIsSummedAcrossMultipleShopsForTheSameProductAndDay() {
        Agent lmt = seedLmt("LMT_STOCK_RETURN_MULTISHOP");
        Product product = seedProduct();
        LocalDate today = LocalDate.now();

        LmtMorningStockRequest morning = new LmtMorningStockRequest();
        morning.setAgentId(lmt.getId());
        LmtStockItemRequest morningItem = new LmtStockItemRequest();
        morningItem.setProductId(product.getId());
        morningItem.setOpeningStock(50);
        morning.setItems(List.of(morningItem));
        lmtStockService.enterMorningStock(morning);

        // Same product returned at two different shops — must sum, exactly
        // mirroring Sold's own multi-shop aggregation safety.
        seedReturn(lmt, product, today, 501L, 3);
        seedReturn(lmt, product, today, 502L, 4);

        LmtReconcileRequest reconcile = new LmtReconcileRequest();
        reconcile.setAgentId(lmt.getId());
        LmtReconcileItemRequest reconcileItem = new LmtReconcileItemRequest();
        reconcileItem.setProductId(product.getId());
        reconcileItem.setUnsoldQty(0);
        reconcile.setItems(List.of(reconcileItem));

        LmtDailyStockDTO result = lmtStockService.reconcile(reconcile);
        LmtDailyStockItemDTO item = itemFor(result, product.getId());

        assertEquals(7, item.getReturnedQty(), "Returned must sum across both shops (3 + 4), not double-count or miss either");
    }

    @Test
    void reconciliationReportBreaksReturnsDownByShop() {
        Agent lmt = seedLmt("LMT_STOCK_RETURN_BYSHOP");
        Product product = seedProduct();
        LocalDate today = LocalDate.now();

        LmtMorningStockRequest morning = new LmtMorningStockRequest();
        morning.setAgentId(lmt.getId());
        LmtStockItemRequest morningItem = new LmtStockItemRequest();
        morningItem.setProductId(product.getId());
        morningItem.setOpeningStock(30);
        morning.setItems(List.of(morningItem));
        lmtStockService.enterMorningStock(morning);

        CustomerShop shopA = seedReturnAtShop(lmt, product, today, 2);
        CustomerShop shopB = seedReturnAtShop(lmt, product, today, 6);

        LmtReconcileRequest reconcile = new LmtReconcileRequest();
        reconcile.setAgentId(lmt.getId());
        LmtReconcileItemRequest reconcileItem = new LmtReconcileItemRequest();
        reconcileItem.setProductId(product.getId());
        reconcileItem.setUnsoldQty(0);
        reconcile.setItems(List.of(reconcileItem));
        lmtStockService.reconcile(reconcile);

        List<LmtDailyStockDTO> report = lmtStockService.getReconciliationReport(today, today, lmt.getId());
        LmtDailyStockItemDTO item = itemFor(report.get(0), product.getId());

        assertEquals(2, item.getReturnsByShop().size(), "Both shops' RETURN rows must appear in the breakdown");
        assertTrue(item.getReturnsByShop().stream().anyMatch(s -> s.getShopId().equals(shopA.getId()) && s.getReturnedQty() == 2));
        assertTrue(item.getReturnsByShop().stream().anyMatch(s -> s.getShopId().equals(shopB.getId()) && s.getReturnedQty() == 6));
    }

    @Test
    void selfServiceGetTodayNeverIncludesShopBreakdown() {
        Agent lmt = seedLmt("LMT_STOCK_NO_SHOP_BREAKDOWN");
        Product product = seedProduct();

        LmtMorningStockRequest morning = new LmtMorningStockRequest();
        morning.setAgentId(lmt.getId());
        LmtStockItemRequest morningItem = new LmtStockItemRequest();
        morningItem.setProductId(product.getId());
        morningItem.setOpeningStock(10);
        morning.setItems(List.of(morningItem));
        lmtStockService.enterMorningStock(morning);

        LmtDailyStockDTO today = lmtStockService.getToday(lmt.getId()).orElseThrow();
        LmtDailyStockItemDTO item = itemFor(today, product.getId());

        assertTrue(item.getReturnsByShop() == null || item.getReturnsByShop().isEmpty(),
                "The LMT's own self-service getToday() must never carry the per-shop breakdown — that's report-only, attached by the controller/service report path, not this one");
    }
}
