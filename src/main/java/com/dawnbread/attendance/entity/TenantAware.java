package com.dawnbread.attendance.entity;

/**
 * Implemented by every tenant-scoped entity so {@link com.dawnbread.attendance.security.TenantEntityListener}
 * can stamp tenant_id on insert without needing a common mapped superclass
 * (these 12 entities already have independent, unrelated shapes).
 */
public interface TenantAware {
    Long getTenantId();
    void setTenantId(Long tenantId);
}
