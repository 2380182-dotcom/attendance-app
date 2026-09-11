package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.AreaCreateDTO;
import com.dawnbread.attendance.dto.AreaDTO;
import com.dawnbread.attendance.dto.HierarchyPersonDTO;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.HierarchyPerson;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.AreaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/** Sales territories. Admin-only for writes, same shape as MartController. */
@RestController
@RequestMapping("/api/lmt/areas")
public class AreaController {

    @Autowired
    private AreaService areaService;

    @Autowired
    private HttpServletRequest request;

    private ResponseEntity<ApiResponse<AreaDTO>> adminOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only an administrator can manage areas."));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AreaDTO>> create(@RequestBody AreaCreateDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            Area created = areaService.create(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Area created successfully", convertToDTO(created)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AreaDTO>>> getActive() {
        List<AreaDTO> dtos = areaService.getActive().stream().map(this::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Areas retrieved successfully", dtos));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<AreaDTO>>> getAll() {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can view inactive areas."));
        }
        List<AreaDTO> dtos = areaService.getAll().stream().map(this::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("All areas retrieved successfully", dtos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AreaDTO>> getById(@PathVariable Long id) {
        return areaService.getById(id)
                .map(a -> ResponseEntity.ok(ApiResponse.success("Area found", convertToDTO(a))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Area not found with id: " + id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AreaDTO>> update(@PathVariable Long id, @RequestBody AreaCreateDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            Area updated = areaService.update(id, dto);
            return ResponseEntity.ok(ApiResponse.success("Area updated successfully", convertToDTO(updated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can manage areas."));
        }
        try {
            areaService.deactivate(id);
            return ResponseEntity.ok(ApiResponse.success("Area deactivated successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<ApiResponse<AreaDTO>> reactivate(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            Area reactivated = areaService.reactivate(id);
            return ResponseEntity.ok(ApiResponse.success("Area reactivated successfully", convertToDTO(reactivated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private HierarchyPersonDTO convertPersonToDTO(HierarchyPerson person) {
        if (person == null) {
            return null;
        }
        HierarchyPersonDTO dto = new HierarchyPersonDTO(
                person.getId(), person.getName(), person.getRoleLabel(), person.getContact(), person.getCreatedAt()
        );
        dto.setIsActive(person.getIsActive());
        return dto;
    }

    private AreaDTO convertToDTO(Area area) {
        AreaDTO dto = new AreaDTO(area.getId(), area.getName(), area.getCreatedAt());
        dto.setTse(convertPersonToDTO(area.getTse()));
        dto.setSrTse(convertPersonToDTO(area.getSrTse()));
        dto.setAsm(convertPersonToDTO(area.getAsm()));
        dto.setIsActive(area.getIsActive());
        return dto;
    }
}
