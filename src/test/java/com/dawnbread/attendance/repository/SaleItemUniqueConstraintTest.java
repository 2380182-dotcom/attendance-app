package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.SaleItem;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.entity.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Proves the ux_sale_items_agent_product_date DB constraint (audit Finding
 * 09) actually rejects a duplicate at the persistence layer — independent of
 * SalesService's in-memory pre-check, which only catches sequential
 * duplicates and cannot stop two concurrent requests racing past it before
 * either commits. Two SaleItems attached to two different parent
 * SalesRecords (simulating two separate submissions), same
 * agent/product/date: the second save must fail on the DB constraint, not
 * merely on the in-memory check this test never exercises.
 */
@SpringBootTest
class SaleItemUniqueConstraintTest {

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

    private Agent seedAgent(String agentId) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole("AGENT");
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private Product seedProduct() {
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("Constraint Test Bread " + System.nanoTime());
        product.setPrice(50.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private SalesRecord seedSalesRecord(Agent agent, LocalDate saleDate) {
        SalesRecord record = new SalesRecord();
        record.setTenantId(tenantId());
        record.setAgent(agent);
        record.setStoreName("Test Store");
        record.setTotalAmount(100.0);
        record.setTotalUnits(2);
        record.setSaleDate(saleDate);
        record.setSaleTime(LocalTime.now());
        record.setSubmittedAt(LocalDateTime.now());
        record.setLocation("Test Location");
        record.setCreatedAt(LocalDateTime.now());
        return salesRecordRepository.save(record);
    }

    private SaleItem buildSaleItem(SalesRecord parent, Product product, Long agentId, LocalDate saleDate) {
        SaleItem item = new SaleItem(product, 2, 50.0, 100.0, null);
        item.setTenantId(tenantId());
        item.setSalesRecord(parent);
        item.setAgentId(agentId);
        item.setSaleDate(saleDate);
        return item;
    }

    @Test
    void secondSaleItemForSameAgentProductAndDateIsRejectedByTheDbConstraint() {
        Agent agent = seedAgent("SALEITEM_UNIQ_1");
        Product product = seedProduct();
        LocalDate saleDate = LocalDate.now();

        SalesRecord firstSubmission = seedSalesRecord(agent, saleDate);
        SaleItem firstItem = buildSaleItem(firstSubmission, product, agent.getId(), saleDate);
        assertDoesNotThrow(() -> saleItemRepository.saveAndFlush(firstItem),
                "The first submission for this agent/product/date must succeed");

        // A second, independent submission (its own parent SalesRecord) for the
        // exact same agent + product + date — as would happen if two concurrent
        // requests both passed SalesService's in-memory duplicate check.
        SalesRecord secondSubmission = seedSalesRecord(agent, saleDate);
        SaleItem secondItem = buildSaleItem(secondSubmission, product, agent.getId(), saleDate);
        assertThrows(DataIntegrityViolationException.class,
                () -> saleItemRepository.saveAndFlush(secondItem),
                "A second sale of the same product by the same agent on the same date must violate ux_sale_items_agent_product_date");
    }

    @Test
    void sameAgentAndProductOnADifferentDateIsAllowed() {
        Agent agent = seedAgent("SALEITEM_UNIQ_2");
        Product product = seedProduct();

        SalesRecord today = seedSalesRecord(agent, LocalDate.now());
        SaleItem todayItem = buildSaleItem(today, product, agent.getId(), LocalDate.now());
        assertDoesNotThrow(() -> saleItemRepository.saveAndFlush(todayItem));

        SalesRecord yesterday = seedSalesRecord(agent, LocalDate.now().minusDays(1));
        SaleItem yesterdayItem = buildSaleItem(yesterday, product, agent.getId(), LocalDate.now().minusDays(1));
        assertDoesNotThrow(() -> saleItemRepository.saveAndFlush(yesterdayItem),
                "The same agent/product combination on a different date must not collide with the constraint");
    }
}
