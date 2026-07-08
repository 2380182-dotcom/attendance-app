package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.SuperAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SuperAdminRepository extends JpaRepository<SuperAdmin, Long> {
    Optional<SuperAdmin> findByUsernameIgnoreCase(String username);
}
