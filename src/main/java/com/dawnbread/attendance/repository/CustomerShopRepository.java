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

    /** Task 3/4 "Not Visited": this salesman's assigned, active outlets (Local or LMT — assignment is role-agnostic, the caller already knows the agent's role). */
    @Query("SELECT s FROM CustomerShop s LEFT JOIN FETCH s.area WHERE s.isActive = true AND s.assignedAgent.id = :agentId")
    List<CustomerShop> findByIsActiveTrueAndAssignedAgentId(@Param("agentId") Long agentId);

    /**
     * Every active shop assigned to ANY salesman of this role — the
     * "Not Visited" tab across all salesmen in one section. Filters by the
     * ASSIGNED AGENT's role, not the shop (a shop has no type of its own).
     */
    @Query("SELECT s FROM CustomerShop s LEFT JOIN FETCH s.area WHERE s.isActive = true AND s.assignedAgent.role = :role")
    List<CustomerShop> findByIsActiveTrueAndAssignedAgentRole(@Param("role") String role);

    /** Task 4 correction: active shops with NO salesman assigned at all — shown as a separate "Unassigned" list so no shop is hidden from either report. */
    @Query("SELECT s FROM CustomerShop s LEFT JOIN FETCH s.area WHERE s.isActive = true AND s.assignedAgent IS NULL")
    List<CustomerShop> findByIsActiveTrueAndAssignedAgentIsNull();
}
