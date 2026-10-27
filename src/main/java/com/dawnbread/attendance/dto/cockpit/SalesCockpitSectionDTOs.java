package com.dawnbread.attendance.dto.cockpit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response shapes for the Sales cockpit's lower sections (step 2):
 * Returns, Shop coverage, Salesman scorecard, LMT stock, Products.
 * Every number was computed by the database. Money rules as everywhere:
 * net = SALE value - RETURN value; Return Before Expiry is units only.
 */
public final class SalesCockpitSectionDTOs {

    private SalesCockpitSectionDTOs() {
    }

    // ------------------------------------------------------------ Returns

    /** returnPercent = returnValue / saleValue * 100 (0 when nothing was sold). */
    public record ReturnRate(Long id, String name, double saleValue, double returnValue, double returnPercent,
                             long returnedUnits) {
    }

    /** returnedUnits = expired returns (RETURN); rbeUnits = return before expiry (replaced). */
    public record ReturnDay(LocalDate date, long returnedUnits, long rbeUnits, double returnValue) {
    }

    public record ReturnsSection(LocalDateTime generatedAt, double saleValue, double returnValue, double returnPercent,
                                 double alertPercent, List<ReturnRate> byShop, List<ReturnRate> bySalesman,
                                 List<ReturnRate> byProduct, List<ReturnDay> daily) {
    }

    // ------------------------------------------------------------ Shop coverage

    /** Assigned shops of one salesman, visited at least once in the period vs not visited at all. */
    public record SalesmanCoverage(Long agentId, String name, String role, long assignedShops, long visitedShops,
                                   double coveragePercent) {
    }

    public record CoverageDay(LocalDate date, long visitedShops, long assignedShops, double coveragePercent) {
    }

    public record ReasonCount(String reason, long count) {
    }

    public record CoverageSection(LocalDateTime generatedAt, long assignedShops, long visitedShops,
                                  double coveragePercent, List<SalesmanCoverage> bySalesman, List<CoverageDay> trend,
                                  List<ReasonCount> feedbackReasons) {
    }

    /**
     * One heatmap row: a shop and its state on each of the 7 days.
     * State per day: "SCAN" (successful QR scan), "FEEDBACK" (visit without a
     * sale), "NONE" (not visited by the assigned salesman), "FUTURE" (day
     * hasn't happened yet).
     */
    public record HeatmapRow(Long shopId, String shopName, String salesman, int visitedDays, List<String> days) {
    }

    public record Heatmap(LocalDate weekStart, List<LocalDate> days, List<HeatmapRow> rows, int page, int size,
                          long totalShops) {
    }

    // ------------------------------------------------------------ Salesman scorecard

    /**
     * visits = distinct (shop, day) visits (successful scan or feedback);
     * flaggedVouchers = vouchers without a QR scan + off-location attempts;
     * avgFirstVisit / avgLastVisit = average over days with activity, "HH:mm".
     */
    public record Scorecard(Long agentId, String name, String role, long visits, long shopsCovered, long vouchers,
                            double saleValue, double returnValue, double netValue, double netPerVisit,
                            long flaggedVouchers, String avgFirstVisit, String avgLastVisit, long activeDays) {
    }

    public record ScorecardSection(LocalDateTime generatedAt, List<Scorecard> rows) {
    }

    /** One day of one salesman, for the detail panel. */
    public record SalesmanDay(LocalDate date, long visits, long vouchers, double netValue, String firstVisit,
                              String lastVisit) {
    }

    // ------------------------------------------------------------ LMT stock

    public record LmtSalesmanStock(Long agentId, String name, long opening, long sold, long returned, long unsold,
                                   long replaced, long missing, long stockDays) {
    }

    /** Stock days: OK = reconciled, nothing missing; SHORTAGE = reconciled with missing units; OPEN = not reconciled. */
    public record LmtHealth(long okDays, long shortageDays, long openDays) {
    }

    public record LmtStockSection(LocalDateTime generatedAt, boolean applicable, List<LmtSalesmanStock> bySalesman,
                                  LmtHealth health) {
    }

    // ------------------------------------------------------------ Products

    /** abcClass: A = products making up the top 80% of sales value, B = next 15%, C = the rest. */
    public record ProductRow(Long productId, String name, long soldUnits, double saleValue, double sharePercent,
                             double cumulativePercent, String abcClass) {
    }

    /** lastSold null = never sold. */
    public record SlowProduct(Long productId, String name, long soldUnits, double saleValue, LocalDate lastSold) {
    }

    public record ProductsSection(LocalDateTime generatedAt, double totalSaleValue, List<ProductRow> products,
                                  List<SlowProduct> slowProducts) {
    }
}
