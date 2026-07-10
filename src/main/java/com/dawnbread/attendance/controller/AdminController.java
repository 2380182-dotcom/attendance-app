package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.*;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Every endpoint here is admin-only — confirmed no other role calls anything
 * under /api/admin/** in the mobile client (Sales/HR/Agent screens use the
 * dedicated MartController/AgentController/etc. endpoints instead). Role is
 * read from the verified JWT via AccessControl, never a client-supplied field.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private HttpServletRequest request;

    private <T> ResponseEntity<ApiResponse<T>> adminOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only an administrator can access this."));
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<AdminStatsDTO>> getStatistics() {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            AdminStatsDTO stats = adminService.getAdminDashboardStats();
            return ResponseEntity.ok(ApiResponse.success("Statistics retrieved successfully", stats));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/agents")
    public ResponseEntity<ApiResponse<AgentDTO>> createAgent(@RequestBody AgentRegistrationDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            Agent created = adminService.createAgent(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Agent created successfully", convertToDTO(created)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/agents")
    public ResponseEntity<ApiResponse<List<AgentDTO>>> listAgents(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Boolean active) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            List<Agent> agents = adminService.listAgents(role, department, active);
            List<AgentDTO> dtos = agents.stream().map(this::convertToDTO).collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Agents retrieved", dtos));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/agents/{id}/face-config")
    public ResponseEntity<ApiResponse<AgentDTO>> updateFaceConfig(
            @PathVariable Long id,
            @RequestBody FaceConfigDTO config) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            Agent updated = adminService.updateFaceConfig(id, config);
            return ResponseEntity.ok(ApiResponse.success("Face config updated", convertToDTO(updated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/agents/{id}/shift")
    public ResponseEntity<ApiResponse<AgentDTO>> updateShift(
            @PathVariable Long id,
            @RequestBody ShiftScheduleDTO shift) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            Agent updated = adminService.updateShift(id, shift);
            return ResponseEntity.ok(ApiResponse.success("Shift updated", convertToDTO(updated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/agents/{id}/schedule")
    public ResponseEntity<ApiResponse<ShiftScheduleDTO>> getAgentSchedule(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            ShiftScheduleDTO schedule = adminService.getAgentSchedule(id);
            return ResponseEntity.ok(ApiResponse.success("Shift schedule retrieved", schedule));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/marts")
    public ResponseEntity<ApiResponse<List<Mart>>> getAllMarts() {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            List<Mart> marts = adminService.getAllMarts();
            return ResponseEntity.ok(ApiResponse.success("Marts retrieved successfully", marts));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/marts/{id}")
    public ResponseEntity<ApiResponse<Mart>> getMartById(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        return adminService.getMartById(id)
                .map(mart -> ResponseEntity.ok(ApiResponse.success("Mart found", mart)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Mart not found with id: " + id)));
    }

    /**
     * Binds to MartCreateDTO, not the Mart entity — see the identical note
     * on AgentController.createAgent for why a raw-entity binding here would
     * let a client-supplied tenantId field plant a row in another tenant.
     */
    @PostMapping("/marts")
    public ResponseEntity<ApiResponse<Mart>> createMart(@RequestBody MartCreateDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            Mart created = adminService.createMart(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Mart created successfully", created));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/marts/{id}")
    public ResponseEntity<ApiResponse<Mart>> updateMart(@PathVariable Long id, @RequestBody Mart martDetails) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            Mart updated = adminService.updateMart(id, martDetails);
            return ResponseEntity.ok(ApiResponse.success("Mart updated successfully", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/marts/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMart(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            adminService.deleteMart(id);
            return ResponseEntity.ok(ApiResponse.success("Mart deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/marts/{id}/reactivate")
    public ResponseEntity<ApiResponse<Mart>> reactivateMart(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
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
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            Mart updated = adminService.toggleGeoFence(id, enabled);
            return ResponseEntity.ok(ApiResponse.success("Mart geo-fence toggled successfully", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Never return the Agent entity directly — password, faceTemplate, and
     * faceEmbedding are the sensitive fields this guards against, and while
     * they now also carry field-level @JsonIgnore, that's a floor, not a
     * substitute for keeping entities off the wire entirely.
     */
    private AgentDTO convertToDTO(Agent agent) {
        AgentDTO dto = new AgentDTO(
                agent.getId(),
                agent.getAgentId(),
                agent.getName(),
                agent.getEmail(),
                agent.getPhone(),
                agent.getRole(),
                agent.getDepartment(),
                agent.getCreatedAt(),
                agent.getFaceVerifyOnCheckIn(),
                agent.getFaceVerifyOnCheckOut(),
                agent.getFaceVerifyAnytime(),
                agent.getFaceRegistered()
        );
        dto.setShiftStartTime(agent.getShiftStartTime());
        dto.setShiftEndTime(agent.getShiftEndTime());
        dto.setGracePeriodMinutes(agent.getGracePeriodMinutes());
        dto.setWorkingDays(agent.getWorkingDays());
        return dto;
    }
}
