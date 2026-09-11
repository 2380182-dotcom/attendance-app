package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.LmtSettingsDTO;
import com.dawnbread.attendance.entity.LmtSettings;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.LmtSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The one LMT-wide setting for this stage: the geofence buffer added to a
 * shop's own radius when a later stage hard-gates shop visits. Read is open
 * to any authenticated role (the mobile shop-visit flow will need this
 * value); only an admin can change it.
 */
@RestController
@RequestMapping("/api/lmt/settings")
public class LmtSettingsController {

    @Autowired
    private LmtSettingsService lmtSettingsService;

    @Autowired
    private HttpServletRequest request;

    @GetMapping
    public ResponseEntity<ApiResponse<LmtSettingsDTO>> get() {
        LmtSettings settings = lmtSettingsService.getOrCreate();
        return ResponseEntity.ok(ApiResponse.success("LMT settings retrieved successfully", convertToDTO(settings)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<LmtSettingsDTO>> update(@RequestBody LmtSettingsDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can change LMT settings."));
        }
        try {
            LmtSettings updated = lmtSettingsService.updateBuffer(dto.getGeofenceBufferMeters());
            return ResponseEntity.ok(ApiResponse.success("LMT settings updated successfully", convertToDTO(updated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private LmtSettingsDTO convertToDTO(LmtSettings settings) {
        return new LmtSettingsDTO(settings.getGeofenceBufferMeters(), settings.getUpdatedAt());
    }
}
