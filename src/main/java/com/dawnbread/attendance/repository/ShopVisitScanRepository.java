package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.ShopVisitScan;
import com.dawnbread.attendance.entity.ShopVisitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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

    /**
     * Task 3 — server-side paginated + filtered "QR / Shop Visits" report.
     * Joins Agent on the denormalized agentId (no mapped relation) purely
     * to filter by role; a to-one fetch join on customerShop, never a
     * collection, so this is safe to combine with Pageable (only a
     * collection fetch-join forces Hibernate's in-memory pagination).
     * Every filter parameter is optional. :role and :shopSearch are Strings
     * — "" means no filter, never null (see PostgresCompatibilityTest: a
     * null String bound inside LOWER(CONCAT(...)) or compared via "=" makes
     * Postgres infer the parameter as bytea and throw "function lower
     * (bytea) does not exist" — a real production outage, invisible on H2,
     * which accepts the untyped null fine). :agentId stays "IS NULL OR ..."
     * — a typed Long parameter doesn't hit this inference problem
     * (confirmed on real Postgres by the same test).
     */
    @Query(value = "SELECT s FROM ShopVisitScan s LEFT JOIN FETCH s.customerShop sh JOIN Agent a ON a.id = s.agentId " +
            "WHERE s.scanDate BETWEEN :startDate AND :endDate " +
            "AND (:agentId IS NULL OR s.agentId = :agentId) " +
            "AND (:role = '' OR a.role = :role) " +
            "AND (:shopSearch = '' OR LOWER(sh.shopName) LIKE LOWER(CONCAT('%', :shopSearch, '%')) OR LOWER(sh.shopCode) LIKE LOWER(CONCAT('%', :shopSearch, '%'))) " +
            "AND (:failedOnly = false OR s.visitStatus <> com.dawnbread.attendance.entity.ShopVisitStatus.SUCCESS) " +
            "ORDER BY s.scanDate DESC, s.scanTime DESC",
            countQuery = "SELECT COUNT(s) FROM ShopVisitScan s LEFT JOIN s.customerShop sh JOIN Agent a ON a.id = s.agentId " +
            "WHERE s.scanDate BETWEEN :startDate AND :endDate " +
            "AND (:agentId IS NULL OR s.agentId = :agentId) " +
            "AND (:role = '' OR a.role = :role) " +
            "AND (:shopSearch = '' OR LOWER(sh.shopName) LIKE LOWER(CONCAT('%', :shopSearch, '%')) OR LOWER(sh.shopCode) LIKE LOWER(CONCAT('%', :shopSearch, '%'))) " +
            "AND (:failedOnly = false OR s.visitStatus <> com.dawnbread.attendance.entity.ShopVisitStatus.SUCCESS)")
    Page<ShopVisitScan> findFiltered(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                      @Param("agentId") Long agentId, @Param("role") String role,
                                      @Param("shopSearch") String shopSearch, @Param("failedOnly") boolean failedOnly,
                                      Pageable pageable);

    /** Task 3 "Not Visited": every shop THIS agent scanned successfully on this date — the LMT per-salesman check. */
    @Query("SELECT DISTINCT s.customerShop.id FROM ShopVisitScan s " +
            "WHERE s.agentId = :agentId AND s.scanDate = :scanDate AND s.visitStatus = com.dawnbread.attendance.entity.ShopVisitStatus.SUCCESS AND s.customerShop IS NOT NULL")
    Set<Long> findSuccessfullyScannedShopIdsForAgentAndDate(@Param("agentId") Long agentId, @Param("scanDate") LocalDate scanDate);

    /** Local's "Not Visited": every shop ANY agent successfully scanned on this date (Local has no per-salesman assignment). */
    @Query("SELECT DISTINCT s.customerShop.id FROM ShopVisitScan s " +
            "WHERE s.scanDate = :scanDate AND s.visitStatus = com.dawnbread.attendance.entity.ShopVisitStatus.SUCCESS AND s.customerShop IS NOT NULL")
    Set<Long> findSuccessfullyScannedShopIdsForDate(@Param("scanDate") LocalDate scanDate);

    /**
     * Task 3 "voucher without scan" — bulk, not per-row: every (agentId,
     * shopId, scanDate) combination that has at least one SUCCESSFUL scan
     * in this date range, so the caller can build a lookup set instead of
     * one exists-query per SalesRecord.
     */
    @Query("SELECT s.agentId, s.customerShop.id, s.scanDate FROM ShopVisitScan s " +
            "WHERE s.scanDate BETWEEN :start AND :end AND s.visitStatus = com.dawnbread.attendance.entity.ShopVisitStatus.SUCCESS AND s.customerShop IS NOT NULL")
    List<Object[]> findSuccessfulScanKeysBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
