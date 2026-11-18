package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.ProductFamily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Every query takes the tenantId explicitly (like CompanyHolidayRepository); findById is checked by the caller. */
@Repository
public interface ProductFamilyRepository extends JpaRepository<ProductFamily, Long> {

    List<ProductFamily> findByTenantIdOrderBySortOrderAscIdAsc(Long tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(Long tenantId, String name, Long id);
}
