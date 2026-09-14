package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.LmtDailyStockDTO;
import com.dawnbread.attendance.dto.LmtMorningStockRequest;
import com.dawnbread.attendance.dto.LmtReconcileRequest;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.LmtStockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * LMT Phase C — stock reconciliation. Entirely new endpoints, no overlap
 * with /api/agents, /api/attendance, or /api/sales — zero routing or
 * behavior change for regular Agents.
 */
@RestController
@RequestMapping("/api/lmt/stock")
public class LmtStockController {

    @Autowired
    private LmtStockService lmtStockService;

    @Autowired
    private HttpServletRequest request;

    /**
     * SALESMAN_LMT (for themselves) or ADMIN (on behalf of anyone) — same
     * explicit role+id check as SalesController.submitShopVisit, and for
     * the same reason: AccessControl.isSelfOrRole's id-only "self" branch
     * would be fine here too (id equality already implies the same account
     * and therefore the same fixed role), but being explicit keeps this
     * endpoint's intent obvious and matches the established LMT pattern.
     */
    private boolean callerIsSelfLmtOrAdmin(Long targetAgentId) {
        String callerRole = AccessControl.callerRole(request);
        Long callerId = AccessControl.callerId(request);
        boolean isAdmin = "ADMIN".equals(callerRole);
        boolean isSelfSalesman = "SALESMAN_LMT".equals(callerRole)
                && callerId != null && callerId.equals(targetAgentId);
        return isAdmin || isSelfSalesman;
    }

    private <T> ResponseEntity<ApiResponse<T>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only a SALESMAN_LMT (for themselves) or an ADMIN can access this."));
    }

    private static final String[] MANAGEMENT_ROLES = { "ADMIN", "HR", "SALES" };

    private <T> ResponseEntity<ApiResponse<T>> managementOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only Admin, HR, or Sales can view the reconciliation report."));
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<LmtDailyStockDTO>> getToday(@RequestParam Long agentId) {
        if (!callerIsSelfLmtOrAdmin(agentId)) {
            return forbidden();
        }
        return lmtStockService.getToday(agentId)
                .map(dto -> ResponseEntity.ok(ApiResponse.success("Today's stock retrieved", dto)))
                .orElse(ResponseEntity.ok(ApiResponse.success("No stock entered yet today", null)));
    }

    @PostMapping("/morning")
    public ResponseEntity<ApiResponse<LmtDailyStockDTO>> enterMorningStock(@Valid @RequestBody LmtMorningStockRequest request) {
        if (!callerIsSelfLmtOrAdmin(request.getAgentId())) {
            return forbidden();
        }
        try {
            LmtDailyStockDTO dto = lmtStockService.enterMorningStock(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Morning stock entered successfully", dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/reconcile")
    public ResponseEntity<ApiResponse<LmtDailyStockDTO>> reconcile(@Valid @RequestBody LmtReconcileRequest request) {
        if (!callerIsSelfLmtOrAdmin(request.getAgentId())) {
            return forbidden();
        }
        try {
            LmtDailyStockDTO dto = lmtStockService.reconcile(request);
            return ResponseEntity.ok(ApiResponse.success("Stock reconciled successfully", dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Phase D (C6): the Sales Department's stock-reconciliation report —
     * management-only, distinct from the self-or-admin endpoints above.
     * Defaults to today when no range is given; agentId is optional (all
     * LMTs when omitted).
     */
    @GetMapping("/reconciliation")
    public ResponseEntity<ApiResponse<List<LmtDailyStockDTO>>> getReconciliationReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long agentId) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end;
        List<LmtDailyStockDTO> report = lmtStockService.getReconciliationReport(start, end, agentId);
        return ResponseEntity.ok(ApiResponse.success("Reconciliation report retrieved", report));
    }
}
