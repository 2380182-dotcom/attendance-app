package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.LmtDailyStockDTO;
import com.dawnbread.attendance.dto.LmtDailyStockItemDTO;
import com.dawnbread.attendance.dto.LmtMorningStockRequest;
import com.dawnbread.attendance.dto.LmtReconcileItemRequest;
import com.dawnbread.attendance.dto.LmtReconcileRequest;
import com.dawnbread.attendance.dto.LmtStockItemRequest;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.SaleItem;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
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
        product.setPrice(50.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    /** A SALE row for the given agent/product/date/shop — mirrors a real shop-visit sale. */
    private void seedSale(Agent agent, Product product, LocalDate saleDate, Long shopId, int quantity) {
        SalesRecord record = new SalesRecord();
        record.setTenantId(tenantId());
        record.setAgent(agent);
        record.setStoreName("Test Shop " + shopId);
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

        LmtReconcileRequest reconcile = new LmtReconcileRequest();
        reconcile.setAgentId(lmt.getId());
        LmtReconcileItemRequest reconcileItem = new LmtReconcileItemRequest();
        reconcileItem.setProductId(product.getId());
        reconcileItem.setReturnedQty(5);
        reconcileItem.setUnsoldQty(10);
        reconcile.setItems(List.of(reconcileItem));

        LmtDailyStockDTO result = lmtStockService.reconcile(reconcile);
        LmtDailyStockItemDTO item = itemFor(result, product.getId());

        assertEquals(30, item.getSoldQty(), "Sold must be the sum across both shops (10 + 20), not double-counted or missed");
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

        // No SaleItem rows seeded at all for this product/day.

        LmtReconcileRequest reconcile = new LmtReconcileRequest();
        reconcile.setAgentId(lmt.getId());
        LmtReconcileItemRequest reconcileItem = new LmtReconcileItemRequest();
        reconcileItem.setProductId(product.getId());
        reconcileItem.setReturnedQty(5);
        reconcileItem.setUnsoldQty(15);
        reconcile.setItems(List.of(reconcileItem));

        LmtDailyStockDTO result = lmtStockService.reconcile(reconcile);
        LmtDailyStockItemDTO item = itemFor(result, product.getId());

        assertEquals(0, item.getSoldQty(), "A product with no SaleItem rows must compute Sold as 0, not null or an error");
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
        reconcileItem.setReturnedQty(0);
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
}
