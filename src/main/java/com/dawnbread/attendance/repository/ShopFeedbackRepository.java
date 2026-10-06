package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.ShopFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** "Salesman updates" feature: a salesman reached a shop but couldn't make a sale, so he recorded feedback instead. */
@Repository
public interface ShopFeedbackRepository extends JpaRepository<ShopFeedback, Long> {

    Optional<ShopFeedback> findByRequestId(String requestId);

    /** Mirrors ShopVisitScanRepository.findSuccessfullyScannedShopIdsForAgentAndDate — a shop with feedback counts as visited too. */
    @Query("SELECT DISTINCT f.customerShop.id FROM ShopFeedback f WHERE f.agentId = :agentId AND f.visitDate = :visitDate")
    Set<Long> findShopIdsForAgentAndDate(@Param("agentId") Long agentId, @Param("visitDate") LocalDate visitDate);

    /** Mirrors ShopVisitScanRepository.findSuccessfullyScannedShopIdsForDate — Local's "every shop anyone visited" check. */
    @Query("SELECT DISTINCT f.customerShop.id FROM ShopFeedback f WHERE f.visitDate = :visitDate")
    Set<Long> findShopIdsForDate(@Param("visitDate") LocalDate visitDate);

    /** The admin's merged "Visited" list, feedback rows only — joined to Agent for role filtering, same pattern as ShopVisitScanRepository.findFiltered. */
    @Query("SELECT f FROM ShopFeedback f LEFT JOIN FETCH f.customerShop sh JOIN Agent a ON a.id = f.agentId " +
            "WHERE f.visitDate BETWEEN :startDate AND :endDate " +
            "AND (:agentId IS NULL OR f.agentId = :agentId) " +
            "AND (:role = '' OR a.role = :role) " +
            "ORDER BY f.visitDate DESC, f.visitTime DESC")
    List<ShopFeedback> findFiltered(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                     @Param("agentId") Long agentId, @Param("role") String role);
}
