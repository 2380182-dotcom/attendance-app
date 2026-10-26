package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.CompanyHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyHolidayRepository extends JpaRepository<CompanyHoliday, Long> {

    /** Used by the 10 PM job (no tenant filter there), so tenantId is explicit. */
    boolean existsByTenantIdAndHolidayDate(Long tenantId, LocalDate holidayDate);

    Optional<CompanyHoliday> findByTenantIdAndHolidayDate(Long tenantId, LocalDate holidayDate);

    List<CompanyHoliday> findByTenantIdOrderByHolidayDateDesc(Long tenantId);
}
