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
import java.util.List;

/** QR shop-visit flow (Q1). New endpoint, no overlap with /api/sales or the existing /api/lmt/customer-shops routes. */
@RestController
@RequestMapping("/api/lmt/shop-visits")
public class ShopVisitScanController {

    @Autowired
    private ShopVisitScanService shopVisitScanService;

    @Autowired
    private HttpServletRequest request;

    private static final String[] MANAGEMENT_ROLES = { "ADMIN", "HR", "SALES" };

    private <T> ResponseEntity<ApiResponse<T>> managementOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only Admin, HR, or Sales can view shop visit scans."));
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

    /**
     * Q2 — the Sales Department's "QR / Shop Visits" report: every scan
     * attempt (pass or fail) in a date range, across all salesmen or one.
     * Management-only, distinct from the self-or-admin /scan endpoint above
     * — same split as LmtStockController's reconciliation report vs its
     * self-or-admin endpoints. Defaults to today when no range is given;
     * agentId is optional (every salesman when omitted).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ShopVisitScanRecordDTO>>> getReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long agentId) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end;
        List<ShopVisitScanRecordDTO> report = shopVisitScanService.getReport(start, end, agentId);
        return ResponseEntity.ok(ApiResponse.success("Shop visit scans retrieved successfully", report));
    }

    /** Q2 — one salesman's visit history for one day, plus the summary counts from the spec's example screen. Defaults to today. */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ShopVisitDaySummaryDTO>> getDaySummary(
            @RequestParam Long agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate target = date != null ? date : LocalDate.now();
        ShopVisitDaySummaryDTO summary = shopVisitScanService.getDaySummary(agentId, target);
        return ResponseEntity.ok(ApiResponse.success("Shop visit summary retrieved successfully", summary));
    }
}
