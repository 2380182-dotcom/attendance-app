package com.dawnbread.attendance.config;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.service.AgentService;
import com.dawnbread.attendance.service.FaceVerificationService;
import com.dawnbread.attendance.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerConfig.class);

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AgentService agentService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private FaceVerificationService faceVerificationService;

    @Scheduled(cron = "0 0 0 * * *")
    public void resetDailyFaceVerificationCounts() {
        logger.info("Resetting daily face verification counts at midnight");
        faceVerificationService.resetAllDailyVerificationCounts();
    }

    /**
     * Safety-net fallback only — the End Duty button is the only *official*
     * way a day's check-out is finalized (see AttendanceService.checkOut,
     * called exclusively from the dashboard's End Duty flow). If an agent
     * simply forgets to press it, this closes the row at midnight so
     * tomorrow's reports aren't corrupted by a permanently-open attendance
     * record — but it's explicitly labeled MISSED_END_DUTY (not a normal
     * checkout status) and never marked face-verified, so HR can tell the
     * difference between a real End-Duty close-out and a forgotten one.
     */
    @Scheduled(cron = "0 59 23 * * *") // Runs at 11:59 PM daily
    public void autoCheckoutAllAgents() {
        logger.info("Running daily auto-checkout scheduler at 11:59 PM");
        List<Attendance> openAttendances = attendanceRepository.findOpenAttendance();
        for (Attendance attendance : openAttendances) {
            attendance.setCheckOutTime(LocalDateTime.now());
            attendance.setStatus("MISSED_END_DUTY");
            attendance.setFaceVerifiedCheckout(false);
            attendanceRepository.save(attendance);
            logger.info("Closed out agent id: {} at midnight — End Duty was never pressed.", attendance.getAgent().getId());

            try {
                notificationService.sendCheckOutNotification(attendance);
                notificationService.sendPushNotification(
                    attendance.getAgent().getId(),
                    "Shift Auto-Closed",
                    "Your shift was closed automatically at 11:59 PM because End Duty wasn't pressed. Contact your admin if this looks wrong."
                );
            } catch (Exception ex) {
                logger.error("Failed to dispatch notifications for auto-checkout: ", ex);
            }
        }
    }

    /**
     * Mark agents who had no check-ins today as ABSENT at 10 PM daily
     */
    @Scheduled(cron = "0 0 22 * * *") // Runs at 10:00 PM daily
    public void markAbsentAgents() {
        logger.info("Running daily absenteeism status verification scheduler at 10:00 PM");
        LocalDateTime start = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime end = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        List<Agent> agents = agentService.getAllAgents();

        for (Agent agent : agents) {
            List<Attendance> attendances = attendanceRepository.findByAgentIdAndCheckInTimeBetween(agent.getId(), start, end);
            if (attendances.isEmpty()) {
                notificationService.sendAbsenteeismNotification(agent);
                logger.info("Agent {} (id: {}) marked as absent today.", agent.getName(), agent.getId());
            }
        }
    }
}
