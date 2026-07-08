package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.AuditLog;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AuditLogRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TenantRepository tenantRepository;

    public AuditLog logAction(String action, String username, String details, String ipAddress, String status) {
        AuditLog log = new AuditLog(action, username, details, ipAddress, LocalDateTime.now(), status);
        log.setTenantId(resolveTenantId());
        return auditLogRepository.save(log);
    }

    /**
     * For call sites that already know the real tenant before TenantContext
     * would otherwise be available — e.g. AuthController.login(), which
     * resolves the Company Code's tenant itself before logging the attempt.
     * Passing null here falls back to the same bridge as the 5-arg overload
     * (e.g. an unknown company code truly has no tenant to attribute to).
     */
    public AuditLog logAction(String action, String username, String details, String ipAddress, String status, Long explicitTenantId) {
        AuditLog log = new AuditLog(action, username, details, ipAddress, LocalDateTime.now(), status);
        log.setTenantId(explicitTenantId != null ? explicitTenantId : resolveTenantId());
        return auditLogRepository.save(log);
    }

    /**
     * Audit logging must never block a request. Most calls happen inside an
     * authenticated request, where SecurityInterceptor already populated
     * TenantContext from the caller's JWT. The one exception is
     * /api/auth/login itself — a login attempt (successful or failed) is
     * audited before any tenant is known, since SecurityInterceptor never
     * runs for that public endpoint. Falling back to the earliest-created
     * tenant here is a deliberate, narrow bridge for this one write-only
     * table; it stops being ambiguous once the Company Code login flow
     * resolves a real tenant before logging the attempt.
     */
    private Long resolveTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository.findFirstByOrderByIdAsc()
                .map(Tenant::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "No tenant context available and no tenant exists to fall back to for audit logging"));
    }
}
