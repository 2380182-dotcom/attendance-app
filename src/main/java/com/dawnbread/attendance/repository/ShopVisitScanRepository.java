package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.ShopVisitScan;
import com.dawnbread.attendance.entity.ShopVisitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShopVisitScanRepository extends JpaRepository<ShopVisitScan, Long> {

    /** The QR-required enforcement check in SalesService.submitShopVisit: has this agent already scanned this shop successfully today? */
    boolean existsByAgentIdAndCustomerShopIdAndScanDateAndVisitStatus(
            Long agentId, Long customerShopId, LocalDate scanDate, ShopVisitStatus visitStatus);

    /** One salesman's scan attempts for one day, most recent first. Used only by the Q1 QR-enforcement tests directly against the repository. */
    List<ShopVisitScan> findByAgentIdAndScanDateOrderByScanTimeDesc(Long agentId, LocalDate scanDate);

    /**
     * Q2 admin report — every scan (pass or fail) in a date range, shop
     * fetch-joined so listing hundreds of rows with shop name/code/area is
     * one query, not N+1. LEFT JOIN since an INVALID_CODE scan has no shop.
     */
    @Query("SELECT s FROM ShopVisitScan s LEFT JOIN FETCH s.customerShop WHERE s.scanDate BETWEEN :startDate AND :endDate ORDER BY s.scanDate DESC, s.scanTime DESC")
    List<ShopVisitScan> findByScanDateBetweenWithShop(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** Same as above, scoped to one salesman. */
    @Query("SELECT s FROM ShopVisitScan s LEFT JOIN FETCH s.customerShop WHERE s.agentId = :agentId AND s.scanDate BETWEEN :startDate AND :endDate ORDER BY s.scanDate DESC, s.scanTime DESC")
    List<ShopVisitScan> findByAgentIdAndScanDateBetweenWithShop(
            @Param("agentId") Long agentId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** The admin's per-salesman-per-day visit history + summary counts (Q2). */
    @Query("SELECT s FROM ShopVisitScan s LEFT JOIN FETCH s.customerShop WHERE s.agentId = :agentId AND s.scanDate = :scanDate ORDER BY s.scanTime DESC")
    List<ShopVisitScan> findByAgentIdAndScanDateWithShop(@Param("agentId") Long agentId, @Param("scanDate") LocalDate scanDate);
}
