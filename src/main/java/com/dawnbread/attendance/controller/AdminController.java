package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.*;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<AdminStatsDTO>> getStatistics() {
        try {
            AdminStatsDTO stats = adminService.getAdminDashboardStats();
            return ResponseEntity.ok(ApiResponse.success("Statistics retrieved successfully", stats));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/agents")
    public ResponseEntity<ApiResponse<Agent>> createAgent(@RequestBody AgentRegistrationDTO dto) {
        try {
            Agent created = adminService.createAgent(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Agent created successfully", created));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/agents")
    public ResponseEntity<ApiResponse<List<Agent>>> listAgents(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Boolean active) {
        try {
            List<Agent> agents = adminService.listAgents(role, department, active);
            return ResponseEntity.ok(ApiResponse.success("Agents retrieved", agents));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/agents/{id}/face-config")
    public ResponseEntity<ApiResponse<Agent>> updateFaceConfig(
            @PathVariable Long id,
            @RequestBody FaceConfigDTO config) {
        try {
            Agent updated = adminService.updateFaceConfig(id, config);
            return ResponseEntity.ok(ApiResponse.success("Face config updated", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/agents/{id}/shift")
    public ResponseEntity<ApiResponse<Agent>> updateShift(
            @PathVariable Long id,
            @RequestBody ShiftScheduleDTO shift) {
        try {
            Agent updated = adminService.updateShift(id, shift);
            return ResponseEntity.ok(ApiResponse.success("Shift updated", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/agents/{id}/schedule")
    public ResponseEntity<ApiResponse<ShiftScheduleDTO>> getAgentSchedule(@PathVariable Long id) {
        try {
            ShiftScheduleDTO schedule = adminService.getAgentSchedule(id);
            return ResponseEntity.ok(ApiResponse.success("Shift schedule retrieved", schedule));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/marts")
    public ResponseEntity<ApiResponse<List<Mart>>> getAllMarts() {
        try {
            List<Mart> marts = adminService.getAllMarts();
            return ResponseEntity.ok(ApiResponse.success("Marts retrieved successfully", marts));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/marts/{id}")
    public ResponseEntity<ApiResponse<Mart>> getMartById(@PathVariable Long id) {
        return adminService.getMartById(id)
                .map(mart -> ResponseEntity.ok(ApiResponse.success("Mart found", mart)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Mart not found with id: " + id)));
    }

    @PostMapping("/marts")
    public ResponseEntity<ApiResponse<Mart>> createMart(@RequestBody Mart mart) {
        try {
            Mart created = adminService.createMart(mart);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Mart created successfully", created));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/marts/{id}")
    public ResponseEntity<ApiResponse<Mart>> updateMart(@PathVariable Long id, @RequestBody Mart martDetails) {
        try {
            Mart updated = adminService.updateMart(id, martDetails);
            return ResponseEntity.ok(ApiResponse.success("Mart updated successfully", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/marts/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMart(@PathVariable Long id) {
        try {
            adminService.deleteMart(id);
            return ResponseEntity.ok(ApiResponse.success("Mart deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/marts/{id}/reactivate")
    public ResponseEntity<ApiResponse<Mart>> reactivateMart(@PathVariable Long id) {
        try {
            Mart reactivated = adminService.reactivateMart(id);
            return ResponseEntity.ok(ApiResponse.success("Mart reactivated successfully", reactivated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/marts/{id}/toggle-geofence")
    public ResponseEntity<ApiResponse<Mart>> toggleGeoFence(
            @PathVariable Long id,
            @RequestParam Boolean enabled) {
        try {
            Mart updated = adminService.toggleGeoFence(id, enabled);
            return ResponseEntity.ok(ApiResponse.success("Mart geo-fence toggled successfully", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
