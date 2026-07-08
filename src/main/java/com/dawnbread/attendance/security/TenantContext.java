package com.dawnbread.attendance.security;

/**
 * Per-request holder for the current tenant id, set by SecurityInterceptor
 * from the verified JWT's tenantId claim. Backs both the Hibernate tenant
 * filter (read-side scoping) and TenantEntityListener (write-side stamping).
 * Must be cleared at the end of every request — see
 * SecurityInterceptor#afterCompletion — since Tomcat reuses worker threads
 * across requests and a leaked value here would leak one tenant's data into
 * another's request.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
