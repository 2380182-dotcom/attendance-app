package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.LmtSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LmtSettingsRepository extends JpaRepository<LmtSettings, Long> {

    Optional<LmtSettings> findByTenantId(Long tenantId);
}
