package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    /**
     * Per-product Sold totals for one LMT's one day, for stock
     * reconciliation (LMT Phase C). Sums across every shop visited that
     * day — safe against double-counting because the V19 unique index
     * (agent_id, product_id, sale_date, customer_shop_id, transaction_type)
     * guarantees at most one SALE row per shop per product per day; summing
     * across shops is exactly the correct daily total, not an inflation of
     * it. Each row is [productId, totalSoldQty].
     */
    @Query("SELECT si.product.id, SUM(si.quantity) FROM SaleItem si " +
           "WHERE si.agentId = :agentId AND si.saleDate = :saleDate " +
           "AND si.transactionType = com.dawnbread.attendance.entity.TransactionType.SALE " +
           "GROUP BY si.product.id")
    List<Object[]> sumSoldQuantityByAgentAndDate(@Param("agentId") Long agentId, @Param("saleDate") LocalDate saleDate);
}
