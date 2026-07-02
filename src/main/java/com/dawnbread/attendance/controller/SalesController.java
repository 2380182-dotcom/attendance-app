package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.*;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.entity.SalesSyncLog;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.SalesSyncLogRepository;
import com.dawnbread.attendance.service.DashboardService;
import com.dawnbread.attendance.service.SalesService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sales")
public class SalesController {

    @Autowired
    private SalesService salesService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SalesSyncLogRepository salesSyncLogRepository;

    // Get products with images
    @GetMapping("/products-with-images")
    public ResponseEntity<ApiResponse<List<Product>>> getProductsWithImages() {
        List<Product> products = productRepository.findByIsActiveTrue();
        return ResponseEntity.ok(ApiResponse.success("Products catalog loaded successfully", products));
    }

    // Add sales with product images
    @PostMapping("/entry-with-images")
    public ResponseEntity<ApiResponse<SalesDTO>> addSalesWithImages(@Valid @RequestBody SalesRequest request) {
        try {
            SalesRecord record = salesService.addSalesWithImages(request);
            SalesDTO dto = salesService.convertToDTO(record);
            return ResponseEntity.ok(ApiResponse.success("Sales record entered successfully", dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // Get agent sales with product images
    @GetMapping("/agent-sales/{agentId}")
    public ResponseEntity<ApiResponse<List<SalesDTO>>> getAgentSales(@PathVariable Long agentId) {
        List<SalesDTO> dtos = salesService.getSalesWithImages(agentId);
        return ResponseEntity.ok(ApiResponse.success("Agent sales retrieved successfully", dtos));
    }

    // Generate daily sales report
    @GetMapping("/daily-report")
    public ResponseEntity<ApiResponse<ReportDTO>> getDailyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        ReportDTO report = salesService.generateDailyReport(target);
        return ResponseEntity.ok(ApiResponse.success("Daily sales report generated", report));
    }

    // Generate weekly sales report
    @GetMapping("/weekly-report")
    public ResponseEntity<ApiResponse<ReportDTO>> getWeeklyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        ReportDTO report = salesService.generateWeeklyReport(target);
        return ResponseEntity.ok(ApiResponse.success("Weekly sales report generated", report));
    }

    // Generate monthly sales report
    @GetMapping("/monthly-report")
    public ResponseEntity<ApiResponse<ReportDTO>> getMonthlyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        ReportDTO report = salesService.generateMonthlyReport(target);
        return ResponseEntity.ok(ApiResponse.success("Monthly sales report generated", report));
    }

    // Sync sales to Sales Department manually
    @PostMapping("/sync-to-sales-department")
    public ResponseEntity<ApiResponse<Void>> syncToSalesDepartment(@RequestParam Long saleRecordId) {
        try {
            // We fetch the record and sync it
            salesService.syncToSalesDepartment(new SalesRecord() {{ setId(saleRecordId); }});
            return ResponseEntity.ok(ApiResponse.success("Sales record synced to Sales Department", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // Sync sales to HR Department manually
    @PostMapping("/sync-to-hr-department")
    public ResponseEntity<ApiResponse<Void>> syncToHRDepartment(@RequestParam Long saleRecordId) {
        try {
            salesService.syncToHRDepartment(new SalesRecord() {{ setId(saleRecordId); }});
            return ResponseEntity.ok(ApiResponse.success("Sales record synced to HR Department", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // Check sync status
    @GetMapping("/sync-status")
    public ResponseEntity<ApiResponse<List<SalesSyncLog>>> getSyncStatus() {
        List<SalesSyncLog> logs = salesSyncLogRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success("Sync logs retrieved successfully", logs));
    }

    // Get real-time sales dashboard
    @GetMapping("/dashboard/realtime")
    public ResponseEntity<ApiResponse<SalesDashboardDTO>> getRealtimeSalesDashboard() {
        SalesDashboardDTO dto = dashboardService.getRealtimeSalesDashboard();
        return ResponseEntity.ok(ApiResponse.success("Realtime sales dashboard metrics compiled", dto));
    }

    // Get top selling products
    @GetMapping("/dashboard/top-products")
    public ResponseEntity<ApiResponse<List<SalesDashboardDTO.ProductSalesDetail>>> getTopProducts() {
        SalesDashboardDTO dto = dashboardService.getRealtimeSalesDashboard();
        return ResponseEntity.ok(ApiResponse.success("Top selling products retrieved", dto.getTopSellingProducts()));
    }

    // Get agent performance ranking
    @GetMapping("/dashboard/agent-ranking")
    public ResponseEntity<ApiResponse<List<SalesDashboardDTO.AgentSalesSummary>>> getAgentRanking() {
        SalesDashboardDTO dto = dashboardService.getRealtimeSalesDashboard();
        return ResponseEntity.ok(ApiResponse.success("Agent performance rankings retrieved", dto.getSalesByAgent()));
    }

    // Get sales trends with graphs
    @GetMapping("/dashboard/trends")
    public ResponseEntity<ApiResponse<List<SalesDashboardDTO.DailyTrend>>> getTrends() {
        SalesDashboardDTO dto = dashboardService.getRealtimeSalesDashboard();
        return ResponseEntity.ok(ApiResponse.success("Daily sales trends retrieved", dto.getSalesTrend()));
    }

    // Report endpoints (re-routing for Report screen compatibility)
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

    @GetMapping("/reports/product-performance")
    public ResponseEntity<ApiResponse<List<ReportDTO.ProductPerformanceDetail>>> getProductPerformanceReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        ReportDTO report = salesService.generateDailyReport(target);
        return ResponseEntity.ok(ApiResponse.success("Product performance report loaded", report.getProductPerformance()));
    }

    @GetMapping("/reports/agent-ranking")
    public ResponseEntity<ApiResponse<List<SalesDashboardDTO.AgentSalesSummary>>> getAgentRankingReport() {
        return getAgentRanking();
    }

    // Admin override sales record
    @PutMapping("/{id}/override")
    public ResponseEntity<ApiResponse<SalesDTO>> overrideSales(
            @PathVariable Long id,
            @Valid @RequestBody SalesRequest request,
            @RequestParam String reason,
            @RequestParam String username) {
        try {
            SalesRecord record = salesService.overrideSalesEntry(id, request, reason, username);
            SalesDTO dto = salesService.convertToDTO(record);
            return ResponseEntity.ok(ApiResponse.success("Sales record overridden by admin", dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
