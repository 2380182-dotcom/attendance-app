package com.dawnbread.attendance.service;

import com.dawnbread.attendance.config.CacheConfig;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Per-company settings kept on the company (tenant) row. For now only
 * "Absence tracking" (V37, off by default). Always the caller's own company.
 */
@Service
public class CompanySettingsService {

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private Clock clock;

    private Tenant current() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Company settings need a tenant context");
        }
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Company not found: " + tenantId));
    }

    @Transactional(readOnly = true)
    public boolean absenceTracking() {
        return Boolean.TRUE.equals(current().getAbsenceTrackingEnabled());
    }

    @Transactional
    public boolean setAbsenceTracking(boolean on) {
        Tenant t = current();
        t.setAbsenceTrackingEnabled(on);
        t.setUpdatedAt(LocalDateTime.now(clock));
        tenantRepository.save(t);
        // HR cockpit answers are cached for 90 s; show the change right away.
        Cache hr = cacheManager.getCache(CacheConfig.HR_COCKPIT);
        if (hr != null) hr.clear();
        return on;
    }
}
