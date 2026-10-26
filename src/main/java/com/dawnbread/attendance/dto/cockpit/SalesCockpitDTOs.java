package com.dawnbread.attendance.dto.cockpit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response shapes for the Sales cockpit. Grouped in one file because they
 * are small, read-only and only ever used together. Every number in them
 * was computed by the database (SUM / COUNT / GROUP BY).
 *
 * Money rules (same as vouchers): net = SALE value - RETURN value. Return
 * Before Expiry is tracked in units only and never changes any money total.
 * "Units" always means SOLD units.
 */
public final class SalesCockpitDTOs {

    private SalesCockpitDTOs() {
    }

    /** One point of a sparkline or daily series. */
    public record DayValue(LocalDate date, double value) {
    }

    /**
     * One highlight card. deltaPercent is null when there is nothing to
     * compare with (previous value 0). higherIsBetter tells the UI whether
     * an increase is green or red.
     */
    public record HighlightCard(String key, String label, double value, Double secondaryValue,
                                String context, List<DayValue> sparkline, Double deltaPercent,
                                String deltaLabel, Boolean higherIsBetter, String meaning, String section) {
    }

    public record Highlights(LocalDateTime generatedAt, LocalDate today, List<HighlightCard> cards) {
    }

    public record AlertItem(String label, String value) {
    }

    /** severity: CRITICAL, WARNING, INFO. section: the cockpit section the "View" link jumps to. */
    public record Alert(String key, String severity, String title, String detail, int count,
                        String section, List<AlertItem> items) {
    }

    public record Attention(LocalDateTime generatedAt, double returnAlertPercent, List<Alert> alerts) {
    }

    public record DailySales(LocalDate date, double saleValue, double returnValue, double netValue,
                             long soldUnits, double saleAvg7) {
    }

    public record SegmentSales(String segment, double saleValue, double returnValue, double netValue, long soldUnits) {
    }

    public record AgentSales(Long agentId, String name, String role, double saleValue, double returnValue,
                             double netValue, long soldUnits, long vouchers) {
    }

    /** areaId null = vouchers with no shop/area (old mart-agent vouchers). */
    public record AreaSales(Long areaId, String name, String tse, String srTse, String asm,
                            double saleValue, double returnValue, double netValue, long soldUnits) {
    }

    public record ProductSales(Long productId, String name, long soldUnits, double saleValue, double returnValue) {
    }

    public record SalesSection(LocalDateTime generatedAt, List<DailySales> daily, List<SegmentSales> bySegment,
                               List<AgentSales> bySalesman, List<AreaSales> byArea, List<ProductSales> topProducts) {
    }

    /** Drill-down row: one voucher with database-computed totals. */
    public record VoucherRow(Long id, LocalDate saleDate, java.time.LocalTime saleTime, String agentName, String role,
                             String shopName, String areaName, double saleValue, double returnValue,
                             double netValue, long soldUnits, long rbeUnits) {
    }

    public record Option(Long id, String name, String extra) {
    }

    public record FilterOptions(List<Option> areas, List<Option> salesmen) {
    }
}
