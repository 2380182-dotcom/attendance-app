package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.LmtDailyStockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LmtDailyStockItemRepository extends JpaRepository<LmtDailyStockItem, Long> {

    List<LmtDailyStockItem> findByLmtDailyStockId(Long lmtDailyStockId);
}
