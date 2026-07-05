package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates the HR per-agent attendance CSV report.
 *
 * Data-boundary note: this service is deliberately wired to ONLY AttendanceRepository
 * and AgentRepository. It has no dependency on SalesRecordRepository/SaleItem anywhere
 * in this class, so there is no code path — regardless of how the /reports/hr endpoint
 * is called — through which sales data could end up in this report. That is the actual
 * enforcement mechanism, not a UI-level filter.
 *
 * Known gap: this codebase has no attendance override/audit-trail feature yet (no
 * override reason is ever recorded against an Attendance row), so the "Admin Override
 * Reason" column is always blank today. Sales overrides ARE tracked (SalesRecord has
 * overrideReason/modifiedBy/modifiedAt) — see SalesAgentCsvService.
 */
@Service
public class HrAgentAttendanceCsvService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AgentRepository agentRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public byte[] exportAgentAttendanceCsv(Long agentId, LocalDate startDate, LocalDate endDate) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        List<Attendance> records = attendanceRepository.findByAgentIdAndCheckInTimeBetween(agentId, start, end);

        StringBuilder csv = new StringBuilder();
        csv.append(CsvUtil.row(
                "Date", "Agent ID", "Agent Name", "Mart", "Check-In Time", "Check-Out Time",
                "Status", "Late Minutes", "Face Verified (Check-In)", "Face Verified (Check-Out)",
                "Distance From Mart (m)", "Geofence Compliant", "Admin Override Reason"
        ));

        for (Attendance att : records) {
            Mart mart = att.getMart();
            Double distance = att.getDistanceFromMart();
            Double radius = mart != null ? mart.getRadius() : null;
            String geofenceCompliant = (distance != null && radius != null)
                    ? (distance <= radius ? "Yes" : "No")
                    : "";

            csv.append(CsvUtil.row(
                    att.getCheckInTime() != null ? att.getCheckInTime().format(DATE_FORMATTER) : "",
                    agent.getAgentId(),
                    agent.getName(),
                    formatMartName(mart),
                    att.getCheckInTime() != null ? att.getCheckInTime().format(TIME_FORMATTER) : "",
                    att.getCheckOutTime() != null ? att.getCheckOutTime().format(TIME_FORMATTER) : "",
                    att.getStatus() != null ? att.getStatus() : "",
                    att.getLateMinutes() != null ? att.getLateMinutes() : 0,
                    Boolean.TRUE.equals(att.getFaceVerifiedCheckin()) ? "Yes" : "No",
                    Boolean.TRUE.equals(att.getFaceVerifiedCheckout()) ? "Yes" : "No",
                    distance != null ? distance : "",
                    geofenceCompliant,
                    "" // no attendance override/audit-trail feature exists in this codebase yet
            ));
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String formatMartName(Mart mart) {
        if (mart == null) return "";
        String name = mart.getName() != null ? mart.getName() : "";
        return Boolean.FALSE.equals(mart.getIsActive()) ? name + " (Deleted)" : name;
    }
}
