package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.SalesRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SalesRecordRepository extends JpaRepository<SalesRecord, Long> {
    List<SalesRecord> findByAgentIdOrderBySaleDateDescSaleTimeDesc(Long agentId);
    List<SalesRecord> findBySaleDate(LocalDate date);
    List<SalesRecord> findBySaleDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT sr FROM SalesRecord sr WHERE sr.agent.id = :agentId AND sr.saleDate = :date")
    List<SalesRecord> findByAgentIdAndSaleDate(@Param("agentId") Long agentId, @Param("date") LocalDate date);

    @Query("SELECT sr FROM SalesRecord sr WHERE sr.agent.id = :agentId AND sr.saleDate BETWEEN :start AND :end " +
            "ORDER BY sr.saleDate DESC, sr.saleTime DESC")
    List<SalesRecord> findByAgentIdAndSaleDateBetween(@Param("agentId") Long agentId,
                                                       @Param("start") LocalDate start,
                                                       @Param("end") LocalDate end);

    @Query("SELECT sr FROM SalesRecord sr JOIN sr.agent a WHERE " +
            "(:agentName IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :agentName, '%'))) AND " +
            "(:date IS NULL OR sr.saleDate = :date) AND " +
            "(:storeName IS NULL OR LOWER(sr.storeName) LIKE LOWER(CONCAT('%', :storeName, '%')) OR LOWER(sr.location) LIKE LOWER(CONCAT('%', :storeName, '%'))) " +
            "ORDER BY sr.saleDate DESC, sr.saleTime DESC")
    List<SalesRecord> searchSales(@Param("agentName") String agentName,
                                  @Param("date") LocalDate date,
                                  @Param("storeName") String storeName);

    @Query("SELECT sr FROM SalesRecord sr JOIN sr.agent a WHERE a.department = :department ORDER BY sr.saleDate DESC")
    List<SalesRecord> findByAgentDepartment(@Param("department") String department);

    /**
     * Phase D: the Agent-vs-LMT report split. a.role is the authoritative
     * discriminator (not sale_items.customer_shop_id's -1 sentinel, which
     * is an implementation artifact of the shop-visit flow, not the actual
     * business fact of who made the sale) — see SalesService.
     */
    @Query("SELECT sr FROM SalesRecord sr JOIN sr.agent a WHERE sr.saleDate = :date AND a.role = :role")
    List<SalesRecord> findBySaleDateAndAgentRole(@Param("date") LocalDate date, @Param("role") String role);

    @Query("SELECT sr FROM SalesRecord sr JOIN sr.agent a WHERE sr.saleDate BETWEEN :start AND :end AND a.role = :role")
    List<SalesRecord> findBySaleDateBetweenAndAgentRole(@Param("start") LocalDate start,
                                                         @Param("end") LocalDate end,
                                                         @Param("role") String role);
}
