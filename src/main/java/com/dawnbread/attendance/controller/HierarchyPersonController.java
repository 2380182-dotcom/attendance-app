package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.HierarchyPersonCreateDTO;
import com.dawnbread.attendance.dto.HierarchyPersonDTO;
import com.dawnbread.attendance.entity.HierarchyPerson;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.HierarchyPersonService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TSE / SR TSE / ASM reference records. Admin-only for writes — same
 * role-gating shape as MartController. Reads stay open to any authenticated
 * role since Area/CustomerShop admin screens need this list to populate
 * pickers, mirroring how mart reads are open for the check-in flow.
 */
@RestController
@RequestMapping("/api/lmt/hierarchy-persons")
public class HierarchyPersonController {

    @Autowired
    private HierarchyPersonService hierarchyPersonService;

    @Autowired
    private HttpServletRequest request;

    private ResponseEntity<ApiResponse<HierarchyPersonDTO>> adminOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only an administrator can manage hierarchy persons."));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HierarchyPersonDTO>> create(@RequestBody HierarchyPersonCreateDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            HierarchyPerson created = hierarchyPersonService.create(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Hierarchy person created successfully", convertToDTO(created)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<HierarchyPersonDTO>>> getActive() {
        List<HierarchyPersonDTO> dtos = hierarchyPersonService.getActive().stream()
                .map(this::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Hierarchy persons retrieved successfully", dtos));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<HierarchyPersonDTO>>> getAll() {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can view inactive hierarchy persons."));
        }
        List<HierarchyPersonDTO> dtos = hierarchyPersonService.getAll().stream()
                .map(this::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("All hierarchy persons retrieved successfully", dtos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HierarchyPersonDTO>> getById(@PathVariable Long id) {
        return hierarchyPersonService.getById(id)
                .map(p -> ResponseEntity.ok(ApiResponse.success("Hierarchy person found", convertToDTO(p))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Hierarchy person not found with id: " + id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HierarchyPersonDTO>> update(@PathVariable Long id, @RequestBody HierarchyPersonCreateDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            HierarchyPerson updated = hierarchyPersonService.update(id, dto);
            return ResponseEntity.ok(ApiResponse.success("Hierarchy person updated successfully", convertToDTO(updated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can manage hierarchy persons."));
        }
        try {
            hierarchyPersonService.deactivate(id);
            return ResponseEntity.ok(ApiResponse.success("Hierarchy person deactivated successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<ApiResponse<HierarchyPersonDTO>> reactivate(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            HierarchyPerson reactivated = hierarchyPersonService.reactivate(id);
            return ResponseEntity.ok(ApiResponse.success("Hierarchy person reactivated successfully", convertToDTO(reactivated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private HierarchyPersonDTO convertToDTO(HierarchyPerson person) {
        HierarchyPersonDTO dto = new HierarchyPersonDTO(
                person.getId(), person.getName(), person.getRoleLabel(), person.getContact(), person.getCreatedAt()
        );
        dto.setIsActive(person.getIsActive());
        return dto;
    }
}
