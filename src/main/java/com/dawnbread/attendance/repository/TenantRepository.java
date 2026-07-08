package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByCompanyCodeIgnoreCase(String companyCode);

    /**
     * Used only as a rollout-bridge fallback (pre-auth audit logging, and
     * JWTs issued before the tenantId claim existed) — never for real
     * tenant-scoped business logic. Resolves to whichever tenant was
     * created first, avoiding a hardcoded company-code string in app code.
     */
    Optional<Tenant> findFirstByOrderByIdAsc();
}
