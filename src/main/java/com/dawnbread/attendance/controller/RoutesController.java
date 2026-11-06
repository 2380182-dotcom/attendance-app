package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.config.CockpitBulkhead;
import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.routes.RoutesDTOs.RoutesData;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.RoutesService;
import com.dawnbread.attendance.service.RoutesViews;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Sales > Routes: which assigned shops were visited on a day or range.
 * ADMIN + SALES only (salesmen and HR get 403). Dates are Pakistan dates,
 * default today; a range is at most 92 days and cannot end after today.
 * Every request runs under the cockpits' shared limit of 2 at a time
 * (CockpitBulkhead) and reads one cached RoutesData (90 s per company +
 * range), so filters, sorting and the Excel file never run extra SQL.
 */
@RestController
@RequestMapping("/api/routes")
public class RoutesController {

    static final String[] ROLES = { "ADMIN", "SALES" };
    static final int MAX_DAYS = 92;

    @Autowired
    private RoutesService service;

    @Autowired
    private CockpitBulkhead bulkhead;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private Clock clock;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Object>> summary(@RequestParam(required = false) String startDate,
                                                       @RequestParam(required = false) String endDate) {
        return run(startDate, endDate, RoutesViews::summary);
    }

    @GetMapping("/not-visited")
    public ResponseEntity<ApiResponse<Object>> notVisited(@RequestParam(required = false) String startDate,
                                                          @RequestParam(required = false) String endDate,
                                                          @RequestParam(required = false) Long agentId,
                                                          @RequestParam(required = false) String type,
                                                          @RequestParam(required = false) String area,
                                                          @RequestParam(required = false) Integer minDaysSince,
                                                          @RequestParam(required = false) String sort,
                                                          @RequestParam(defaultValue = "asc") String dir,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "50") int size) {
        RoutesViews.Filter filter;
        try {
            filter = filter(agentId, type, area, minDaysSince, sort, dir);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        int boundedSize = Math.min(Math.max(size, 1), 100);
        int boundedPage = Math.max(page, 0);
        return run(startDate, endDate, data -> RoutesViews.page(data, filter, boundedPage, boundedSize));
    }

    @GetMapping("/unassigned")
    public ResponseEntity<ApiResponse<Object>> unassigned(@RequestParam(required = false) String startDate,
                                                          @RequestParam(required = false) String endDate) {
        return run(startDate, endDate, RoutesViews::unassigned);
    }

    /** The current filtered + sorted not-visited list as an Excel file (same parameters as /not-visited). */
    @GetMapping("/not-visited/export")
    public ResponseEntity<?> export(@RequestParam(required = false) String startDate,
                                    @RequestParam(required = false) String endDate,
                                    @RequestParam(required = false) Long agentId,
                                    @RequestParam(required = false) String type,
                                    @RequestParam(required = false) String area,
                                    @RequestParam(required = false) Integer minDaysSince,
                                    @RequestParam(required = false) String sort,
                                    @RequestParam(defaultValue = "asc") String dir) {
        if (!AccessControl.hasRole(request, ROLES)) {
            return forbidden();
        }
        LocalDate[] range;
        RoutesViews.Filter filter;
        try {
            range = parse(startDate, endDate, LocalDate.now(clock));
            filter = filter(agentId, type, area, minDaysSince, sort, dir);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        try {
            return bulkhead.run(() -> {
                RoutesData data = service.load(range[0], range[1]);
                byte[] file = RoutesViews.excel(data, filter, describe(data, filter));
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(RoutesViews.fileName(data)).build().toString())
                        .body(file);
            });
        } catch (CockpitBulkhead.BusyException busy) {
            return busy(busy);
        }
    }

    private ResponseEntity<ApiResponse<Object>> run(String startDate, String endDate, Function<RoutesData, Object> view) {
        if (!AccessControl.hasRole(request, ROLES)) {
            return forbidden();
        }
        LocalDate[] range;
        try {
            range = parse(startDate, endDate, LocalDate.now(clock));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success("Routes retrieved",
                    bulkhead.run(() -> view.apply(service.load(range[0], range[1])))));
        } catch (CockpitBulkhead.BusyException busy) {
            return busy(busy);
        }
    }

    static LocalDate[] parse(String startDate, String endDate, LocalDate today) {
        LocalDate end = parseDate(endDate, "endDate", today);
        LocalDate start = parseDate(startDate, "startDate", end);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("startDate must be on or before endDate");
        }
        if (end.isAfter(today)) {
            throw new IllegalArgumentException("endDate cannot be after today (" + today + ")");
        }
        if (end.toEpochDay() - start.toEpochDay() + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("The date range can be at most " + MAX_DAYS + " days");
        }
        return new LocalDate[] { start, end };
    }

    static RoutesViews.Filter filter(Long agentId, String type, String area, Integer minDaysSince, String sort, String dir) {
        String t = type == null || type.isBlank() || type.equalsIgnoreCase("ALL") ? null : type.trim().toUpperCase();
        if (t != null && !t.equals("LOCAL") && !t.equals("LMT")) {
            throw new IllegalArgumentException("type must be ALL, LOCAL or LMT");
        }
        if (minDaysSince != null && minDaysSince < 0) {
            throw new IllegalArgumentException("minDaysSince cannot be negative");
        }
        String s = sort == null || sort.isBlank() ? null : sort.trim();
        if (s != null && !RoutesViews.SORTS.contains(s)) {
            throw new IllegalArgumentException("sort must be one of " + String.join(", ", RoutesViews.SORTS.stream().sorted().toList()));
        }
        String a = area == null || area.isBlank() ? null : area.trim();
        return new RoutesViews.Filter(agentId, t, a, minDaysSince, s, "desc".equalsIgnoreCase(dir));
    }

    /** The Excel file's "Filters:" line. */
    private static String describe(RoutesData data, RoutesViews.Filter f) {
        List<String> parts = new ArrayList<>();
        if (f.agentId() != null) {
            data.shops().stream().filter(s -> f.agentId().equals(s.agentId())).findFirst()
                    .ifPresentOrElse(s -> parts.add("salesman " + s.salesman()), () -> parts.add("salesman #" + f.agentId()));
        }
        if (f.type() != null) parts.add("LMT".equals(f.type()) ? "LMT only" : "Local only");
        if (f.area() != null) parts.add("area " + f.area());
        if (f.minDaysSince() != null) parts.add("not visited for " + f.minDaysSince() + "+ days");
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    private static LocalDate parseDate(String value, String name, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be a date like 2026-10-25");
        }
    }

    private static <T> ResponseEntity<T> busy(CockpitBulkhead.BusyException busy) {
        @SuppressWarnings("unchecked")
        ResponseEntity<T> r = (ResponseEntity<T>) ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5").body(ApiResponse.error(busy.getMessage()));
        return r;
    }

    private static <T> ResponseEntity<T> forbidden() {
        @SuppressWarnings("unchecked")
        ResponseEntity<T> r = (ResponseEntity<T>) ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only administrators and the Sales team can view Routes."));
        return r;
    }
}
