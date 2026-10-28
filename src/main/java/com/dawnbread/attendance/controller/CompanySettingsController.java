package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.CompanySettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Company-wide switches for the caller's own company. Admin and HR can read
 * them; only an administrator can change them (Admin > Holidays page).
 */
@RestController
@RequestMapping("/api/company-settings")
public class CompanySettingsController {

    @Autowired
    private CompanySettingsService service;

    @Autowired
    private HttpServletRequest request;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> get() {
        if (!AccessControl.hasRole(request, "ADMIN", "HR")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Only Admin and HR can view company settings."));
        }
        return ResponseEntity.ok(ApiResponse.success("Company settings retrieved", Map.of("absenceTracking", service.absenceTracking())));
    }

    /** Body: {"absenceTracking": true|false}. */
    @PutMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@RequestBody Map<String, Object> body) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Only an administrator can change company settings."));
        }
        if (!(body.get("absenceTracking") instanceof Boolean on)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("absenceTracking must be true or false"));
        }
        return ResponseEntity.ok(ApiResponse.success("Company settings updated", Map.of("absenceTracking", service.setAbsenceTracking(on))));
    }
}
