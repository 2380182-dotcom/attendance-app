package com.dawnbread.attendance.dto.routes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response shapes for Sales > Routes. Every assigned shop is due on each of
 * its salesman's working days in the range, except company holidays
 * ("due days"). A shop counts as visited on a due day when its assigned
 * salesman made a successful scan or a feedback there that day (the Sales
 * cockpit's rule). Type is LOCAL or LMT.
 */
public final class RoutesDTOs {

    private RoutesDTOs() {
    }

    public record Holiday(LocalDate date, String name) {
    }

    /** One active shop of an active salesman. lastVisit = latest visit on or before the range end (null = never). */
    public record ShopRow(Long shopId, String shopName, String shopCode, String area, Long agentId, String salesman,
                          String type, LocalDate lastVisit, int dueDays, int visitedDays) {
        public int missedDays() {
            return dueDays - visitedDays;
        }
    }

    /**
     * An active shop with no usable salesman. reason: NO_SALESMAN, SALESMAN_INACTIVE or NOT_A_SALESMAN.
     * Visits here are by any salesman (the QR Scanned Shops rule); due days skip company holidays only.
     */
    public record UnassignedRow(Long shopId, String shopName, String shopCode, String area, String reason,
                                String assignedName, LocalDate lastVisit, int dueDays, int visitedDays) {
        public int missedDays() {
            return dueDays - visitedDays;
        }
    }

    /** Everything the page is built from, for one company and date range (cached 90 s). */
    public record RoutesData(LocalDate from, LocalDate to, int rangeDays, List<Holiday> holidays, List<ShopRow> shops,
                             List<UnassignedRow> unassigned, LocalDateTime updatedAt) {
        public boolean singleDay() {
            return from.equals(to);
        }
    }

    /**
     * Per-salesman (and total) coverage. For one day the shop-day numbers are simply shops. dayOff = nothing
     * was due (the salesman's day off or a company holiday). coveragePercent is null when nothing was due.
     * shopsNotVisited = shops with at least one due day and no visit at all in the range.
     */
    public record SalesmanCoverage(Long agentId, String salesman, String type, int assignedShops, int due,
                                   int visited, int notVisited, Double coveragePercent, int shopsNotVisited,
                                   boolean dayOff) {
    }

    public record UnassignedSummary(int shops, int notVisited, int noSalesman, int salesmanInactive,
                                    int notASalesman) {
    }

    public record Option(Long id, String name, String type) {
    }

    public record Summary(LocalDate from, LocalDate to, boolean singleDay, int rangeDays, List<Holiday> holidays,
                          LocalDateTime updatedAt, SalesmanCoverage total, List<SalesmanCoverage> salesmen,
                          UnassignedSummary unassigned, List<String> areas, List<Option> salesmanOptions) {
    }

    /** One row of the not-visited list. daysSinceLastVisit counts from the range end; null = never visited. */
    public record NotVisitedRow(Long shopId, String shopName, String shopCode, String area, Long agentId,
                                String salesman, String type, LocalDate lastVisit, Integer daysSinceLastVisit,
                                int dueDays, int visitedDays, int missedDays) {
    }

    public record NotVisitedPage(List<NotVisitedRow> content, long totalElements, int page, int size,
                                 LocalDate from, LocalDate to, boolean singleDay, LocalDateTime updatedAt) {
    }

    public record UnassignedListRow(Long shopId, String shopName, String shopCode, String area, String reason,
                                    String assignedName, LocalDate lastVisit, Integer daysSinceLastVisit,
                                    int dueDays, int visitedDays, int missedDays) {
    }

    public record UnassignedList(List<UnassignedListRow> content, LocalDate from, LocalDate to, boolean singleDay,
                                 LocalDateTime updatedAt) {
    }
}
