package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.AttendanceDTO;
import com.dawnbread.attendance.dto.AttendanceWithShiftDTO;
import com.dawnbread.attendance.dto.CheckInRequest;
import com.dawnbread.attendance.dto.CheckOutRequest;
import com.dawnbread.attendance.dto.FaceVerificationStatusDTO;
import com.dawnbread.attendance.dto.FaceResultRequest;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * checkin/checkout/face-result/verify-midday/verify-scheduled stay open to any
 * authenticated role — they're self-service actions an agent performs for
 * themselves. Every other endpoint here reads attendance/location data, which
 * is either a cross-agent aggregate (management-only) or a specific agent's
 * record (self-or-management), per the same rationale as
 * NotificationController/FaceVerificationController.
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private static final String[] MANAGEMENT_ROLES = { "ADMIN", "HR", "SALES" };

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private com.dawnbread.attendance.service.FaceVerificationService faceVerificationService;

    @Autowired
    private HttpServletRequest request;

    private <T> ResponseEntity<ApiResponse<T>> managementOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only Admin, HR, or Sales can access this."));
    }

    private <T> ResponseEntity<ApiResponse<T>> selfOrManagementOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You can only view your own attendance records."));
    }

    private <T> ResponseEntity<ApiResponse<T>> selfOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You can only do this for yourself."));
    }

    /**
     * Check-in an agent. Self-only — confirmed every mobile caller passes the
     * logged-in agent's own id; no management-on-behalf-of flow exists.
     */
    @PostMapping("/checkin")
    public ResponseEntity<ApiResponse<AttendanceDTO>> checkIn(@RequestBody CheckInRequest request) {
        if (!AccessControl.isSelfOrRole(this.request, request.getAgentId())) {
            return selfOnly();
        }
        try {
            Attendance attendance = attendanceService.checkIn(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Check-in successful", convertToDTO(attendance)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Check-out an agent. Self-only — same rationale as checkIn().
     */
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<AttendanceDTO>> checkOut(@RequestBody CheckOutRequest request) {
        if (!AccessControl.isSelfOrRole(this.request, request.getAgentId())) {
            return selfOnly();
        }
        try {
            Attendance attendance = attendanceService.checkOut(request);
            return ResponseEntity.ok(ApiResponse.success("Check-out successful", convertToDTO(attendance)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get all attendance records — every agent, unscoped. Management-only.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getAllAttendance() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<Attendance> attendances = attendanceService.getAllAttendance();
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Attendance records retrieved", dtos));
    }

    /**
     * Get attendance by ID — arbitrary record, no agent scoping. Management-only.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AttendanceDTO>> getAttendanceById(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        return attendanceService.getAttendanceById(id)
                .map(attendance -> ResponseEntity.ok(ApiResponse.success("Attendance found", convertToDTO(attendance))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Attendance not found with id: " + id)));
    }

    /**
     * Get attendance by agent — full history. Self-or-management.
     */
    @GetMapping("/agent/{agentId}")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getAttendanceByAgent(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId, MANAGEMENT_ROLES)) {
            return selfOrManagementOnly();
        }
        List<Attendance> attendances = attendanceService.getAttendanceByAgent(agentId);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Attendance records for agent", dtos));
    }

    /**
     * Get today's attendance for an agent. Self-or-management.
     */
    @GetMapping("/agent/{agentId}/today")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getTodayAttendanceForAgent(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId, MANAGEMENT_ROLES)) {
            return selfOrManagementOnly();
        }
        List<Attendance> attendances = attendanceService.getTodayAttendanceForAgent(agentId);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Today's attendance for agent", dtos));
    }

    /**
     * Get attendance by date range — every agent, unscoped. Management-only.
     */
    @GetMapping("/date-range")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getAttendanceByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<Attendance> attendances = attendanceService.getAttendanceByDateRange(startDate, endDate);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Attendance records for date range", dtos));
    }

    /**
     * Get attendance for an agent by date range. Self-or-management.
     */
    @GetMapping("/agent/{agentId}/date-range")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getAttendanceForAgentByDateRange(
            @PathVariable Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (!AccessControl.isSelfOrRole(request, agentId, MANAGEMENT_ROLES)) {
            return selfOrManagementOnly();
        }
        List<Attendance> attendances = attendanceService.getAttendanceForAgentByDateRange(agentId, startDate, endDate);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Attendance records for agent by date range", dtos));
    }

    /**
     * Get attendance by status — every agent, unscoped. Management-only.
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getAttendanceByStatus(@PathVariable String status) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<Attendance> attendances = attendanceService.getAttendanceByStatus(status);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Attendance records with status: " + status, dtos));
    }

    /**
     * Get open attendance (not checked out) — confirmed caller: SalesDashboardScreen's
     * live agent map. Management-only, not bare Agent.
     */
    @GetMapping("/open")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getOpenAttendance() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<Attendance> attendances = attendanceService.getOpenAttendance();
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Open attendance records", dtos));
    }

    /**
     * Get today's attendance report — every agent, unscoped. Management-only.
     */
    @GetMapping("/report/today")
    public ResponseEntity<ApiResponse<List<Object[]>>> getTodayAttendanceReport() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<Object[]> report = attendanceService.getTodayAttendanceReport();
        return ResponseEntity.ok(ApiResponse.success("Today's attendance report", report));
    }

    /**
     * Get daily attendance report — every agent, unscoped. Management-only.
     */
    @GetMapping("/report/daily")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getDailyAttendanceReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<Attendance> attendances = attendanceService.getDailyAttendanceReport(date);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Daily attendance report", dtos));
    }

    /**
     * Get monthly attendance for an agent. Self-or-management.
     */
    @GetMapping("/agent/{agentId}/monthly")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getMonthlyAttendanceForAgent(
            @PathVariable Long agentId,
            @RequestParam int year,
            @RequestParam int month) {
        if (!AccessControl.isSelfOrRole(request, agentId, MANAGEMENT_ROLES)) {
            return selfOrManagementOnly();
        }
        List<Attendance> attendances = attendanceService.getMonthlyAttendanceForAgent(agentId, year, month);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Monthly attendance for agent", dtos));
    }

    /**
     * Get attendance statistics — every agent, unscoped. Management-only.
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Object[]>> getAttendanceStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        Object[] stats = attendanceService.getAttendanceStatistics(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Attendance statistics", stats));
    }

    /**
     * Check if agent is checked in. Self-or-management.
     */
    @GetMapping("/agent/{agentId}/is-checked-in")
    public ResponseEntity<ApiResponse<Boolean>> isAgentCheckedIn(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId, MANAGEMENT_ROLES)) {
            return selfOrManagementOnly();
        }
        boolean isCheckedIn = attendanceService.isAgentCheckedIn(agentId);
        return ResponseEntity.ok(ApiResponse.success("Agent check-in status", isCheckedIn));
    }

    /**
     * Get current check-in for an agent. Self-or-management.
     */
    @GetMapping("/agent/{agentId}/current")
    public ResponseEntity<ApiResponse<AttendanceDTO>> getCurrentCheckIn(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId, MANAGEMENT_ROLES)) {
            return selfOrManagementOnly();
        }
        return attendanceService.getCurrentCheckIn(agentId)
                .map(attendance -> ResponseEntity.ok(ApiResponse.success("Current check-in", convertToDTO(attendance))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("No active check-in found for agent")));
    }

    /**
     * Count attendance for an agent. Self-or-management.
     */
    @GetMapping("/agent/{agentId}/count")
    public ResponseEntity<ApiResponse<Long>> countAttendanceByAgent(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId, MANAGEMENT_ROLES)) {
            return selfOrManagementOnly();
        }
        long count = attendanceService.countAttendanceByAgent(agentId);
        return ResponseEntity.ok(ApiResponse.success("Total attendance count for agent", count));
    }

    /**
     * Record on-device face verification result from mobile app. Self-only.
     */
    @PostMapping("/face-result")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordFaceResult(
            @Valid @RequestBody FaceResultRequest request) {
        if (!AccessControl.isSelfOrRole(this.request, request.getAgentId())) {
            return selfOnly();
        }
        try {
            var log = faceVerificationService.recordFaceResult(request);

            // Update mid-shift attendance record when applicable
            if ("MIDSHIFT".equalsIgnoreCase(request.getCheckpointType())
                    && "PASS".equalsIgnoreCase(request.getVerificationResult())) {
                attendanceService.recordMidShiftVerification(request.getAgentId());
            }

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("logId", log.getId());
            data.put("success", log.getSuccess());
            data.put("confidenceScore", log.getSimilarityScore());
            return ResponseEntity.ok(ApiResponse.success("Face verification result recorded", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Record mid-day face verification (legacy — prefer /face-result with MIDSHIFT checkpoint).
     * Self-only.
     */
    @PostMapping("/verify-midday/{agentId}")
    public ResponseEntity<ApiResponse<AttendanceDTO>> verifyMidDayFace(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId)) {
            return selfOnly();
        }
        try {
            Attendance attendance = attendanceService.verifyMidDayFace(agentId);
            return ResponseEntity.ok(ApiResponse.success("Mid-day face verification recorded successfully", convertToDTO(attendance)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Self-only.
     */
    @PostMapping("/verify-scheduled")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyScheduled(
            @Valid @RequestBody FaceResultRequest request) {
        if (!AccessControl.isSelfOrRole(this.request, request.getAgentId())) {
            return selfOnly();
        }
        try {
            request.setCheckpointType("MIDSHIFT");
            var log = attendanceService.recordScheduledFaceResult(request);
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("verified", log.getSuccess());
            data.put("logId", log.getId());
            return ResponseEntity.ok(ApiResponse.success("Scheduled face verification recorded", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Daily report with shift compliance — every agent, unscoped. Management-only.
     */
    @GetMapping("/daily-report")
    public ResponseEntity<ApiResponse<List<AttendanceWithShiftDTO>>> getDailyReportWithShift(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        LocalDate target = date != null ? date : LocalDate.now();
        List<AttendanceWithShiftDTO> report = attendanceService.getDailyReportWithShift(target);
        return ResponseEntity.ok(ApiResponse.success("Daily attendance report with shift compliance", report));
    }

    /**
     * Verification status for an agent. Self-or-management.
     */
    @GetMapping("/verification-status/{agentId}")
    public ResponseEntity<ApiResponse<FaceVerificationStatusDTO>> getVerificationStatus(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId, MANAGEMENT_ROLES)) {
            return selfOrManagementOnly();
        }
        FaceVerificationStatusDTO status = attendanceService.getVerificationStatusForAgent(agentId);
        return ResponseEntity.ok(ApiResponse.success("Verification status retrieved", status));
    }

    // ===== Helper Methods =====
    private AttendanceDTO convertToDTO(Attendance attendance) {
        return new AttendanceDTO(
                attendance.getId(),
                attendance.getAgent().getId(),
                attendance.getAgent().getName(),
                attendance.getMart().getId(),
                attendance.getMart().getName(),
                attendance.getCheckInTime(),
                attendance.getCheckOutTime(),
                attendance.getStatus(),
                attendance.getCheckInLatitude(),
                attendance.getCheckInLongitude(),
                attendance.getCheckOutLatitude(),
                attendance.getCheckOutLongitude(),
                attendance.getDistanceFromMart(),
                attendance.getMidDayVerificationTime()
        );
    }
}
