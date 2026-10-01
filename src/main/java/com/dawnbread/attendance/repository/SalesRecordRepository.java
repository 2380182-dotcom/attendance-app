package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.dto.VoucherListItemDTO;
import com.dawnbread.attendance.dto.VoucherShopSummaryDTO;
import com.dawnbread.attendance.entity.SalesRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Postgres-safe pattern (see PostgresCompatibilityTest): a String param
    // compared only via "= '' OR LOWER(...)", never "IS NULL OR" — an
    // untyped null bound inside LOWER(CONCAT(...)) makes Postgres infer the
    // parameter as bytea ("function lower(bytea) does not exist"), a real
    // production outage H2 never catches since it accepts the untyped null
    // fine. Callers pass "" for "no filter", never null (see SalesService
    // .searchSales). LocalDate :date is fine as "IS NULL OR" — a typed
    // date parameter doesn't hit this inference problem (confirmed on
    // real Postgres by the same test).
    @Query("SELECT DISTINCT sr FROM SalesRecord sr LEFT JOIN FETCH sr.items JOIN FETCH sr.agent a WHERE " +
            "(:agentName = '' OR LOWER(a.name) LIKE LOWER(CONCAT('%', :agentName, '%'))) AND " +
            "(:date IS NULL OR sr.saleDate = :date) AND " +
            "(:storeName = '' OR LOWER(sr.storeName) LIKE LOWER(CONCAT('%', :storeName, '%')) OR LOWER(sr.location) LIKE LOWER(CONCAT('%', :storeName, '%'))) " +
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

    /**
     * Task 4 — Local/LMT Sales Voucher shop-list page. Every total here is
     * a database SUM/COUNT over SaleItem, grouped by shop (never Java-side
     * summing over loaded vouchers/items — the server has a 512MB RAM
     * limit). Section membership uses the role SNAPSHOTTED at submission
     * time (agentRoleAtSale) with a fallback to the live agent.role join
     * for every record that predates this field, so an agent's later role
     * change never retroactively moves an already-existing voucher.
     * :shopSearch is optional — "" means no filter (never null; see
     * searchSales's doc for why "= '' OR ..." replaces "IS NULL OR ..."
     * here — a real production outage on Postgres 15, invisible on H2).
     */
    @Query(value = "SELECT new com.dawnbread.attendance.dto.VoucherShopSummaryDTO(" +
            "sh.id, sh.shopCode, sh.shopName, sh.branch, COUNT(DISTINCT sr.id), " +
            "SUM(CASE WHEN si.transactionType = com.dawnbread.attendance.entity.TransactionType.SALE THEN si.totalPrice ELSE 0.0 END), " +
            "SUM(CASE WHEN si.transactionType = com.dawnbread.attendance.entity.TransactionType.RETURN THEN si.totalPrice ELSE 0.0 END), " +
            "SUM(CASE WHEN si.transactionType = com.dawnbread.attendance.entity.TransactionType.SALE THEN si.quantity ELSE 0 END), " +
            "MAX(sr.saleDate)) " +
            "FROM SalesRecord sr JOIN sr.items si JOIN sr.customerShop sh " +
            "WHERE COALESCE(sr.agentRoleAtSale, sr.agent.role) = :role " +
            "AND (:shopSearch = '' OR LOWER(sh.shopName) LIKE LOWER(CONCAT('%', :shopSearch, '%')) OR LOWER(sh.shopCode) LIKE LOWER(CONCAT('%', :shopSearch, '%'))) " +
            "GROUP BY sh.id, sh.shopCode, sh.shopName, sh.branch " +
            "ORDER BY MAX(sr.saleDate) DESC",
            countQuery = "SELECT COUNT(DISTINCT sh.id) FROM SalesRecord sr JOIN sr.customerShop sh " +
            "WHERE COALESCE(sr.agentRoleAtSale, sr.agent.role) = :role " +
            "AND (:shopSearch = '' OR LOWER(sh.shopName) LIKE LOWER(CONCAT('%', :shopSearch, '%')) OR LOWER(sh.shopCode) LIKE LOWER(CONCAT('%', :shopSearch, '%')))")
    Page<VoucherShopSummaryDTO> findShopVoucherSummaries(@Param("role") String role,
                                                          @Param("shopSearch") String shopSearch,
                                                          Pageable pageable);

    /**
     * Task 4 — one shop's paginated voucher list. Deliberately never
     * JOIN FETCHes sr.items (that would force Hibernate's in-memory
     * pagination, HHH90003004) — per-voucher totals come from the same
     * SaleItem GROUP BY aggregate technique as the shop-list query above.
     * This is a DTO projection with GROUP BY, so pagination happens as a
     * real LIMIT/OFFSET in the database.
     */
    @Query(value = "SELECT new com.dawnbread.attendance.dto.VoucherListItemDTO(" +
            "sr.id, sr.saleDate, sr.saleTime, sr.agent.name, " +
            "SUM(CASE WHEN si.transactionType = com.dawnbread.attendance.entity.TransactionType.SALE THEN si.totalPrice ELSE 0.0 END), " +
            "SUM(CASE WHEN si.transactionType = com.dawnbread.attendance.entity.TransactionType.RETURN THEN si.totalPrice ELSE 0.0 END), " +
            "SUM(CASE WHEN si.transactionType = com.dawnbread.attendance.entity.TransactionType.SALE THEN si.quantity ELSE 0 END), " +
            "sr.distanceFromShopMeters, sr.status) " +
            "FROM SalesRecord sr JOIN sr.items si " +
            "WHERE sr.customerShop.id = :shopId AND COALESCE(sr.agentRoleAtSale, sr.agent.role) = :role " +
            "GROUP BY sr.id, sr.saleDate, sr.saleTime, sr.agent.name, sr.distanceFromShopMeters, sr.status " +
            "ORDER BY sr.saleDate DESC, sr.saleTime DESC",
            countQuery = "SELECT COUNT(DISTINCT sr.id) FROM SalesRecord sr " +
            "WHERE sr.customerShop.id = :shopId AND COALESCE(sr.agentRoleAtSale, sr.agent.role) = :role")
    Page<VoucherListItemDTO> findVoucherListForShop(@Param("shopId") Long shopId, @Param("role") String role, Pageable pageable);

    /**
     * Task 4 — the single-voucher detail page and PDF. Fetch-joins items
     * (and their product) since this loads exactly one record by id, never
     * paginated — safe under the same to-one-vs-collection distinction
     * that makes the paginated queries above avoid it. Cross-tenant ids
     * transparently come back empty (SecurityInterceptor enables the
     * Hibernate tenantFilter for every request before this runs) — the
     * caller maps that to 404, never 403.
     */
    @Query("SELECT DISTINCT sr FROM SalesRecord sr " +
            "LEFT JOIN FETCH sr.items si LEFT JOIN FETCH si.product " +
            "LEFT JOIN FETCH sr.customerShop LEFT JOIN FETCH sr.agent " +
            "WHERE sr.id = :id")
    Optional<SalesRecord> findDetailById(@Param("id") Long id);
}
