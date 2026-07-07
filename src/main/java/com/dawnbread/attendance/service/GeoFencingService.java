package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.GeoFenceResponse;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.GeoFenceLog;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.GeoFenceLogRepository;
import com.dawnbread.attendance.repository.MartRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class GeoFencingService {

    @Autowired
    private GeoFenceLogRepository geoFenceLogRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private MartRepository martRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private NotificationService notificationService;

    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth's radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // meters
    }

    /**
     * Geofence events never create or finalize an Attendance row — GPS
     * proximity alone proves nothing about identity. The day's very first
     * entry only flips a "you're here, go verify" prompt
     * (ENTERED_PENDING_VERIFICATION); the row is created exclusively by a
     * real, successful face verification through AttendanceController.checkIn
     * (POST /api/attendance/checkin, faceVerified=true). Every other entry
     * and every exit is a live presence signal for Sales only. Check-out is
     * exclusively finalized via the "End Duty" button
     * (AttendanceController.checkOut / AttendanceService.checkOut), never here.
     *
     * A GeoFenceLog transition (not "is currently inside/outside") drives the
     * ENTERED/EXITED decision so a stationary agent pinging every ~10s doesn't
     * spam a new log/notification on every call — only real boundary crossings do.
     */
    public GeoFenceResponse checkGeoFenceStatus(Long agentId, Double latitude, Double longitude) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));

        List<Mart> marts = martRepository.findByIsActiveTrue();
        Mart insideMart = null;

        for (Mart mart : marts) {
            if (Boolean.TRUE.equals(mart.getGeoFencingEnabled())) {
                double distance = calculateDistance(latitude, longitude, mart.getLatitude(), mart.getLongitude());
                if (distance <= mart.getRadius()) {
                    insideMart = mart;
                    break;
                }
            }
        }

        Optional<GeoFenceLog> lastLog = geoFenceLogRepository.findFirstByAgentIdOrderByCreatedAtDesc(agentId);
        boolean lastKnownInside = lastLog.isPresent() && "ENTERED".equals(lastLog.get().getAction());
        Long lastKnownMartId = lastLog.isPresent() && lastLog.get().getMart() != null
                ? lastLog.get().getMart().getId() : null;

        if (insideMart != null) {
            boolean sameMartAsLastKnown = lastKnownInside && Objects.equals(lastKnownMartId, insideMart.getId());
            if (sameMartAsLastKnown) {
                // No boundary crossing — still inside the same mart as last known. No-op.
                Optional<Attendance> openAttendance = attendanceRepository.findOpenAttendanceByAgentId(agentId);
                return new GeoFenceResponse("STAYED", "Agent is active inside " + insideMart.getName(),
                        openAttendance.orElse(null));
            }

            // Genuine ENTER transition.
            logGeoFenceEvent(agent, insideMart, "ENTERED", latitude, longitude);

            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
            boolean alreadyCheckedInToday = !attendanceRepository
                    .findByAgentIdAndCheckInTimeBetween(agentId, startOfDay, endOfDay).isEmpty();

            if (!alreadyCheckedInToday) {
                // Geofence entry alone must NEVER create the attendance row — it
                // only proves GPS proximity, not identity. Duty starts only once
                // the agent completes a real face verification through the app
                // (POST /api/attendance/checkin with faceVerified=true, gated by
                // FaceVerificationModal). This just prompts that step; no
                // Attendance row exists yet, and none will unless verification
                // actually succeeds — no pending/flagged row is created either.
                notificationService.sendPushNotification(agentId, "Verification Required",
                        "You've arrived at " + insideMart.getName() + ". Open the app and verify your face to start your shift.");

                return new GeoFenceResponse("ENTERED_PENDING_VERIFICATION",
                        "Arrived at " + insideMart.getName() + " — face verification required to check in", null);
            } else {
                // Already checked in today (whether still open or already
                // finalized via End Duty) — this re-entry is Sales-notification
                // only, no attendance change.
                notificationService.sendGeoFenceActivityNotification(agent, insideMart.getName(), "ENTERED");
                return new GeoFenceResponse("ENTERED_LOGGED",
                        "Presence logged at " + insideMart.getName() + " (already checked in today)", null);
            }
        } else {
            if (!lastKnownInside) {
                // No boundary crossing — still outside, as last known. No-op.
                return new GeoFenceResponse("OUTSIDE", "Agent is outside all geo-fences", null);
            }

            // Genuine EXIT transition. Never finalizes check-out — that only
            // happens via the End Duty button — this is a presence signal only.
            Mart lastMart = lastLog.get().getMart();
            logGeoFenceEvent(agent, lastMart, "EXITED", latitude, longitude);
            notificationService.sendGeoFenceActivityNotification(agent, lastMart.getName(), "EXITED");

            Optional<Attendance> openAttendance = attendanceRepository.findOpenAttendanceByAgentId(agentId);
            return new GeoFenceResponse("EXITED_LOGGED",
                    "Presence logged leaving " + lastMart.getName() + " — check out with End Duty when your shift ends",
                    openAttendance.orElse(null));
        }
    }

    private void logGeoFenceEvent(Agent agent, Mart mart, String action, Double latitude, Double longitude) {
        GeoFenceLog log = new GeoFenceLog();
        log.setAgent(agent);
        log.setMart(mart);
        log.setAction(action);
        log.setLatitude(latitude);
        log.setLongitude(longitude);
        log.setCreatedAt(LocalDateTime.now());
        geoFenceLogRepository.save(log);
    }

    public List<GeoFenceLog> getLogsForAgent(Long agentId) {
        return geoFenceLogRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
    }

    public List<GeoFenceLog> getAllLogs() {
        return geoFenceLogRepository.findAll();
    }
}
