package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.AttendanceDTO;
import com.dawnbread.attendance.dto.CheckInRequest;
import com.dawnbread.attendance.dto.CheckOutRequest;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    /**
     * Check-in an agent
     */
    @PostMapping("/checkin")
    public ResponseEntity<ApiResponse<AttendanceDTO>> checkIn(@RequestBody CheckInRequest request) {
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
     * Check-out an agent
     */
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<AttendanceDTO>> checkOut(@RequestBody CheckOutRequest request) {
        try {
            Attendance attendance = attendanceService.checkOut(request);
            return ResponseEntity.ok(ApiResponse.success("Check-out successful", convertToDTO(attendance)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get all attendance records
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getAllAttendance() {
        List<Attendance> attendances = attendanceService.getAllAttendance();
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Attendance records retrieved", dtos));
    }

    /**
     * Get attendance by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AttendanceDTO>> getAttendanceById(@PathVariable Long id) {
        return attendanceService.getAttendanceById(id)
                .map(attendance -> ResponseEntity.ok(ApiResponse.success("Attendance found", convertToDTO(attendance))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Attendance not found with id: " + id)));
    }

    /**
     * Get attendance by agent
     */
    @GetMapping("/agent/{agentId}")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getAttendanceByAgent(@PathVariable Long agentId) {
        List<Attendance> attendances = attendanceService.getAttendanceByAgent(agentId);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Attendance records for agent", dtos));
    }

    /**
     * Get today's attendance for an agent
     */
    @GetMapping("/agent/{agentId}/today")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getTodayAttendanceForAgent(@PathVariable Long agentId) {
        List<Attendance> attendances = attendanceService.getTodayAttendanceForAgent(agentId);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Today's attendance for agent", dtos));
    }

    /**
     * Get attendance by date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getAttendanceByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<Attendance> attendances = attendanceService.getAttendanceByDateRange(startDate, endDate);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Attendance records for date range", dtos));
    }

    /**
     * Get attendance for an agent by date range
     */
    @GetMapping("/agent/{agentId}/date-range")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getAttendanceForAgentByDateRange(
            @PathVariable Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<Attendance> attendances = attendanceService.getAttendanceForAgentByDateRange(agentId, startDate, endDate);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Attendance records for agent by date range", dtos));
    }

    /**
     * Get attendance by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getAttendanceByStatus(@PathVariable String status) {
        List<Attendance> attendances = attendanceService.getAttendanceByStatus(status);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Attendance records with status: " + status, dtos));
    }

    /**
     * Get open attendance (not checked out)
     */
    @GetMapping("/open")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getOpenAttendance() {
        List<Attendance> attendances = attendanceService.getOpenAttendance();
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Open attendance records", dtos));
    }

    /**
     * Get today's attendance report
     */
    @GetMapping("/report/today")
    public ResponseEntity<ApiResponse<List<Object[]>>> getTodayAttendanceReport() {
        List<Object[]> report = attendanceService.getTodayAttendanceReport();
        return ResponseEntity.ok(ApiResponse.success("Today's attendance report", report));
    }

    /**
     * Get daily attendance report
     */
    @GetMapping("/report/daily")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getDailyAttendanceReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Attendance> attendances = attendanceService.getDailyAttendanceReport(date);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Daily attendance report", dtos));
    }

    /**
     * Get monthly attendance for an agent
     */
    @GetMapping("/agent/{agentId}/monthly")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getMonthlyAttendanceForAgent(
            @PathVariable Long agentId,
            @RequestParam int year,
            @RequestParam int month) {
        List<Attendance> attendances = attendanceService.getMonthlyAttendanceForAgent(agentId, year, month);
        List<AttendanceDTO> dtos = attendances.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Monthly attendance for agent", dtos));
    }

    /**
     * Get attendance statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Object[]>> getAttendanceStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        Object[] stats = attendanceService.getAttendanceStatistics(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Attendance statistics", stats));
    }

    /**
     * Check if agent is checked in
     */
    @GetMapping("/agent/{agentId}/is-checked-in")
    public ResponseEntity<ApiResponse<Boolean>> isAgentCheckedIn(@PathVariable Long agentId) {
        boolean isCheckedIn = attendanceService.isAgentCheckedIn(agentId);
        return ResponseEntity.ok(ApiResponse.success("Agent check-in status", isCheckedIn));
    }

    /**
     * Get current check-in for an agent
     */
    @GetMapping("/agent/{agentId}/current")
    public ResponseEntity<ApiResponse<AttendanceDTO>> getCurrentCheckIn(@PathVariable Long agentId) {
        return attendanceService.getCurrentCheckIn(agentId)
                .map(attendance -> ResponseEntity.ok(ApiResponse.success("Current check-in", convertToDTO(attendance))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("No active check-in found for agent")));
    }

    /**
     * Count attendance for an agent
     */
    @GetMapping("/agent/{agentId}/count")
    public ResponseEntity<ApiResponse<Long>> countAttendanceByAgent(@PathVariable Long agentId) {
        long count = attendanceService.countAttendanceByAgent(agentId);
        return ResponseEntity.ok(ApiResponse.success("Total attendance count for agent", count));
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
                attendance.getDistanceFromMart()
        );
    }
}
