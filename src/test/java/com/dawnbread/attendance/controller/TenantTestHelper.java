package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.TenantRepository;

import java.time.LocalDateTime;

/**
 * Test setup helpers (seedAgent/seedMart/etc.) call repository.save()
 * directly, outside any HTTP request — so SecurityInterceptor never runs and
 * TenantContext is never set. TenantEntityListener correctly refuses to
 * guess a tenant in that situation, so every such helper must set tenant_id
 * explicitly.
 *
 * Deliberately resolves the SAME tenant SecurityInterceptor's own
 * rollout-bridge fallback resolves to (findFirstByOrderByIdAsc — whichever
 * tenant was created first, which is DataInitializer's "DAWNBREAD" seeded at
 * app startup, before any test runs). Using a different tenant here would
 * silently put test-seeded data in a different tenant than the one the
 * HTTP request under test actually resolves to, since none of these
 * existing tests' tokens carry an explicit tenantId claim yet — exactly the
 * kind of mismatch that would show up as data mysteriously "not found"
 * rather than a clear tenant-isolation failure. Tests that need real
 * cross-tenant isolation (TenantIsolationIntegrationTest) create their own
 * distinct tenants and use TokenProvider's 4-arg generateToken instead of
 * this helper.
 */
final class TenantTestHelper {

    private TenantTestHelper() {
    }

    static Long defaultTenantId(TenantRepository tenantRepository) {
        return tenantRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setCompanyCode("DAWNBREAD");
                    t.setName("Dawn Bread");
                    t.setIsActive(true);
                    t.setCreatedAt(LocalDateTime.now());
                    t.setCreatedBy("TEST");
                    return tenantRepository.save(t);
                })
                .getId();
    }
}
