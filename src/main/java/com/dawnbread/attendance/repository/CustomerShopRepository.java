package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.CustomerShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Deliberately no findShopsWithinRadius yet — geofence-based shop lookup is
// a mobile/shop-visit concern (a later stage), out of scope for this
// additive-foundation stage.
@Repository
public interface CustomerShopRepository extends JpaRepository<CustomerShop, Long> {

    Optional<CustomerShop> findByShopCode(String shopCode);
    List<CustomerShop> findByShopNameContainingIgnoreCase(String shopName);
    List<CustomerShop> findByIsActiveTrue();
    boolean existsByShopCode(String shopCode);

    /** Task 3 LMT "Not Visited": this salesman's assigned, active outlets. */
    @Query("SELECT s FROM CustomerShop s LEFT JOIN FETCH s.area WHERE s.isActive = true AND s.assignedAgent.id = :agentId")
    List<CustomerShop> findByIsActiveTrueAndAssignedAgentId(@Param("agentId") Long agentId);

    /** Every active shop assigned to ANY LMT salesman — for the LMT "Not Visited" tab across all salesmen. */
    @Query("SELECT s FROM CustomerShop s LEFT JOIN FETCH s.area WHERE s.isActive = true AND s.assignedAgent IS NOT NULL")
    List<CustomerShop> findByIsActiveTrueAndAssignedAgentIsNotNull();

    /** Task 3 Local "Not Visited": every active shop, unassigned concept doesn't apply. Area fetch-joined for the report's display fields. */
    @Query("SELECT s FROM CustomerShop s LEFT JOIN FETCH s.area WHERE s.isActive = true")
    List<CustomerShop> findByIsActiveTrueWithArea();
}
