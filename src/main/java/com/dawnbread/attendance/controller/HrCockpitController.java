package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.config.CockpitBulkhead;
import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.cockpit.CockpitFilter;
import com.dawnbread.attendance.dto.cockpit.HrFilter;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.HrCockpitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Function;

/**
 * HR cockpit — mart agents' attendance on one page. One endpoint per
 * section. ADMIN + HR only (salesmen and the Sales team get 403),
 * tenant-scoped (explicit tenant_id in every query), Pakistan-time "today",
 * shared 2-at-a-time limit with the Sales cockpit.
 *
 * Filters: startDate, endDate (ISO, default last 30 days), department
 * (exact name), martId.
 */
@RestController
@RequestMapping("/api/cockpit/hr")
public class HrCockpitController {

    static final String[] ROLES = { "ADMIN", "HR" };
    static final int MAX_DEPARTMENT = 100;

    @Autowired
    private HrCockpitService service;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private CockpitBulkhead bulkhead;

    @Autowired
    private Clock clock;

    @GetMapping("/highlights")
    public ResponseEntity<ApiResponse<Object>> highlights(@RequestParam(required = false) String startDate,
                                                          @RequestParam(required = false) String endDate,
                                                          @RequestParam(required = false) String department,
                                                          @RequestParam(required = false) Long martId) {
        return run(startDate, endDate, department, martId, service::highlights);
    }

    @GetMapping("/attention")
    public ResponseEntity<ApiResponse<Object>> attention(@RequestParam(required = false) String startDate,
                                                         @RequestParam(required = false) String endDate,
                                                         @RequestParam(required = false) String department,
                                                         @RequestParam(required = false) Long martId) {
        return run(startDate, endDate, department, martId, service::attention);
    }

    @GetMapping("/filter-options")
    public ResponseEntity<ApiResponse<Object>> filterOptions() {
        if (!AccessControl.hasRole(request, ROLES)) {
            return forbidden();
        }
        return ResponseEntity.ok(ApiResponse.success("Filter options retrieved", service.filterOptions()));
    }

    private ResponseEntity<ApiResponse<Object>> run(String startDate, String endDate, String department, Long martId,
                                                    Function<HrFilter, Object> section) {
        if (!AccessControl.hasRole(request, ROLES)) {
            return forbidden();
        }
        HrFilter filter;
        try {
            // Same date rules as the Sales cockpit (default 30 days, start <= end, max 366 days).
            CockpitFilter dates = SalesCockpitController.parse(startDate, endDate, null, null, null, LocalDate.now(clock));
            String dept = department == null ? "" : department.trim();
            if (dept.length() > MAX_DEPARTMENT) {
                throw new IllegalArgumentException("department is too long");
            }
            filter = new HrFilter(dates.startDate(), dates.endDate(), dept, martId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success("Cockpit data retrieved", bulkhead.run(() -> section.apply(filter))));
        } catch (CockpitBulkhead.BusyException busy) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "5")
                    .body(ApiResponse.error(busy.getMessage()));
        }
    }

    private static ResponseEntity<ApiResponse<Object>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only administrators and HR can view the HR cockpit."));
    }
}
