package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.AbsenceDTO;
import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.AbsenceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Saved absences (mart agents) for HR history. Dates are Pakistan calendar dates. */
@RestController
@RequestMapping("/api/hr/absences")
public class AbsenceController {

    private static final int MAX_DAYS = 366;

    @Autowired
    private AbsenceService absenceService;

    @Autowired
    private HttpServletRequest request;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AbsenceDTO>>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long agentId) {
        if (!AccessControl.hasRole(request, "ADMIN", "HR")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Only Admin and HR can view absences."));
        }
        if (startDate.isAfter(endDate) || endDate.toEpochDay() - startDate.toEpochDay() >= MAX_DAYS) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Choose a range of at most " + MAX_DAYS + " days, start before end."));
        }
        return ResponseEntity.ok(ApiResponse.success("Absences retrieved", absenceService.getAbsences(startDate, endDate, agentId)));
    }
}
