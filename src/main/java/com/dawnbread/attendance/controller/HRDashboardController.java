package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.AgentPerformanceDTO;
import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.HRDashboardDTO;
import com.dawnbread.attendance.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hr/dashboard")
public class HRDashboardController {

    @Autowired
    private DashboardService dashboardService;

    // Combined attendance + sales data
    @GetMapping("/attendance-sales")
    public ResponseEntity<ApiResponse<HRDashboardDTO>> getAttendanceSales() {
        HRDashboardDTO dto = dashboardService.getHRDashboardWithSales();
        return ResponseEntity.ok(ApiResponse.success("Combined HR roster and sales data compiled", dto));
    }

    // Top performing agents
    @GetMapping("/top-performers")
    public ResponseEntity<ApiResponse<List<AgentPerformanceDTO>>> getTopPerformers() {
        HRDashboardDTO dto = dashboardService.getHRDashboardWithSales();
        return ResponseEntity.ok(ApiResponse.success("Top performing agents list loaded", dto.getTopPerformers()));
    }

    // Verification compliance
    @GetMapping("/compliance")
    public ResponseEntity<ApiResponse<HRDashboardDTO>> getCompliance() {
        HRDashboardDTO dto = dashboardService.getHRDashboardWithSales();
        return ResponseEntity.ok(ApiResponse.success("Verification compliance data compiled", dto));
    }
}
