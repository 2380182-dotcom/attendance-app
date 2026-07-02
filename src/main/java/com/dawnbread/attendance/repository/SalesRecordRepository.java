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
}
