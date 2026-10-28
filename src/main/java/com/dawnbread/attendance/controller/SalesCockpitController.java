package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.cockpit.CockpitFilter;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.SalesCockpitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.function.Function;

/**
 * Sales cockpit — one endpoint per page section (not one per chart), plus a
 * paginated voucher drill-down and the filter dropdown options.
 * ADMIN + SALES only (salesmen and HR get 403), tenant-scoped through the
 * Hibernate tenant filter, Pakistan-time "today" via the injected Clock.
 *
 * Shared filters: startDate, endDate (ISO dates, default the last 30 days
 * ending today), segment (ALL / LOCAL / LMT, default ALL), areaId, agentId.
 */
@RestController
@RequestMapping("/api/cockpit/sales")
public class SalesCockpitController {

    static final String[] ROLES = { "ADMIN", "SALES" };
    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    private static final Set<String> SEGMENTS = Set.of("ALL", "LOCAL", "LMT");

    @Autowired
    private SalesCockpitService service;

    @Autowired
    private com.dawnbread.attendance.service.SalesCockpitSectionService sections;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private com.dawnbread.attendance.config.CockpitBulkhead bulkhead;

    @Autowired
    private Clock clock;

    @GetMapping("/highlights")
    public ResponseEntity<ApiResponse<Object>> highlights(@RequestParam(required = false) String startDate,
                                                          @RequestParam(required = false) String endDate,
                                                          @RequestParam(required = false) String segment,
                                                          @RequestParam(required = false) Long areaId,
                                                          @RequestParam(required = false) Long agentId) {
        return run(startDate, endDate, segment, areaId, agentId, service::highlights);
    }

    @GetMapping("/attention")
    public ResponseEntity<ApiResponse<Object>> attention(@RequestParam(required = false) String startDate,
                                                         @RequestParam(required = false) String endDate,
                                                         @RequestParam(required = false) String segment,
                                                         @RequestParam(required = false) Long areaId,
                                                         @RequestParam(required = false) Long agentId) {
        return run(startDate, endDate, segment, areaId, agentId, service::attention);
    }

    @GetMapping("/sales")
    public ResponseEntity<ApiResponse<Object>> sales(@RequestParam(required = false) String startDate,
                                                     @RequestParam(required = false) String endDate,
                                                     @RequestParam(required = false) String segment,
                                                     @RequestParam(required = false) Long areaId,
                                                     @RequestParam(required = false) Long agentId) {
        return run(startDate, endDate, segment, areaId, agentId, service::salesSection);
    }

    /** Drill-down: the vouchers behind a clicked bar / slice / row. noArea=true = the "No area" bucket. */
    @GetMapping("/vouchers")
    public ResponseEntity<ApiResponse<Object>> vouchers(@RequestParam(required = false) String startDate,
                                                        @RequestParam(required = false) String endDate,
                                                        @RequestParam(required = false) String segment,
                                                        @RequestParam(required = false) Long areaId,
                                                        @RequestParam(required = false) Long agentId,
                                                        @RequestParam(required = false) Long productId,
                                                        @RequestParam(defaultValue = "false") boolean noArea,
                                                        @RequestParam(required = false) Long shopId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "25") int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        int boundedPage = Math.max(page, 0);
        return run(startDate, endDate, segment, areaId, agentId,
                f -> service.vouchers(f, productId, noArea, shopId, boundedPage, boundedSize));
    }

    // ---------------------------------------------------------------- step 2 sections

    @GetMapping("/returns")
    public ResponseEntity<ApiResponse<Object>> returns(@RequestParam(required = false) String startDate,
                                                       @RequestParam(required = false) String endDate,
                                                       @RequestParam(required = false) String segment,
                                                       @RequestParam(required = false) Long areaId,
                                                       @RequestParam(required = false) Long agentId) {
        return run(startDate, endDate, segment, areaId, agentId, sections::returns);
    }

    @GetMapping("/coverage")
    public ResponseEntity<ApiResponse<Object>> coverage(@RequestParam(required = false) String startDate,
                                                        @RequestParam(required = false) String endDate,
                                                        @RequestParam(required = false) String segment,
                                                        @RequestParam(required = false) Long areaId,
                                                        @RequestParam(required = false) Long agentId) {
        return run(startDate, endDate, segment, areaId, agentId, sections::coverage);
    }

    /** Shop x day grid for one week (weekOf = any date in it, default the end date), 20 shops per page. */
    @GetMapping("/coverage/heatmap")
    public ResponseEntity<ApiResponse<Object>> heatmap(@RequestParam(required = false) String startDate,
                                                       @RequestParam(required = false) String endDate,
                                                       @RequestParam(required = false) String segment,
                                                       @RequestParam(required = false) Long areaId,
                                                       @RequestParam(required = false) Long agentId,
                                                       @RequestParam(required = false) String weekOf,
                                                       @RequestParam(defaultValue = "0") int page) {
        LocalDate week;
        try {
            week = weekOf == null || weekOf.isBlank() ? null : LocalDate.parse(weekOf.trim());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("weekOf must be a date like 2026-10-25"));
        }
        int boundedPage = Math.max(page, 0);
        return run(startDate, endDate, segment, areaId, agentId, f -> sections.heatmap(f, week, boundedPage));
    }

    @GetMapping("/salesmen")
    public ResponseEntity<ApiResponse<Object>> salesmen(@RequestParam(required = false) String startDate,
                                                        @RequestParam(required = false) String endDate,
                                                        @RequestParam(required = false) String segment,
                                                        @RequestParam(required = false) Long areaId,
                                                        @RequestParam(required = false) Long agentId) {
        return run(startDate, endDate, segment, areaId, agentId, sections::scorecard);
    }

    /** One salesman's day-by-day detail for the side panel. Another company's salesman simply has no rows. */
    @GetMapping("/salesmen/{salesmanId}/days")
    public ResponseEntity<ApiResponse<Object>> salesmanDays(@PathVariable Long salesmanId,
                                                            @RequestParam(required = false) String startDate,
                                                            @RequestParam(required = false) String endDate,
                                                            @RequestParam(required = false) String segment,
                                                            @RequestParam(required = false) Long areaId) {
        return run(startDate, endDate, segment, areaId, null, f -> sections.salesmanDays(f, salesmanId));
    }

    @GetMapping("/lmt-stock")
    public ResponseEntity<ApiResponse<Object>> lmtStock(@RequestParam(required = false) String startDate,
                                                        @RequestParam(required = false) String endDate,
                                                        @RequestParam(required = false) String segment,
                                                        @RequestParam(required = false) Long areaId,
                                                        @RequestParam(required = false) Long agentId) {
        return run(startDate, endDate, segment, areaId, agentId, sections::lmtStock);
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<Object>> products(@RequestParam(required = false) String startDate,
                                                        @RequestParam(required = false) String endDate,
                                                        @RequestParam(required = false) String segment,
                                                        @RequestParam(required = false) Long areaId,
                                                        @RequestParam(required = false) Long agentId) {
        return run(startDate, endDate, segment, areaId, agentId, sections::products);
    }

    @GetMapping("/filter-options")
    public ResponseEntity<ApiResponse<Object>> filterOptions() {
        if (!AccessControl.hasRole(request, ROLES)) {
            return forbidden();
        }
        return ResponseEntity.ok(ApiResponse.success("Filter options retrieved", service.filterOptions()));
    }

    private ResponseEntity<ApiResponse<Object>> run(String startDate, String endDate, String segment, Long areaId,
                                                    Long agentId, Function<CockpitFilter, Object> section) {
        if (!AccessControl.hasRole(request, ROLES)) {
            return forbidden();
        }
        CockpitFilter filter;
        try {
            filter = parse(startDate, endDate, segment, areaId, agentId, LocalDate.now(clock));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success("Cockpit data retrieved", bulkhead.run(() -> section.apply(filter))));
        } catch (com.dawnbread.attendance.config.CockpitBulkhead.BusyException busy) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(org.springframework.http.HttpHeaders.RETRY_AFTER, "5")
                    .body(ApiResponse.error(busy.getMessage()));
        }
    }

    static CockpitFilter parse(String startDate, String endDate, String segment, Long areaId, Long agentId,
                               LocalDate today) {
        LocalDate end = parseDate(endDate, "endDate", today);
        LocalDate start = parseDate(startDate, "startDate", end.minusDays(DEFAULT_DAYS - 1));
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("startDate must be on or before endDate");
        }
        if (end.toEpochDay() - start.toEpochDay() + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("The date range can be at most " + MAX_DAYS + " days");
        }
        String seg = segment == null || segment.isBlank() ? "ALL" : segment.trim().toUpperCase();
        if (!SEGMENTS.contains(seg)) {
            throw new IllegalArgumentException("segment must be ALL, LOCAL or LMT");
        }
        return new CockpitFilter(start, end, seg, areaId, agentId);
    }

    private static LocalDate parseDate(String value, String name, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be a date like 2026-10-25");
        }
    }

    private static ResponseEntity<ApiResponse<Object>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only administrators and the Sales team can view the Sales cockpit."));
    }
}
