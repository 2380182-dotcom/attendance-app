package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.LmtDailyStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LmtDailyStockRepository extends JpaRepository<LmtDailyStock, Long> {

    Optional<LmtDailyStock> findByAgentIdAndStockDate(Long agentId, LocalDate stockDate);

    List<LmtDailyStock> findByStockDateBetween(LocalDate startDate, LocalDate endDate);

    List<LmtDailyStock> findByAgentIdAndStockDateBetween(Long agentId, LocalDate startDate, LocalDate endDate);
}
