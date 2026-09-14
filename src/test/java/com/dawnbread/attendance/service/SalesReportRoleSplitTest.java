package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.ReportDTO;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase D backend: proves the Agent-vs-LMT report split uses Agent.role as
 * the discriminator, is additive (a null role reproduces the exact
 * unfiltered totals as before), and correctly separates same-day sales
 * from an AGENT and a SALESMAN_LMT.
 */
@SpringBootTest
class SalesReportRoleSplitTest {

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
    private SalesService salesService;

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

    private Agent seedAgent(String agentId, String role) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole(role);
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private Product seedProduct() {
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("Role Split Test Bread " + System.nanoTime());
        product.setPrice(50.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private void seedSale(Agent agent, Product product, LocalDate saleDate, int quantity) {
        SalesRecord record = new SalesRecord();
        record.setTenantId(tenantId());
        record.setAgent(agent);
        record.setStoreName("Role Split Test Store");
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
        saleItemRepository.saveAndFlush(item);
    }

    @Test
    void dailyReportSeparatesAgentAndLmtSalesByRole() {
        LocalDate today = LocalDate.now();
        Agent agent = seedAgent("RSPLIT_AGENT_1", "AGENT");
        Agent lmt = seedAgent("RSPLIT_LMT_1", "SALESMAN_LMT");
        Product product = seedProduct();

        seedSale(agent, product, today, 10);
        seedSale(lmt, product, today, 7);

        ReportDTO agentReport = salesService.generateDailyReport(today, "AGENT");
        ReportDTO lmtReport = salesService.generateDailyReport(today, "SALESMAN_LMT");

        assertTrue(agentReport.getAgentSummaries().stream().anyMatch(s -> s.getEmployeeId().equals("RSPLIT_AGENT_1")),
                "AGENT-scoped report must include the AGENT's sale");
        assertTrue(agentReport.getAgentSummaries().stream().noneMatch(s -> s.getEmployeeId().equals("RSPLIT_LMT_1")),
                "AGENT-scoped report must NOT include the SALESMAN_LMT's sale");

        assertTrue(lmtReport.getAgentSummaries().stream().anyMatch(s -> s.getEmployeeId().equals("RSPLIT_LMT_1")),
                "SALESMAN_LMT-scoped report must include the LMT's sale");
        assertTrue(lmtReport.getAgentSummaries().stream().noneMatch(s -> s.getEmployeeId().equals("RSPLIT_AGENT_1")),
                "SALESMAN_LMT-scoped report must NOT include the AGENT's sale");

        // Per-agent summary revenue, not the whole report's total — isolated
        // from any other AGENT/LMT sales seeded elsewhere on the same day
        // (this class's other tests, or other test classes' same-day data
        // in the shared test DB).
        double agentOwnRevenue = agentReport.getAgentSummaries().stream()
                .filter(s -> s.getEmployeeId().equals("RSPLIT_AGENT_1")).findFirst().orElseThrow().getTotalRevenue();
        double lmtOwnRevenue = lmtReport.getAgentSummaries().stream()
                .filter(s -> s.getEmployeeId().equals("RSPLIT_LMT_1")).findFirst().orElseThrow().getTotalRevenue();
        assertEquals(500.0, agentOwnRevenue, 0.001, "AGENT's own summary revenue must reflect their 10 units");
        assertEquals(350.0, lmtOwnRevenue, 0.001, "LMT's own summary revenue must reflect their 7 units");
    }

    @Test
    void nullRoleReproducesTheExactUnfilteredReport() {
        LocalDate today = LocalDate.now();
        Agent agent = seedAgent("RSPLIT_AGENT_2", "AGENT");
        Agent lmt = seedAgent("RSPLIT_LMT_2", "SALESMAN_LMT");
        Product product = seedProduct();

        seedSale(agent, product, today, 4);
        seedSale(lmt, product, today, 3);

        ReportDTO unfilteredOverload = salesService.generateDailyReport(today, null);
        ReportDTO originalMethod = salesService.generateDailyReport(today);

        assertEquals(originalMethod.getTotalRevenue(), unfilteredOverload.getTotalRevenue(), 0.001,
                "A null role must delegate to the exact same unfiltered method — no behavior change for existing callers");
        assertEquals(originalMethod.getTotalUnits(), unfilteredOverload.getTotalUnits());
        assertEquals(originalMethod.getActiveAgents(), unfilteredOverload.getActiveAgents());
    }

    @Test
    void weeklyAndMonthlyReportsAlsoSeparateByRole() {
        // Weekly/monthly windows span multiple days and can pick up sales
        // seeded by other tests in this class on "today" — so this checks
        // per-product performance (each test uses its own unique product)
        // rather than the report's absolute total, which isn't isolated
        // across tests sharing the same date window.
        LocalDate today = LocalDate.now();
        Agent agent = seedAgent("RSPLIT_AGENT_3", "AGENT");
        Agent lmt = seedAgent("RSPLIT_LMT_3", "SALESMAN_LMT");
        Product agentProduct = seedProduct();
        Product lmtProduct = seedProduct();

        seedSale(agent, agentProduct, today, 5);
        seedSale(lmt, lmtProduct, today, 9);

        ReportDTO weeklyAgent = salesService.generateWeeklyReport(today, "AGENT");
        ReportDTO weeklyLmt = salesService.generateWeeklyReport(today, "SALESMAN_LMT");
        assertTrue(weeklyAgent.getProductPerformance().stream().anyMatch(p -> p.getProductName().equals(agentProduct.getName())),
                "AGENT-scoped weekly report must include the AGENT's product");
        assertTrue(weeklyAgent.getProductPerformance().stream().noneMatch(p -> p.getProductName().equals(lmtProduct.getName())),
                "AGENT-scoped weekly report must NOT include the LMT's product");
        assertTrue(weeklyLmt.getProductPerformance().stream().anyMatch(p -> p.getProductName().equals(lmtProduct.getName())),
                "SALESMAN_LMT-scoped weekly report must include the LMT's product");
        assertTrue(weeklyLmt.getProductPerformance().stream().noneMatch(p -> p.getProductName().equals(agentProduct.getName())),
                "SALESMAN_LMT-scoped weekly report must NOT include the AGENT's product");

        ReportDTO monthlyAgent = salesService.generateMonthlyReport(today, "AGENT");
        ReportDTO monthlyLmt = salesService.generateMonthlyReport(today, "SALESMAN_LMT");
        assertTrue(monthlyAgent.getProductPerformance().stream().anyMatch(p -> p.getProductName().equals(agentProduct.getName())),
                "AGENT-scoped monthly report must include the AGENT's product");
        assertTrue(monthlyAgent.getProductPerformance().stream().noneMatch(p -> p.getProductName().equals(lmtProduct.getName())),
                "AGENT-scoped monthly report must NOT include the LMT's product");
        assertTrue(monthlyLmt.getProductPerformance().stream().anyMatch(p -> p.getProductName().equals(lmtProduct.getName())),
                "SALESMAN_LMT-scoped monthly report must include the LMT's product");
        assertTrue(monthlyLmt.getProductPerformance().stream().noneMatch(p -> p.getProductName().equals(agentProduct.getName())),
                "SALESMAN_LMT-scoped monthly report must NOT include the AGENT's product");
    }
}
