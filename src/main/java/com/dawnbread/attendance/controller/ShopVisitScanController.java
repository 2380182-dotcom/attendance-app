package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.ShopVisitDaySummaryDTO;
import com.dawnbread.attendance.dto.ShopVisitScanRecordDTO;
import com.dawnbread.attendance.dto.ShopVisitScanRequest;
import com.dawnbread.attendance.dto.ShopVisitScanResponseDTO;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.ShopVisitScanService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** QR shop-visit flow (Q1). New endpoint, no overlap with /api/sales or the existing /api/lmt/customer-shops routes. */
@RestController
@RequestMapping("/api/lmt/shop-visits")
public class ShopVisitScanController {

    // Same rationale as ShopVisitScanService's injected Clock — an admin
    // opening this report with no explicit date param must get Karachi's
    // "today", not the server's UTC one.
    @Autowired
    private java.time.Clock clock;

    @Autowired
    private ShopVisitScanService shopVisitScanService;

    @Autowired
    private HttpServletRequest request;

    // Task 3: admin-only, per the user's explicit decision — QR Scanned
    // Shops moved to its own Admin-sidebar item (was Sales-accessible
    // before). Only used by the reporting endpoints below; POST /scan
    // above has its own separate self-or-admin gate, unaffected.
    private static final String[] MANAGEMENT_ROLES = { "ADMIN" };

    private <T> ResponseEntity<ApiResponse<T>> managementOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only an administrator can view shop visit scans."));
    }

    /**
     * Self-or-admin, same explicit role+id check as
     * SalesController.submitShopVisit — a salesman may only scan for
     * themselves, never on behalf of another agent id.
     */
    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<ShopVisitScanResponseDTO>> scan(@Valid @RequestBody ShopVisitScanRequest scanRequest) {
        String callerRole = AccessControl.callerRole(this.request);
        Long callerId = AccessControl.callerId(this.request);
        boolean isAdmin = "ADMIN".equals(callerRole);
        boolean isSelfSalesman = ("SALESMAN_LMT".equals(callerRole) || "SALESMAN_LOCAL".equals(callerRole))
                && callerId != null && callerId.equals(scanRequest.getAgentId());
        if (!isAdmin && !isSelfSalesman) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only a salesman (for themselves) or an ADMIN can scan a shop QR."));
        }
        try {
            ShopVisitScanResponseDTO result = shopVisitScanService.recordScan(scanRequest);
            // recordScan never throws for a failed scan (invalid code,
            // inactive shop, outside geofence) — it records the attempt
            // and returns a rejection in the DTO itself, so the mobile
            // client always gets a normal 200 with the reason to show.
            // It CAN throw for a true precondition failure (agent not
            // found, LMT not checked in) — those remain errors.
            return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private static final int MAX_PAGE_SIZE = 200;

    private org.springframework.data.domain.Pageable pageable(int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return org.springframework.data.domain.PageRequest.of(Math.max(page, 0), boundedSize);
    }

    /**
     * Task 3 — the admin's "QR / Shop Visits" report, server-side paginated
     * and filtered: date range, optional agentId/role/shopSearch/failedOnly.
     * Replaces the old unpaginated GET — the dashboard's QrShopVisitsPage
     * was its only caller, rewritten in the same change. Management-only,
     * distinct from the self-or-admin /scan endpoint above.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<com.dawnbread.attendance.dto.PageResponse<ShopVisitScanRecordDTO>>> getReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String shopSearch,
            @RequestParam(defaultValue = "false") boolean failedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate end = endDate != null ? endDate : LocalDate.now(clock);
        LocalDate start = startDate != null ? startDate : end;
        var result = shopVisitScanService.getPagedReport(start, end, agentId, role, shopSearch, failedOnly, pageable(page, size));
        return ResponseEntity.ok(ApiResponse.success("Shop visit scans retrieved successfully", com.dawnbread.attendance.dto.PageResponse.of(result)));
    }

    /** Q2 — one salesman's visit history for one day, plus the summary counts from the spec's example screen. Defaults to today. */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ShopVisitDaySummaryDTO>> getDaySummary(
            @RequestParam Long agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate target = date != null ? date : LocalDate.now(clock);
        ShopVisitDaySummaryDTO summary = shopVisitScanService.getDaySummary(agentId, target);
        return ResponseEntity.ok(ApiResponse.success("Shop visit summary retrieved successfully", summary));
    }

    /**
     * Task 3 "Not Visited" tab, server-side paginated. role is required
     * (LMT or LOCAL — they mean genuinely different things here: LMT is
     * assigned outlets not scanned, Local is every active shop not scanned
     * by anyone). agentId only makes sense for LMT (scopes to one
     * salesman's assigned outlets); ignored for LOCAL.
     */
    @GetMapping("/not-visited")
    public ResponseEntity<ApiResponse<com.dawnbread.attendance.dto.PageResponse<com.dawnbread.attendance.dto.NotVisitedShopDTO>>> getNotVisited(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String role,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String shopSearch,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate target = date != null ? date : LocalDate.now(clock);
        var result = shopVisitScanService.getNotVisited(target, role, agentId, shopSearch, pageable(page, size));
        return ResponseEntity.ok(ApiResponse.success("Not-visited shops retrieved successfully", com.dawnbread.attendance.dto.PageResponse.of(result)));
    }

    /** Task 3 summary counts (Total Shops / Visited / Not Visited / Voucher-Without-Scan) for one date + role. */
    @GetMapping("/summary-counts")
    public ResponseEntity<ApiResponse<com.dawnbread.attendance.dto.QrVisitSummaryDTO>> getSummaryCounts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String role,
            @RequestParam(required = false) Long agentId) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate target = date != null ? date : LocalDate.now(clock);
        var result = shopVisitScanService.getSummaryCounts(target, role, agentId);
        return ResponseEntity.ok(ApiResponse.success("Summary counts retrieved successfully", result));
    }

    /** Task 3 "voucher without scan": a SalesRecord exists for this agent/shop/day but no successful QR scan does. */
    @GetMapping("/vouchers-without-scan")
    public ResponseEntity<ApiResponse<com.dawnbread.attendance.dto.PageResponse<com.dawnbread.attendance.dto.VoucherWithoutScanDTO>>> getVouchersWithoutScan(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate end = endDate != null ? endDate : LocalDate.now(clock);
        LocalDate start = startDate != null ? startDate : end;
        var result = shopVisitScanService.getVouchersWithoutScan(start, end, agentId, role, pageable(page, size));
        return ResponseEntity.ok(ApiResponse.success("Vouchers without scan retrieved successfully", com.dawnbread.attendance.dto.PageResponse.of(result)));
    }
}
