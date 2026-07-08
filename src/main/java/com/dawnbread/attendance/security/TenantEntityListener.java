package com.dawnbread.attendance.security;

import com.dawnbread.attendance.entity.TenantAware;
import jakarta.persistence.PrePersist;

/**
 * Auto-stamps tenant_id on every new tenant-scoped entity so application
 * code never has to remember to set it manually. If the entity already has
 * a tenant_id (e.g. a test or seeder explicitly set one, or code is
 * deliberately creating data for a specific tenant), that value is left
 * untouched. If neither is set, this fails loudly rather than silently
 * defaulting — a save reaching here with no tenant context at all is a real
 * bug (a request that should have gone through SecurityInterceptor didn't),
 * and silently guessing a tenant would be exactly the kind of data leak this
 * whole mechanism exists to prevent.
 */
public class TenantEntityListener {

    @PrePersist
    public void setTenantId(Object entity) {
        if (!(entity instanceof TenantAware tenantAware)) {
            return;
        }
        if (tenantAware.getTenantId() != null) {
            return;
        }
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "Cannot save " + entity.getClass().getSimpleName()
                            + ": no tenant_id set on the entity and no tenant context available. "
                            + "Set tenantId explicitly, or ensure this save happens within a request "
                            + "that went through SecurityInterceptor.");
        }
        tenantAware.setTenantId(tenantId);
    }
}
