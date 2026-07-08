package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.entity.SuperAdmin;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.security.TokenProvider;
import com.dawnbread.attendance.service.AuditLogService;
import com.dawnbread.attendance.service.SuperAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wholly separate from /api/auth/**: a Super Admin manages companies
 * (tenants), never agents/attendance/sales — see SuperAdminService, which
 * has no dependency on any tenant-scoped repository at all. Every endpoint
 * here except /login is gated to AccessControl.SUPER_ADMIN_ROLE, a role no
 * tenant-data controller ever allowlists.
 */
@RestController
@RequestMapping("/api/super-admin")
public class SuperAdminController {

    @Autowired
    private SuperAdminService superAdminService;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private HttpServletRequest request;

    private <T> ResponseEntity<ApiResponse<T>> superAdminOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only a Super Admin can perform this action."));
    }

    /**
     * Public — deliberately never mixed with /api/auth/login. See
     * SecurityInterceptor's public-path bypass list.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");
        String ip = request != null ? request.getRemoteAddr() : "0.0.0.0";

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Username and password are required"));
        }

        Optional<SuperAdmin> superAdmin = superAdminService.authenticate(username, password);
        if (superAdmin.isEmpty()) {
            auditLogService.logAction("SUPER_ADMIN_LOGIN", username, "Invalid username or password", ip, "FAILED");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid username or password"));
        }

        SuperAdmin sa = superAdmin.get();
        String token = tokenProvider.generateSuperAdminToken(sa.getId(), sa.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("id", sa.getId());
        response.put("username", sa.getUsername());
        response.put("name", sa.getName());
        response.put("role", AccessControl.SUPER_ADMIN_ROLE);
        response.put("token", token);

        auditLogService.logAction("SUPER_ADMIN_LOGIN", username, "Login successful", ip, "SUCCESS");
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<List<Tenant>>> listTenants() {
        if (!AccessControl.hasRole(request, AccessControl.SUPER_ADMIN_ROLE)) {
            return superAdminOnly();
        }
        return ResponseEntity.ok(ApiResponse.success("Tenants retrieved", superAdminService.getAllTenants()));
    }

    @GetMapping("/tenants/{id}")
    public ResponseEntity<ApiResponse<Tenant>> getTenant(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, AccessControl.SUPER_ADMIN_ROLE)) {
            return superAdminOnly();
        }
        return superAdminService.getTenantById(id)
                .map(t -> ResponseEntity.ok(ApiResponse.success("Tenant found", t)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Tenant not found with id: " + id)));
    }

    @PostMapping("/tenants")
    public ResponseEntity<ApiResponse<Tenant>> createTenant(@RequestBody Tenant tenant) {
        if (!AccessControl.hasRole(request, AccessControl.SUPER_ADMIN_ROLE)) {
            return superAdminOnly();
        }
        try {
            Tenant created = superAdminService.createTenant(tenant, AccessControl.callerUsername(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tenant created", created));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/tenants/{id}/suspend")
    public ResponseEntity<ApiResponse<Tenant>> suspendTenant(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, AccessControl.SUPER_ADMIN_ROLE)) {
            return superAdminOnly();
        }
        try {
            return ResponseEntity.ok(ApiResponse.success("Tenant suspended",
                    superAdminService.setTenantActive(id, false, AccessControl.callerUsername(request))));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/tenants/{id}/activate")
    public ResponseEntity<ApiResponse<Tenant>> activateTenant(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, AccessControl.SUPER_ADMIN_ROLE)) {
            return superAdminOnly();
        }
        try {
            return ResponseEntity.ok(ApiResponse.success("Tenant activated",
                    superAdminService.setTenantActive(id, true, AccessControl.callerUsername(request))));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
