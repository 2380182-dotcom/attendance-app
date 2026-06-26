package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.CheckInRequest;
import com.dawnbread.attendance.dto.CheckOutRequest;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.repository.AttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AttendanceService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AgentService agentService;

    @Autowired
    private MartService martService;

    @Autowired
    private NotificationService notificationService;

    /**
     * Calculate distance between two coordinates (Haversine formula)
     * @param lat1 First latitude
     * @param lon1 First longitude
     * @param lat2 Second latitude
     * @param lon2 Second longitude
     * @return Distance in meters
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth's radius in kilometers
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // Convert to meters
        
        return distance;
    }

    /**
     * Check-in an agent
     */
    public Attendance checkIn(CheckInRequest request) {
        // Validate agent
        Agent agent = agentService.getAgentById(request.getAgentId())
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + request.getAgentId()));
        
        // Validate mart
        Mart mart = martService.getMartById(request.getMartId())
                .orElseThrow(() -> new RuntimeException("Mart not found with id: " + request.getMartId()));
        
        // Check if already checked in
        Optional<Attendance> openAttendance = attendanceRepository.findOpenAttendanceByAgentId(request.getAgentId());
        if (openAttendance.isPresent()) {
            throw new RuntimeException("Agent already checked in. Please check out first.");
        }
        
        // Calculate distance from mart
        double distance = calculateDistance(request.getLatitude(), request.getLongitude(), 
                                           mart.getLatitude(), mart.getLongitude());
        
        // Determine status
        String status = "IN";
        LocalDateTime now = LocalDateTime.now();
        
        // If distance is greater than mart radius, mark as LATE
        if (distance > mart.getRadius() * 1000) { // Convert radius from km to meters
            status = "LATE";
        }
        
        // Check if it's before 9:30 AM (can adjust as needed)
        LocalTime checkInTime = now.toLocalTime();
        if (checkInTime.isAfter(LocalTime.of(9, 30))) {
            status = "LATE";
        }
        
        // Create attendance record
        Attendance attendance = new Attendance();
        attendance.setAgent(agent);
        attendance.setMart(mart);
        attendance.setCheckInTime(now);
        attendance.setStatus(status);
        attendance.setCheckInLatitude(request.getLatitude());
        attendance.setCheckInLongitude(request.getLongitude());
        attendance.setDistanceFromMart(distance);
        
        Attendance saved = attendanceRepository.save(attendance);
        notificationService.sendCheckInNotification(saved);
        return saved;
    }

    /**
     * Check-out an agent
     */
    public Attendance checkOut(CheckOutRequest request) {
        // Find open attendance
        Attendance attendance = attendanceRepository.findOpenAttendanceByAgentId(request.getAgentId())
                .orElseThrow(() -> new RuntimeException("No active check-in found for agent id: " + request.getAgentId()));
        
        // Update check-out details
        attendance.setCheckOutTime(LocalDateTime.now());
        attendance.setCheckOutLatitude(request.getLatitude());
        attendance.setCheckOutLongitude(request.getLongitude());
        
        Attendance saved = attendanceRepository.save(attendance);
        notificationService.sendCheckOutNotification(saved);
        return saved;
    }

    /**
     * Get all attendance records
     */
    public List<Attendance> getAllAttendance() {
        return attendanceRepository.findAll();
    }

    /**
     * Get attendance by ID
     */
    public Optional<Attendance> getAttendanceById(Long id) {
        return attendanceRepository.findById(id);
    }

    /**
     * Get attendance for an agent
     */
    public List<Attendance> getAttendanceByAgent(Long agentId) {
        return attendanceRepository.findByAgentId(agentId);
    }

    /**
     * Get attendance for a mart
     */
    public List<Attendance> getAttendanceByMart(Long martId) {
        return attendanceRepository.findByMartId(martId);
    }

    /**
     * Get today's attendance for an agent
     */
    public List<Attendance> getTodayAttendanceForAgent(Long agentId) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        return attendanceRepository.findByAgentIdAndCheckInTimeBetween(agentId, startOfDay, endOfDay);
    }

    /**
     * Get attendance by date range
     */
    public List<Attendance> getAttendanceByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return attendanceRepository.findByCheckInTimeBetween(startDate, endDate);
    }

    /**
     * Get attendance for an agent by date range
     */
    public List<Attendance> getAttendanceForAgentByDateRange(Long agentId, LocalDateTime startDate, LocalDateTime endDate) {
        return attendanceRepository.findByAgentIdAndCheckInTimeBetween(agentId, startDate, endDate);
    }

    /**
     * Get attendance by status
     */
    public List<Attendance> getAttendanceByStatus(String status) {
        return attendanceRepository.findByStatus(status);
    }

    /**
     * Get open attendance (not checked out)
     */
    public List<Attendance> getOpenAttendance() {
        return attendanceRepository.findOpenAttendance();
    }

    /**
     * Get today's attendance report
     */
    public List<Object[]> getTodayAttendanceReport() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        return attendanceRepository.getTodayAttendanceReport(startOfDay, endOfDay);
    }

    /**
     * Get attendance statistics for a date range
     */
    public Object[] getAttendanceStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        return attendanceRepository.getAttendanceStatistics(startDate, endDate);
    }

    /**
     * Get attendance summary for an agent
     */
    public Object[] getAttendanceSummaryForAgent(Long agentId, LocalDateTime startDate, LocalDateTime endDate) {
        return attendanceRepository.getAttendanceSummaryForAgent(agentId, startDate, endDate);
    }

    /**
     * Get daily attendance report
     */
    public List<Attendance> getDailyAttendanceReport(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);
        return attendanceRepository.findByCheckInTimeBetween(startOfDay, endOfDay);
    }

    /**
     * Get monthly attendance for an agent
     */
    public List<Attendance> getMonthlyAttendanceForAgent(Long agentId, int year, int month) {
        return attendanceRepository.getMonthlyAttendanceReportForAgent(agentId, year, month);
    }

    /**
     * Count attendance for an agent
     */
    public long countAttendanceByAgent(Long agentId) {
        return attendanceRepository.countByAgentId(agentId);
    }

    /**
     * Count attendance for a mart
     */
    public long countAttendanceByMart(Long martId) {
        return attendanceRepository.countByMartId(martId);
    }

    /**
     * Check if an agent is currently checked in
     */
    public boolean isAgentCheckedIn(Long agentId) {
        return attendanceRepository.findOpenAttendanceByAgentId(agentId).isPresent();
    }

    /**
     * Get current check-in for an agent
     */
    public Optional<Attendance> getCurrentCheckIn(Long agentId) {
        return attendanceRepository.findOpenAttendanceByAgentId(agentId);
    }
}
