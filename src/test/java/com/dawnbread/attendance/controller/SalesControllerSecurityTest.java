package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.SalesRecordRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Full RBAC pass over SalesController's 27 endpoints, same rigor as the
 * earlier controllers: real HTTP through the actual interceptor + controller
 * chain. Covers the three access patterns applied — self-only (sales entry
 * submission, no management on-behalf-of flow exists), self-or-management
 * (per-agent sales lookups, same pattern as attendance), and management-only
 * (company-wide aggregates, exports, sync ops) — via representative
 * endpoints from each category rather than exhaustively re-testing all 27
 * individually.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SalesControllerSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Long tenantId() {
        return TenantTestHelper.defaultTenantId(tenantRepository);
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

    private SalesRecord seedSalesRecord(Agent agent) {
        SalesRecord record = new SalesRecord();
        record.setTenantId(tenantId());
        record.setAgent(agent);
        record.setStoreName("Test Store");
        record.setTotalAmount(100.0);
        record.setTotalUnits(2);
        record.setSaleDate(LocalDate.now());
        record.setSaleTime(LocalTime.now());
        record.setSubmittedAt(LocalDateTime.now());
        record.setLocation("Test Location");
        record.setCreatedAt(LocalDateTime.now());
        return salesRecordRepository.save(record);
    }

    private Product seedProduct() {
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("Sales RBAC Test Bread " + System.nanoTime());
        product.setPrice(50.0);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, Object>> withTokenAndBody(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    // ---------- Self-only: /entry-with-images (no on-behalf-of flow exists) ----------

    @Test
    void entryWithImagesRejectsImpersonationButAllowsSelf() {
        Agent self = seedAgent("SALES_SELF_1", "AGENT");
        Agent other = seedAgent("SALES_OTHER_1", "AGENT");
        Product product = seedProduct();
        String selfToken = tokenProvider.generateToken(self.getId(), self.getAgentId(), "AGENT", tenantId());

        Map<String, Object> item = new HashMap<>();
        item.put("productId", product.getId());
        item.put("quantity", 2);
        item.put("unitPrice", 50.0);
        item.put("totalPrice", 100.0);

        Map<String, Object> impersonatingBody = new HashMap<>();
        impersonatingBody.put("agentId", other.getId());
        impersonatingBody.put("location", "Test Store");
        impersonatingBody.put("items", List.of(item));

        ResponseEntity<String> impersonating = restTemplate.exchange(
                url("/api/sales/entry-with-images"), HttpMethod.POST, withTokenAndBody(selfToken, impersonatingBody), String.class);
        assertEquals(HttpStatus.FORBIDDEN, impersonating.getStatusCode(),
                "Submitting a sales entry attributed to a different agent must be rejected: " + impersonating.getBody());

        Map<String, Object> ownBody = new HashMap<>();
        ownBody.put("agentId", self.getId());
        ownBody.put("location", "Test Store");
        ownBody.put("items", List.of(item));

        ResponseEntity<String> ownRequest = restTemplate.exchange(
                url("/api/sales/entry-with-images"), HttpMethod.POST, withTokenAndBody(selfToken, ownBody), String.class);
        assertEquals(HttpStatus.OK, ownRequest.getStatusCode(),
                "Submitting your own sales entry must succeed: " + ownRequest.getBody());
    }

    // ---------- Self-or-management: per-agent sales lookups ----------

    @Test
    void agentSalesLookupEnforcesOwnershipUnlessManagement() {
        Agent self = seedAgent("SALES_SELF_2", "AGENT");
        Agent other = seedAgent("SALES_OTHER_2", "AGENT");
        Agent salesStaff = seedAgent("SALES_STAFF_2", "SALES");
        String selfToken = tokenProvider.generateToken(self.getId(), self.getAgentId(), "AGENT", tenantId());
        String salesToken = tokenProvider.generateToken(salesStaff.getId(), salesStaff.getAgentId(), "SALES", tenantId());

        ResponseEntity<String> ownLookup = restTemplate.exchange(
                url("/api/sales/agent-sales/" + self.getId()), HttpMethod.GET, withToken(selfToken), String.class);
        assertEquals(HttpStatus.OK, ownLookup.getStatusCode(), "Agent reading their own sales must succeed: " + ownLookup.getBody());

        ResponseEntity<String> crossLookup = restTemplate.exchange(
                url("/api/sales/agent-sales/" + other.getId()), HttpMethod.GET, withToken(selfToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, crossLookup.getStatusCode(),
                "Agent reading another agent's sales must be rejected: " + crossLookup.getBody());

        ResponseEntity<String> managementLookup = restTemplate.exchange(
                url("/api/sales/agent-sales/" + other.getId()), HttpMethod.GET, withToken(salesToken), String.class);
        assertEquals(HttpStatus.OK, managementLookup.getStatusCode(),
                "Sales role reading any agent's sales must succeed: " + managementLookup.getBody());

        // Same pattern on the dashboard/agent/{agentId} sibling endpoint.
        ResponseEntity<String> dashboardCross = restTemplate.exchange(
                url("/api/sales/dashboard/agent/" + other.getId()), HttpMethod.GET, withToken(selfToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, dashboardCross.getStatusCode(), dashboardCross.getBody());
    }

    // ---------- Management-only aggregates ----------

    @Test
    void aggregateDashboardsAndReportsRejectBareAgent() {
        Agent agent = seedAgent("SALES_AGENT_3", "AGENT");
        Agent salesStaff = seedAgent("SALES_STAFF_3", "SALES");
        String agentToken = tokenProvider.generateToken(agent.getId(), agent.getAgentId(), "AGENT", tenantId());
        String salesToken = tokenProvider.generateToken(salesStaff.getId(), salesStaff.getAgentId(), "SALES", tenantId());

        String[] managementOnlyPaths = {
                "/api/sales/dashboard/today",
                "/api/sales/dashboard/realtime",
                "/api/sales/dashboard/top-products",
                "/api/sales/dashboard/agent-ranking",
                "/api/sales/dashboard/trends",
                "/api/sales/daily-report",
                "/api/sales/weekly-report",
                "/api/sales/monthly-report",
                "/api/sales/reports/product-performance",
                "/api/sales/reports/agent-ranking",
                "/api/sales/sync-status",
        };

        for (String path : managementOnlyPaths) {
            ResponseEntity<String> agentAttempt = restTemplate.exchange(url(path), HttpMethod.GET, withToken(agentToken), String.class);
            assertEquals(HttpStatus.FORBIDDEN, agentAttempt.getStatusCode(),
                    "AGENT must be rejected from " + path + ": " + agentAttempt.getBody());

            ResponseEntity<String> salesAttempt = restTemplate.exchange(url(path), HttpMethod.GET, withToken(salesToken), String.class);
            assertNotEquals(HttpStatus.FORBIDDEN, salesAttempt.getStatusCode(),
                    "SALES must be allowed on " + path + ": " + salesAttempt.getBody());
        }

        ResponseEntity<String> searchAttempt = restTemplate.exchange(
                url("/api/sales/dashboard/search"), HttpMethod.GET, withToken(agentToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, searchAttempt.getStatusCode());
    }

    // ---------- Sync endpoints: gated, and the stub-record NPE is fixed ----------

    @Test
    void syncEndpointsAreManagementGated() {
        Agent agent = seedAgent("SALES_AGENT_4", "AGENT");
        String agentToken = tokenProvider.generateToken(agent.getId(), agent.getAgentId(), "AGENT", tenantId());

        // Bare agent rejected from both, before any business logic runs.
        ResponseEntity<String> agentSalesSync = restTemplate.exchange(
                url("/api/sales/sync-to-sales-department?saleRecordId=1"), HttpMethod.POST, withToken(agentToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, agentSalesSync.getStatusCode());

        ResponseEntity<String> agentHrSync = restTemplate.exchange(
                url("/api/sales/sync-to-hr-department?saleRecordId=1"), HttpMethod.POST, withToken(agentToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, agentHrSync.getStatusCode());
    }

    /**
     * Previously: the controller passed a stub SalesRecord with only its id
     * set, and syncToSalesDepartment() NPE'd on record.getAgent().getName().
     * Now it loads the real record from the database first — proving that
     * fix with a genuine, fully-seeded SalesRecord, not just a raw id.
     */
    @Test
    void syncToSalesDepartmentSucceedsForARealRecord() {
        Agent salesSubject = seedAgent("SALES_SYNC_SUBJECT", "AGENT");
        Agent adminStaff = seedAgent("SALES_SYNC_ADMIN", "ADMIN");
        SalesRecord record = seedSalesRecord(salesSubject);
        String adminToken = tokenProvider.generateToken(adminStaff.getId(), adminStaff.getAgentId(), "ADMIN", tenantId());

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/sales/sync-to-sales-department?saleRecordId=" + record.getId()),
                HttpMethod.POST, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Syncing a real sales record must succeed now that the real record is loaded instead of an empty stub: " + response.getBody());
    }

    /** Same fix applied to the HR sibling, which had the identical stub bug. */
    @Test
    void syncToHrDepartmentSucceedsForARealRecord() {
        Agent salesSubject = seedAgent("SALES_SYNC_SUBJECT_HR", "AGENT");
        Agent adminStaff = seedAgent("SALES_SYNC_ADMIN_HR", "ADMIN");
        SalesRecord record = seedSalesRecord(salesSubject);
        String adminToken = tokenProvider.generateToken(adminStaff.getId(), adminStaff.getAgentId(), "ADMIN", tenantId());

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/sales/sync-to-hr-department?saleRecordId=" + record.getId()),
                HttpMethod.POST, withToken(adminToken), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
    }

    /**
     * The old stub silently "succeeded" for any id, real or not — now a
     * nonexistent record fails cleanly with a normal 400, not a raw NPE.
     */
    @Test
    void syncToSalesDepartmentFailsCleanlyForANonexistentRecord() {
        Agent adminStaff = seedAgent("SALES_SYNC_ADMIN_MISSING", "ADMIN");
        String adminToken = tokenProvider.generateToken(adminStaff.getId(), adminStaff.getAgentId(), "ADMIN", tenantId());

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/sales/sync-to-sales-department?saleRecordId=999999999"),
                HttpMethod.POST, withToken(adminToken), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("not found"), response.getBody());
    }

    // ---------- Export endpoints ----------

    @Test
    void exportEndpointsAreGatedCorrectly() {
        Agent self = seedAgent("SALES_SELF_5", "AGENT");
        Agent other = seedAgent("SALES_OTHER_5", "AGENT");
        Agent hrStaff = seedAgent("SALES_HR_5", "HR");
        String selfToken = tokenProvider.generateToken(self.getId(), self.getAgentId(), "AGENT", tenantId());
        String hrToken = tokenProvider.generateToken(hrStaff.getId(), hrStaff.getAgentId(), "HR", tenantId());

        ResponseEntity<String> agentExportAll = restTemplate.exchange(
                url("/api/sales/export"), HttpMethod.GET, withToken(selfToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, agentExportAll.getStatusCode());

        ResponseEntity<String> agentExportDept = restTemplate.exchange(
                url("/api/sales/export/department/SALES"), HttpMethod.GET, withToken(selfToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, agentExportDept.getStatusCode());

        ResponseEntity<String> ownExport = restTemplate.exchange(
                url("/api/sales/export/agent/" + self.getId()), HttpMethod.GET, withToken(selfToken), String.class);
        assertNotEquals(HttpStatus.FORBIDDEN, ownExport.getStatusCode(), "Self export must be allowed: " + ownExport.getBody());

        ResponseEntity<String> crossExport = restTemplate.exchange(
                url("/api/sales/export/agent/" + other.getId()), HttpMethod.GET, withToken(selfToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, crossExport.getStatusCode(), "Cross-agent export must be rejected: " + crossExport.getBody());

        ResponseEntity<String> hrExportAny = restTemplate.exchange(
                url("/api/sales/export/agent/" + other.getId()), HttpMethod.GET, withToken(hrToken), String.class);
        assertNotEquals(HttpStatus.FORBIDDEN, hrExportAny.getStatusCode(), "HR export of any agent must be allowed: " + hrExportAny.getBody());
    }

    // ---------- Confirms the override endpoint (already fixed earlier) is untouched ----------

    @Test
    void productCatalogEndpointsRemainOpenToAnyAuthenticatedRole() {
        Agent agent = seedAgent("SALES_AGENT_6", "AGENT");
        String agentToken = tokenProvider.generateToken(agent.getId(), agent.getAgentId(), "AGENT", tenantId());

        ResponseEntity<String> products = restTemplate.exchange(
                url("/api/sales/products-with-images"), HttpMethod.GET, withToken(agentToken), String.class);
        assertEquals(HttpStatus.OK, products.getStatusCode(),
                "Agents must still be able to browse the product catalog to build a sales entry: " + products.getBody());
    }
}
