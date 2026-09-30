package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.SalesRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalesRecordRepository extends JpaRepository<SalesRecord, Long> {

    // Every method below is called by a report/dashboard/export path that
    // then iterates record.getItems() — items is LAZY with no batch-fetch
    // configured, so without an explicit JOIN FETCH each of these was an
    // N+1 (one extra query per SalesRecord returned). DISTINCT avoids the
    // duplicate parent rows a one-to-many join otherwise produces.

    @Query("SELECT DISTINCT sr FROM SalesRecord sr LEFT JOIN FETCH sr.items LEFT JOIN FETCH sr.agent " +
            "WHERE sr.agent.id = :agentId ORDER BY sr.saleDate DESC, sr.saleTime DESC")
    List<SalesRecord> findByAgentIdOrderBySaleDateDescSaleTimeDesc(@Param("agentId") Long agentId);

    @Query("SELECT DISTINCT sr FROM SalesRecord sr LEFT JOIN FETCH sr.items LEFT JOIN FETCH sr.agent WHERE sr.saleDate = :date")
    List<SalesRecord> findBySaleDate(@Param("date") LocalDate date);

    @Query("SELECT DISTINCT sr FROM SalesRecord sr LEFT JOIN FETCH sr.items LEFT JOIN FETCH sr.agent " +
            "WHERE sr.saleDate BETWEEN :start AND :end")
    List<SalesRecord> findBySaleDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT DISTINCT sr FROM SalesRecord sr LEFT JOIN FETCH sr.items WHERE sr.agent.id = :agentId AND sr.saleDate = :date")
    List<SalesRecord> findByAgentIdAndSaleDate(@Param("agentId") Long agentId, @Param("date") LocalDate date);

    @Query("SELECT DISTINCT sr FROM SalesRecord sr LEFT JOIN FETCH sr.items WHERE sr.agent.id = :agentId AND sr.saleDate BETWEEN :start AND :end " +
            "ORDER BY sr.saleDate DESC, sr.saleTime DESC")
    List<SalesRecord> findByAgentIdAndSaleDateBetween(@Param("agentId") Long agentId,
                                                       @Param("start") LocalDate start,
                                                       @Param("end") LocalDate end);

    @Query("SELECT DISTINCT sr FROM SalesRecord sr LEFT JOIN FETCH sr.items JOIN FETCH sr.agent a WHERE " +
            "(:agentName IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :agentName, '%'))) AND " +
            "(:date IS NULL OR sr.saleDate = :date) AND " +
            "(:storeName IS NULL OR LOWER(sr.storeName) LIKE LOWER(CONCAT('%', :storeName, '%')) OR LOWER(sr.location) LIKE LOWER(CONCAT('%', :storeName, '%'))) " +
            "ORDER BY sr.saleDate DESC, sr.saleTime DESC")
    List<SalesRecord> searchSales(@Param("agentName") String agentName,
                                  @Param("date") LocalDate date,
                                  @Param("storeName") String storeName);

    @Query("SELECT DISTINCT sr FROM SalesRecord sr LEFT JOIN FETCH sr.items JOIN FETCH sr.agent a " +
            "WHERE a.department = :department ORDER BY sr.saleDate DESC")
    List<SalesRecord> findByAgentDepartment(@Param("department") String department);

    /**
     * Phase D: the Agent-vs-LMT report split. a.role is the authoritative
     * discriminator (not sale_items.customer_shop_id's -1 sentinel, which
     * is an implementation artifact of the shop-visit flow, not the actual
     * business fact of who made the sale) — see SalesService.
     */
    @Query("SELECT DISTINCT sr FROM SalesRecord sr LEFT JOIN FETCH sr.items JOIN FETCH sr.agent a WHERE sr.saleDate = :date AND a.role = :role")
    List<SalesRecord> findBySaleDateAndAgentRole(@Param("date") LocalDate date, @Param("role") String role);

    @Query("SELECT DISTINCT sr FROM SalesRecord sr LEFT JOIN FETCH sr.items JOIN FETCH sr.agent a " +
            "WHERE sr.saleDate BETWEEN :start AND :end AND a.role = :role")
    List<SalesRecord> findBySaleDateBetweenAndAgentRole(@Param("start") LocalDate start,
                                                         @Param("end") LocalDate end,
                                                         @Param("role") String role);

    @Query("SELECT DISTINCT sr FROM SalesRecord sr LEFT JOIN FETCH sr.items WHERE sr.requestId = :requestId")
    Optional<SalesRecord> findByRequestId(@Param("requestId") String requestId);

    /**
     * Task 3 "voucher created?" column — bulk, not per-row, to avoid N+1
     * across a page of scan results: every (agentId, shopId, saleDate)
     * combination that has at least one SalesRecord in this range. Raw
     * Object[] rows rather than an entity projection — the caller only
     * needs the key to build a lookup set, not the full record. The date
     * has to be part of the key, not just the range bound — the "voucher
     * created?" column must match on the SAME day as each scan, not "any
     * day in range."
     */
    @Query("SELECT DISTINCT sr.agent.id, sr.customerShop.id, sr.saleDate FROM SalesRecord sr " +
            "WHERE sr.saleDate BETWEEN :start AND :end AND sr.customerShop IS NOT NULL")
    List<Object[]> findAgentShopDatePairsWithVoucherBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Task 3 "voucher without scan": every SalesRecord with a shop in this date range, fetch-joined for the report's display fields. */
    @Query("SELECT DISTINCT sr FROM SalesRecord sr LEFT JOIN FETCH sr.customerShop " +
            "WHERE sr.saleDate BETWEEN :start AND :end AND sr.customerShop IS NOT NULL " +
            "ORDER BY sr.saleDate DESC, sr.saleTime DESC")
    List<SalesRecord> findBySaleDateBetweenWithShopNotNull(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
