package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.service.ExcelExportService;
import com.dawnbread.attendance.service.HrAgentAttendanceCsvService;
import com.dawnbread.attendance.service.SalesAgentCsvService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ExcelExportController {

    @Autowired
    private ExcelExportService excelExportService;

    @Autowired
    private HrAgentAttendanceCsvService hrAgentAttendanceCsvService;

    @Autowired
    private SalesAgentCsvService salesAgentCsvService;

    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportReports(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) throws IOException {

        ByteArrayInputStream in = excelExportService.exportAllReports(date, agentId, year, month);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=attendance_report.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @GetMapping("/export/agent/{agentId}")
    public ResponseEntity<InputStreamResource> exportAgentReport(
            @PathVariable Long agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws IOException {

        ByteArrayInputStream in = excelExportService.exportAgentReport(agentId, startDate, endDate);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=agent_attendance_history.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    /**
     * HR: per-agent attendance CSV, date-ranged. Never includes sales data — see
     * HrAgentAttendanceCsvService's data-boundary note.
     */
    @GetMapping("/hr/agent-attendance-csv")
    public ResponseEntity<ByteArrayResource> exportHrAgentAttendanceCsv(
            @RequestParam Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        byte[] csv = hrAgentAttendanceCsvService.exportAgentAttendanceCsv(agentId, from, to);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=agent_" + agentId + "_attendance_" + from + "_to_" + to + ".csv");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(new ByteArrayResource(csv));
    }

    /**
     * Sales: per-agent sales CSV, date-ranged. Never includes attendance data — see
     * SalesAgentCsvService's data-boundary note.
     */
    @GetMapping("/sales/agent-sales-csv")
    public ResponseEntity<ByteArrayResource> exportSalesAgentSalesCsv(
            @RequestParam Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        byte[] csv = salesAgentCsvService.exportAgentSalesCsv(agentId, from, to);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=agent_" + agentId + "_sales_" + from + "_to_" + to + ".csv");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(new ByteArrayResource(csv));
    }
}
