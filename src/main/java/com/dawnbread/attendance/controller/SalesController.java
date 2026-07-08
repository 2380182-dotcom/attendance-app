package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.*;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.entity.SalesSyncLog;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.SalesSyncLogRepository;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.DashboardService;
import com.dawnbread.attendance.service.ExcelExportService;
import com.dawnbread.attendance.service.SalesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class SalesController {

    private static final String[] MANAGEMENT_ROLES = { "ADMIN", "HR", "SALES" };

    @Autowired
    private SalesService salesService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SalesSyncLogRepository salesSyncLogRepository;

    @Autowired
    private ExcelExportService excelExportService;

    @Autowired
    private HttpServletRequest request;

    private <T> ResponseEntity<ApiResponse<T>> managementOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only Admin, HR, or Sales can access this."));
    }

    private <T> ResponseEntity<ApiResponse<T>> selfOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You can only do this for yourself."));
    }

    // Product catalog — open to any authenticated role; agents need this to
    // build a sales entry (confirmed: SalesEntryScreen calls this directly).
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductCatalogDTO>>> getProducts() {
        List<ProductCatalogDTO> products = salesService.getProductCatalog();
        return ResponseEntity.ok(ApiResponse.success("Product catalog loaded", products));
    }

    @GetMapping("/products-with-images")
    public ResponseEntity<ApiResponse<List<Product>>> getProductsWithImages() {
        List<Product> products = productRepository.findByIsActiveTrue();
        return ResponseEntity.ok(ApiResponse.success("Products catalog loaded successfully", products));
    }

    /**
     * Self-only. Confirmed no mobile screen ever submits a sales entry with
     * an agentId other than the logged-in user's own — same rationale as the
     * attendance checkin/checkout impersonation guard, no management
     * on-behalf-of flow exists for this endpoint (it's also the older,
     * currently-unused-by-mobile sibling of /entry-with-images below, but
     * gated the same way since it's still a live, reachable endpoint).
     */
    @PostMapping("/entry")
    public ResponseEntity<ApiResponse<SalesEntryResponseDTO>> submitSalesEntry(
            @Valid @RequestBody SalesEntryRequestDTO request) {
        if (!AccessControl.isSelfOrRole(this.request, request.getAgentId())) {
            return selfOnly();
        }
        try {
            SalesEntryResponseDTO response = salesService.submitSalesEntry(request);
            return ResponseEntity.ok(ApiResponse.success("Sales entry submitted", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // Company-wide aggregate (SalesService.getTodaySummary() sums ALL agents'
    // records for today, unfiltered by agent) — management-only.
    @GetMapping("/dashboard/today")
    public ResponseEntity<ApiResponse<SalesDashboardDTO>> getTodayDashboard() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        SalesDashboardDTO dto = salesService.getTodaySummary();
        return ResponseEntity.ok(ApiResponse.success("Today's sales summary", dto));
    }

    /** Self-or-management — same pattern as the attendance endpoints. */
    @GetMapping("/dashboard/agent/{agentId}")
    public ResponseEntity<ApiResponse<List<SalesDTO>>> getAgentDashboardSales(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<SalesDTO> dtos = salesService.getSalesWithImages(agentId);
        return ResponseEntity.ok(ApiResponse.success("Agent sales retrieved", dtos));
    }

    // Cross-agent search by name/date/store — management-only.
    @GetMapping("/dashboard/search")
    public ResponseEntity<ApiResponse<List<SalesDTO>>> searchSales(
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String storeName) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<SalesDTO> results = salesService.searchSales(agentName, date, storeName);
        return ResponseEntity.ok(ApiResponse.success("Sales search results", results));
    }

    /**
     * Self-only. Confirmed via SalesEntryScreen: submitSales() always sends
     * agentId: user.id, the logged-in agent's own id — no on-behalf-of flow.
     */
    @PostMapping("/entry-with-images")
    public ResponseEntity<ApiResponse<SalesDTO>> addSalesWithImages(@Valid @RequestBody SalesRequest request) {
        if (!AccessControl.isSelfOrRole(this.request, request.getAgentId())) {
            return selfOnly();
        }
        try {
            SalesRecord record = salesService.addSalesWithImages(request);
            SalesDTO dto = salesService.convertToDTO(record);
            return ResponseEntity.ok(ApiResponse.success("Sales record entered successfully", dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Self-or-management. Confirmed via DashboardScreen (agent): always
     * called with the logged-in agent's own id. No management screen
     * currently calls this for a picked agent (SalesAgentReportScreen uses
     * the separate CSV export endpoint instead) — gated the same as the
     * attendance/notification pattern anyway, for the same oversight reason.
     */
    @GetMapping("/agent-sales/{agentId}")
    public ResponseEntity<ApiResponse<List<SalesDTO>>> getAgentSales(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<SalesDTO> dtos = salesService.getSalesWithImages(agentId);
        return ResponseEntity.ok(ApiResponse.success("Agent sales retrieved successfully", dtos));
    }

    // Company-wide reports — management-only. Confirmed used by
    // SalesReportScreen (Sales role screen), never by a bare agent.
    @GetMapping("/daily-report")
    public ResponseEntity<ApiResponse<ReportDTO>> getDailyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate target = date != null ? date : LocalDate.now();
        ReportDTO report = salesService.generateDailyReport(target);
        return ResponseEntity.ok(ApiResponse.success("Daily sales report generated", report));
    }

    @GetMapping("/weekly-report")
    public ResponseEntity<ApiResponse<ReportDTO>> getWeeklyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate target = date != null ? date : LocalDate.now();
        ReportDTO report = salesService.generateWeeklyReport(target);
        return ResponseEntity.ok(ApiResponse.success("Weekly sales report generated", report));
    }

    @GetMapping("/monthly-report")
    public ResponseEntity<ApiResponse<ReportDTO>> getMonthlyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate target = date != null ? date : LocalDate.now();
        ReportDTO report = salesService.generateMonthlyReport(target);
        return ResponseEntity.ok(ApiResponse.success("Monthly sales report generated", report));
    }

    /**
     * Management-only. Previously built an anonymous SalesRecord stub with
     * only its id set and passed it straight to the service, which called
     * record.getAgent().getName() on that stub — always null, always an
     * NPE. Now loads the real record by id first (SalesService.
     * syncToSalesDepartment(Long)); a nonexistent id now fails cleanly with
     * a normal 400 error message instead of a raw NPE stack trace.
     */
    @PostMapping("/sync-to-sales-department")
    public ResponseEntity<ApiResponse<Void>> syncToSalesDepartment(@RequestParam Long saleRecordId) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        try {
            salesService.syncToSalesDepartment(saleRecordId);
            return ResponseEntity.ok(ApiResponse.success("Sales record synced to Sales Department", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Management-only. Had the identical stub-record pattern as its sibling
     * above — it just never NPE'd, since this sync message never
     * dereferences record.getAgent(). It also never verified saleRecordId
     * corresponded to a real row, so a bogus id would silently log a fake
     * "success" sync. Fixed the same way, for the same reason.
     */
    @PostMapping("/sync-to-hr-department")
    public ResponseEntity<ApiResponse<Void>> syncToHRDepartment(@RequestParam Long saleRecordId) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        try {
            salesService.syncToHRDepartment(saleRecordId);
            return ResponseEntity.ok(ApiResponse.success("Sales record synced to HR Department", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // Internal sync-log visibility — operational data, not something a bare
    // agent needs; gated the same as the other ops endpoints in this file.
    @GetMapping("/sync-status")
    public ResponseEntity<ApiResponse<List<SalesSyncLog>>> getSyncStatus() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<SalesSyncLog> logs = salesSyncLogRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success("Sync logs retrieved successfully", logs));
    }

    // Company-wide dashboards — management-only. Confirmed used by
    // SalesDashboardScreen (Sales role screen).
    @GetMapping("/dashboard/realtime")
    public ResponseEntity<ApiResponse<SalesDashboardDTO>> getRealtimeSalesDashboard() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        SalesDashboardDTO dto = dashboardService.getRealtimeSalesDashboard();
        return ResponseEntity.ok(ApiResponse.success("Realtime sales dashboard metrics compiled", dto));
    }

    @GetMapping("/dashboard/top-products")
    public ResponseEntity<ApiResponse<List<SalesDashboardDTO.ProductSalesDetail>>> getTopProducts() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        SalesDashboardDTO dto = dashboardService.getRealtimeSalesDashboard();
        return ResponseEntity.ok(ApiResponse.success("Top selling products retrieved", dto.getTopSellingProducts()));
    }

    @GetMapping("/dashboard/agent-ranking")
    public ResponseEntity<ApiResponse<List<SalesDashboardDTO.AgentSalesSummary>>> getAgentRanking() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        SalesDashboardDTO dto = dashboardService.getRealtimeSalesDashboard();
        return ResponseEntity.ok(ApiResponse.success("Agent performance rankings retrieved", dto.getSalesByAgent()));
    }

    @GetMapping("/dashboard/trends")
    public ResponseEntity<ApiResponse<List<SalesDashboardDTO.DailyTrend>>> getTrends() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        SalesDashboardDTO dto = dashboardService.getRealtimeSalesDashboard();
        return ResponseEntity.ok(ApiResponse.success("Daily sales trends retrieved", dto.getSalesTrend()));
    }

    // Report endpoints (re-routing for Report screen compatibility) — each
    // delegates straight into an already-gated sibling method above, so the
    // same management-only check applies transparently; no separate check
    // needed here.
    @GetMapping("/reports/daily")
    public ResponseEntity<ApiResponse<ReportDTO>> getReportsDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return getDailyReport(date);
    }

    @GetMapping("/reports/weekly")
    public ResponseEntity<ApiResponse<ReportDTO>> getReportsWeekly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return getWeeklyReport(date);
    }

    @GetMapping("/reports/monthly")
    public ResponseEntity<ApiResponse<ReportDTO>> getReportsMonthly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return getMonthlyReport(date);
    }

    // Does NOT delegate to an already-gated sibling — needs its own check.
    @GetMapping("/reports/product-performance")
    public ResponseEntity<ApiResponse<List<ReportDTO.ProductPerformanceDetail>>> getProductPerformanceReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate target = date != null ? date : LocalDate.now();
        ReportDTO report = salesService.generateDailyReport(target);
        return ResponseEntity.ok(ApiResponse.success("Product performance report loaded", report.getProductPerformance()));
    }

    @GetMapping("/reports/agent-ranking")
    public ResponseEntity<ApiResponse<List<SalesDashboardDTO.AgentSalesSummary>>> getAgentRankingReport() {
        return getAgentRanking();
    }

    /**
     * Admin override sales record. Unchanged — already ADMIN/HR only from
     * the earlier RBAC pass.
     */
    @PutMapping("/{id}/override")
    public ResponseEntity<ApiResponse<SalesDTO>> overrideSales(
            @PathVariable Long id,
            @Valid @RequestBody SalesRequest request,
            @RequestParam String reason) {
        if (!AccessControl.hasRole(this.request, "ADMIN", "HR")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator or HR can override sales records."));
        }
        try {
            String actingUsername = AccessControl.callerUsername(this.request);
            SalesRecord record = salesService.overrideSalesEntry(id, request, reason, actingUsername);
            SalesDTO dto = salesService.convertToDTO(record);
            return ResponseEntity.ok(ApiResponse.success("Sales record overridden by admin", dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Management-only. NOT redundant with ExcelExportController's /export —
     * confirmed that one calls excelExportService.exportAllReports() for
     * ATTENDANCE workbooks; this calls .exportAllSales() for SALES data, a
     * genuinely different export with no other gated entry point.
     */
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportAllSales() throws IOException {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ByteArrayInputStream in = excelExportService.exportAllSales();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=sales_export.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @GetMapping("/export/agent/{agentId}")
    public ResponseEntity<InputStreamResource> exportAgentSales(@PathVariable Long agentId) throws IOException {
        if (!AccessControl.isSelfOrRole(request, agentId, MANAGEMENT_ROLES)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ByteArrayInputStream in = excelExportService.exportAgentSales(agentId);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=agent_sales_" + agentId + ".xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @GetMapping("/export/department/{department}")
    public ResponseEntity<InputStreamResource> exportDepartmentSales(@PathVariable String department) throws IOException {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ByteArrayInputStream in = excelExportService.exportDepartmentSales(department);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=sales_" + department + ".xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }
}
