package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.AgentPerformanceDTO;
import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.HRDashboardDTO;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Every endpoint here backs HRDashboardScreen, which is HR-role only reachable
 * in the mobile navigator. Admin/HR only — a Sales or bare Agent account has
 * no business reading HR's roster/compliance analytics.
 */
@RestController
@RequestMapping("/api/hr/dashboard")
public class HRDashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private HttpServletRequest request;

    private <T> ResponseEntity<ApiResponse<T>> hrOrAdminOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only HR or an administrator can view this dashboard."));
    }

    // Combined attendance + sales data
    @GetMapping("/attendance-sales")
    public ResponseEntity<ApiResponse<HRDashboardDTO>> getAttendanceSales() {
        if (!AccessControl.hasRole(request, "ADMIN", "HR")) {
            return hrOrAdminOnly();
        }
        HRDashboardDTO dto = dashboardService.getHRDashboardWithSales();
        return ResponseEntity.ok(ApiResponse.success("Combined HR roster and sales data compiled", dto));
    }

    // Top performing agents
    @GetMapping("/top-performers")
    public ResponseEntity<ApiResponse<List<AgentPerformanceDTO>>> getTopPerformers() {
        if (!AccessControl.hasRole(request, "ADMIN", "HR")) {
            return hrOrAdminOnly();
        }
        HRDashboardDTO dto = dashboardService.getHRDashboardWithSales();
        return ResponseEntity.ok(ApiResponse.success("Top performing agents list loaded", dto.getTopPerformers()));
    }

    // Verification compliance
    @GetMapping("/compliance")
    public ResponseEntity<ApiResponse<HRDashboardDTO>> getCompliance() {
        if (!AccessControl.hasRole(request, "ADMIN", "HR")) {
            return hrOrAdminOnly();
        }
        HRDashboardDTO dto = dashboardService.getHRDashboardWithSales();
        return ResponseEntity.ok(ApiResponse.success("Verification compliance data compiled", dto));
    }
}
