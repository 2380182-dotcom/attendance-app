package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.MartCreateDTO;
import com.dawnbread.attendance.dto.MartDTO;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.MartService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/marts")
public class MartController {

    @Autowired
    private MartService martService;

    @Autowired
    private HttpServletRequest request;

    private ResponseEntity<ApiResponse<MartDTO>> adminOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only an administrator can manage marts."));
    }

    /**
     * Create a new mart. Admin-only — reads stay open to any authenticated
     * role (agents need the mart list to check in), but mutations don't.
     *
     * Binds to MartCreateDTO, not the Mart entity — the entity's tenantId
     * field would otherwise be directly settable from client JSON (see the
     * identical note on AgentController.createAgent).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<MartDTO>> createMart(@RequestBody MartCreateDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            Mart created = martService.createMart(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Mart created successfully", convertToDTO(created)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get all marts
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<MartDTO>>> getAllMarts() {
        List<Mart> marts = martService.getAllMarts();
        List<MartDTO> dtos = marts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Marts retrieved successfully", dtos));
    }

    /**
     * Get mart by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MartDTO>> getMartById(@PathVariable Long id) {
        return martService.getMartById(id)
                .map(mart -> ResponseEntity.ok(ApiResponse.success("Mart found", convertToDTO(mart))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Mart not found with id: " + id)));
    }

    /**
     * Get mart by name
     */
    @GetMapping("/name/{name}")
    public ResponseEntity<ApiResponse<MartDTO>> getMartByName(@PathVariable String name) {
        return martService.getMartByName(name)
                .map(mart -> ResponseEntity.ok(ApiResponse.success("Mart found", convertToDTO(mart))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Mart not found with name: " + name)));
    }

    /**
     * Search marts by name
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<MartDTO>>> searchMarts(@RequestParam String name) {
        List<Mart> marts = martService.searchMartsByName(name);
        List<MartDTO> dtos = marts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Marts found", dtos));
    }

    /**
     * Find marts within radius
     */
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<MartDTO>>> findMartsWithinRadius(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam double radius) {
        List<Mart> marts = martService.findMartsWithinRadius(latitude, longitude, radius);
        List<MartDTO> dtos = marts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Marts found within radius", dtos));
    }

    /**
     * Update mart. Admin-only.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MartDTO>> updateMart(@PathVariable Long id, @RequestBody Mart martDetails) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            Mart updated = martService.updateMart(id, martDetails);
            return ResponseEntity.ok(ApiResponse.success("Mart updated successfully", convertToDTO(updated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Delete mart (soft delete — sets isActive = false, does not remove the row).
     * Admin-only.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMart(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can manage marts."));
        }
        try {
            martService.deleteMart(id);
            return ResponseEntity.ok(ApiResponse.success("Mart deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Reactivate a previously soft-deleted mart. Admin-only.
     */
    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<ApiResponse<MartDTO>> reactivateMart(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            Mart reactivated = martService.reactivateMart(id);
            return ResponseEntity.ok(ApiResponse.success("Mart reactivated successfully", convertToDTO(reactivated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get active marts
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<MartDTO>>> getActiveMarts() {
        List<Mart> marts = martService.getActiveMarts();
        List<MartDTO> dtos = marts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Active marts retrieved", dtos));
    }

    /**
     * Get top marts by attendance
     */
    @GetMapping("/top")
    public ResponseEntity<ApiResponse<List<Object[]>>> getTopMarts(@RequestParam(defaultValue = "5") int limit) {
        List<Object[]> topMarts = martService.getTopMartsByAttendance(limit);
        return ResponseEntity.ok(ApiResponse.success("Top marts retrieved", topMarts));
    }

    /**
     * Count marts
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> countMarts() {
        long count = martService.countMarts();
        return ResponseEntity.ok(ApiResponse.success("Total marts count", count));
    }

    // ===== Helper Methods =====
    private MartDTO convertToDTO(Mart mart) {
        MartDTO dto = new MartDTO(
                mart.getId(),
                mart.getName(),
                mart.getAddress(),
                mart.getLatitude(),
                mart.getLongitude(),
                mart.getRadius(),
                mart.getCreatedAt()
        );
        dto.setIsActive(mart.getIsActive());
        return dto;
    }
}
