package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.SuperAdmin;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.SuperAdminRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Deliberately has NO dependency on AgentRepository, AttendanceRepository,
 * or any other tenant-scoped repository — a Super Admin's entire surface is
 * managing the Tenant table itself. That is what "no access to any tenant's
 * operational data by default" means structurally, not just as a role check.
 */
@Service
@Transactional
public class SuperAdminService {

    @Autowired
    private SuperAdminRepository superAdminRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Optional<SuperAdmin> authenticate(String username, String password) {
        Optional<SuperAdmin> found = superAdminRepository.findByUsernameIgnoreCase(username);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        SuperAdmin superAdmin = found.get();
        if (!Boolean.TRUE.equals(superAdmin.getIsActive())) {
            return Optional.empty();
        }
        if (!passwordEncoder.matches(password, superAdmin.getPassword())) {
            return Optional.empty();
        }
        return found;
    }

    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }

    public Optional<Tenant> getTenantById(Long id) {
        return tenantRepository.findById(id);
    }

    public Tenant createTenant(Tenant tenant, String createdByUsername) {
        if (tenant.getCompanyCode() == null || tenant.getCompanyCode().isBlank()) {
            throw new RuntimeException("Company code is required");
        }
        if (tenantRepository.findByCompanyCodeIgnoreCase(tenant.getCompanyCode()).isPresent()) {
            throw new RuntimeException("Company code already in use: " + tenant.getCompanyCode());
        }
        tenant.setId(null);
        tenant.setIsActive(true);
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setCreatedBy(createdByUsername);
        return tenantRepository.save(tenant);
    }

    public Tenant setTenantActive(Long id, boolean active, String actingUsername) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found with id: " + id));
        tenant.setIsActive(active);
        tenant.setUpdatedAt(LocalDateTime.now());
        tenant.setUpdatedBy(actingUsername);
        return tenantRepository.save(tenant);
    }
}
