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

    /**
     * LMT flow refinement: Returned is now computed the same way Sold is —
     * summed from that day's per-shop RETURN line items, never a manual
     * day-level entry. Same double-counting safety as sumSoldQuantityByAgentAndDate
     * (the V19 unique index still guarantees at most one RETURN row per
     * shop/product/day; summing across shops is correct, not inflation).
     */
    @Query("SELECT si.product.id, SUM(si.quantity) FROM SaleItem si " +
           "WHERE si.agentId = :agentId AND si.saleDate = :saleDate " +
           "AND si.transactionType = com.dawnbread.attendance.entity.TransactionType.RETURN " +
           "GROUP BY si.product.id")
    List<Object[]> sumReturnedQuantityByAgentAndDate(@Param("agentId") Long agentId, @Param("saleDate") LocalDate saleDate);

    /**
     * Per-shop Returned breakdown for the Sales Department's reconciliation
     * report — never exposed to the LMT's own self-service endpoints.
     * Joins through SalesRecord (SaleItem.customerShopId itself has no FK —
     * see SaleItem's own field comment; the real shop identity lives on
     * its parent SalesRecord). Each row is
     * [shopId, shopCode, shopName, productId, productName, totalReturnedQty].
     */
    @Query("SELECT sr.customerShop.id, sr.customerShop.shopCode, sr.customerShop.shopName, " +
           "si.product.id, si.product.name, SUM(si.quantity) " +
           "FROM SaleItem si JOIN si.salesRecord sr " +
           "WHERE si.agentId = :agentId AND si.saleDate BETWEEN :startDate AND :endDate " +
           "AND si.transactionType = com.dawnbread.attendance.entity.TransactionType.RETURN " +
           "AND sr.customerShop IS NOT NULL " +
           "GROUP BY sr.customerShop.id, sr.customerShop.shopCode, sr.customerShop.shopName, si.product.id, si.product.name")
    List<Object[]> sumReturnedQuantityByShopForAgentAndDateRange(@Param("agentId") Long agentId,
                                                                  @Param("startDate") LocalDate startDate,
                                                                  @Param("endDate") LocalDate endDate);
}
