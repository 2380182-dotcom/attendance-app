package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.AttendanceWithShiftDTO;
import com.dawnbread.attendance.dto.CheckInRequest;
import com.dawnbread.attendance.dto.CheckOutRequest;
import com.dawnbread.attendance.dto.FaceVerificationStatusDTO;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.repository.AttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Autowired
    private FaceVerificationService faceVerificationService;

    @Autowired
    private com.dawnbread.attendance.repository.FaceVerificationLogRepository faceVerificationLogRepository;

    @Autowired
    private ShiftValidationService shiftValidationService;

    // DIAG(2026-08-10): OFF by default on purpose — every currently-stored
    // faceEmbedding was captured through a since-confirmed-broken TFLite
    // pipeline (constant output regardless of input; see the face-model
    // investigation), so every agent's reference is the same non-
    // discriminative vector right now. Flipping this on before that's fixed
    // AND every agent has re-enrolled would lock out 100% of check-ins.
    // The geofence gate below has no such dependency and is unconditional.
    // Turn on only once the model fix is verified and re-enrollment
    // coverage is confirmed; remove this flag (and hardcode "true") once
    // that's durably true and this has been live a while.
    @Value("${face.verification.hard-gate.enabled:false}")
    private boolean faceHardGateEnabled;

    /**
     * The server never sees a face image — on-device verification is a
     * deliberate architectural choice (see FaceVerificationService's own
     * docs). But CheckInRequest/CheckOutRequest.faceVerified is a plain
     * client-supplied boolean, and until this method existed it was stored
     * verbatim with no corroboration at all: any authenticated agent could
     * call the API directly with "faceVerified": true and no on-device
     * match ever ran, defeating the one control this whole subsystem exists
     * to enforce. This cross-checks the claim against FaceVerificationLog —
     * populated independently by POST /attendance/face-result, which the
     * mobile app calls right before check-in/out on a real successful
     * verification — within a short trailing window.
     */
    private boolean corroboratedFaceVerification(Long agentId, Boolean clientClaim) {
        if (!Boolean.TRUE.equals(clientClaim)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return faceVerificationLogRepository
                .findByAgentIdAndVerificationTimeBetween(agentId, now.minusMinutes(5), now)
                .stream()
                .anyMatch(log -> Boolean.TRUE.equals(log.getSuccess()));
    }

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
        Agent agent = agentService.getAgentById(request.getAgentId())
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + request.getAgentId()));

        Mart mart = martService.getMartById(request.getMartId())
                .orElseThrow(() -> new RuntimeException("Mart not found with id: " + request.getMartId()));

        Optional<Attendance> openAttendance = attendanceRepository.findOpenAttendanceByAgentId(request.getAgentId());
        if (openAttendance.isPresent()) {
            throw new RuntimeException("Agent already checked in. Please check out first.");
        }

        double distance = calculateDistance(request.getLatitude(), request.getLongitude(),
                mart.getLatitude(), mart.getLongitude());

        // Hard gate — was previously only folded into the "LATE" status
        // below, which meant an agent outside the mart's radius still
        // checked in successfully. mart.getGeoFencingEnabled() is a genuine,
        // existing per-mart admin opt-out (defaults true); everything else
        // is unconditional and server-side, so it can't be bypassed by a
        // client that skips its own pre-flight check.
        if (Boolean.TRUE.equals(mart.getGeoFencingEnabled()) && distance > mart.getRadius()) {
            throw new RuntimeException(String.format(
                    "You must be within %.0fm of %s to check in — you are %.0fm away.",
                    mart.getRadius(), mart.getName(), distance));
        }

        boolean faceVerified = corroboratedFaceVerification(agent.getId(), request.getFaceVerified());
        if (faceHardGateEnabled
                && faceVerificationService.isFaceVerificationRequired(agent.getId(), "CHECKIN")
                && !faceVerified) {
            throw new RuntimeException(
                    "Face verification is required and was not confirmed. Please verify your face and try again.");
        }

        LocalDateTime now = LocalDateTime.now();
        AttendanceWithShiftDTO shiftResult = shiftValidationService.validateAttendanceWithShift(request.getAgentId(), now);

        // mart.getRadius() is stored in meters (see AdminMartScreen.js: "Radius
        // (meters)"), and calculateDistance() already returns meters — no unit
        // conversion needed here, matching GeoFencingService's own
        // distance <= mart.getRadius() comparison. Distance is no longer part
        // of this check — being outside the radius now rejects the request
        // outright (above) rather than merely downgrading status.
        String status = "IN";
        if ("LATE".equals(shiftResult.getShiftCompliance())) {
            status = "LATE";
        }
        if (!shiftResult.isWorkingDay()) {
            status = "NON_WORKING_DAY";
        }

        Attendance attendance = new Attendance();
        attendance.setAgent(agent);
        attendance.setMart(mart);
        attendance.setCheckInTime(now);
        attendance.setStatus(status);
        attendance.setCheckInLatitude(request.getLatitude());
        attendance.setCheckInLongitude(request.getLongitude());
        attendance.setDistanceFromMart(distance);
        attendance.setShiftStartTime(agent.getShiftStartTime());
        attendance.setShiftEndTime(agent.getShiftEndTime());
        attendance.setLateMinutes(shiftResult.getLateMinutes());
        attendance.setFaceVerifiedCheckin(faceVerified);

        // The open-attendance read above is a genuine check-then-act race —
        // two concurrent check-ins can both pass it before either commits.
        // ux_attendance_agent_open (V16) is the actual guard; this just turns
        // its violation into the same clean message the read-based check
        // above already produces, instead of a raw constraint-violation
        // stack trace reaching the client.
        Attendance saved;
        try {
            saved = attendanceRepository.save(attendance);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new RuntimeException("Agent already checked in. Please check out first.");
        }
        notificationService.sendCheckInNotification(saved);
        return saved;
    }

    /**
     * Check-out an agent
     */
    public Attendance checkOut(CheckOutRequest request) {
        Attendance attendance = attendanceRepository.findOpenAttendanceByAgentId(request.getAgentId())
                .orElseThrow(() -> new RuntimeException("No active check-in found for agent id: " + request.getAgentId()));

        // Same AND-gate as checkIn(), reusing the mart the agent originally
        // checked into (CheckOutRequest carries no martId of its own).
        Mart mart = attendance.getMart();
        if (mart != null && Boolean.TRUE.equals(mart.getGeoFencingEnabled())) {
            double distance = calculateDistance(request.getLatitude(), request.getLongitude(),
                    mart.getLatitude(), mart.getLongitude());
            if (distance > mart.getRadius()) {
                throw new RuntimeException(String.format(
                        "You must be within %.0fm of %s to check out — you are %.0fm away.",
                        mart.getRadius(), mart.getName(), distance));
            }
        }

        boolean faceVerified = corroboratedFaceVerification(request.getAgentId(), request.getFaceVerified());
        if (faceHardGateEnabled
                && faceVerificationService.isFaceVerificationRequired(request.getAgentId(), "CHECKOUT")
                && !faceVerified) {
            throw new RuntimeException(
                    "Face verification is required and was not confirmed. Please verify your face and try again.");
        }

        attendance.setCheckOutTime(LocalDateTime.now());
        attendance.setCheckOutLatitude(request.getLatitude());
        attendance.setCheckOutLongitude(request.getLongitude());
        attendance.setFaceVerifiedCheckout(faceVerified);

        Attendance saved = attendanceRepository.save(attendance);
        notificationService.sendCheckOutNotification(saved);
        return saved;
    }

    public void recordMidShiftVerification(Long agentId) {
        Attendance attendance = attendanceRepository.findOpenAttendanceByAgentId(agentId)
                .orElse(null);
        if (attendance != null) {
            attendance.setMidDayVerificationTime(LocalDateTime.now());
            attendanceRepository.save(attendance);
        }
    }

    public com.dawnbread.attendance.entity.FaceVerificationLog recordScheduledFaceResult(
            com.dawnbread.attendance.dto.FaceResultRequest request) {
        var log = faceVerificationService.recordFaceResult(request);
        if (Boolean.TRUE.equals(log.getSuccess())) {
            recordMidShiftVerification(request.getAgentId());
        }
        return log;
    }

    public List<AttendanceWithShiftDTO> getDailyReportWithShift(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(23, 59, 59);
        List<Attendance> records = attendanceRepository.findByCheckInTimeBetween(start, end);
        List<AttendanceWithShiftDTO> report = new ArrayList<>();

        for (Attendance att : records) {
            AttendanceWithShiftDTO dto = shiftValidationService.validateAttendanceWithShift(
                    att.getAgent().getId(), att.getCheckInTime());

            AttendanceWithShiftDTO.AttendanceDTO attDto = new AttendanceWithShiftDTO.AttendanceDTO();
            attDto.setId(att.getId());
            attDto.setAgentId(att.getAgent().getId());
            attDto.setAgentName(att.getAgent().getName());
            attDto.setCheckInTime(att.getCheckInTime());
            attDto.setCheckOutTime(att.getCheckOutTime());
            attDto.setStatus(att.getStatus());
            attDto.setLateMinutes(att.getLateMinutes());
            attDto.setFaceVerifiedCheckin(att.getFaceVerifiedCheckin());
            attDto.setFaceVerifiedCheckout(att.getFaceVerifiedCheckout());
            dto.setAttendance(attDto);
            dto.setFaceVerified(Boolean.TRUE.equals(att.getFaceVerifiedCheckin()));
            report.add(dto);
        }
        return report;
    }

    public FaceVerificationStatusDTO getVerificationStatusForAgent(Long agentId) {
        return faceVerificationService.getVerificationStatus(agentId);
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

    /**
     * Record mid-day face verification
     */
    public Attendance verifyMidDayFace(Long agentId) {
        Attendance attendance = attendanceRepository.findOpenAttendanceByAgentId(agentId)
                .orElseThrow(() -> new RuntimeException("No active check-in found for agent id: " + agentId + ". You must check in first."));
        
        attendance.setMidDayVerificationTime(LocalDateTime.now());
        return attendanceRepository.save(attendance);
    }
}
