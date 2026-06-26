package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.Notification;
import com.dawnbread.attendance.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AgentService agentService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");

    public Notification saveNotification(Notification notification) {
        notification.setCreatedAt(LocalDateTime.now());
        if (notification.getIsRead() == null) {
            notification.setIsRead(false);
        }
        return notificationRepository.save(notification);
    }

    public void sendCheckInNotification(Attendance attendance) {
        String agentName = attendance.getAgent().getName();
        String martName = attendance.getMart().getName();
        String timeStr = attendance.getCheckInTime().format(formatter);
        boolean isLate = "LATE".equalsIgnoreCase(attendance.getStatus());

        // SALES
        String salesMsg = isLate 
            ? "⚠️ " + agentName + " is running late to " + martName
            : "✅ " + agentName + " checked in at " + martName + " at " + timeStr;
        Notification salesNotif = new Notification();
        salesNotif.setAgent(attendance.getAgent());
        salesNotif.setAgentName(agentName);
        salesNotif.setMessage(salesMsg);
        salesNotif.setType("CHECK_IN");
        salesNotif.setDepartment("SALES");
        saveNotification(salesNotif);
        logger.info("[SALES NOTIFICATION] {}", salesMsg);

        // HR
        String hrMsg = isLate
            ? "⚠️ " + agentName + " is LATE! Check-in: " + timeStr
            : "📋 " + agentName + " checked in at " + timeStr;
        Notification hrNotif = new Notification();
        hrNotif.setAgent(attendance.getAgent());
        hrNotif.setAgentName(agentName);
        hrNotif.setMessage(hrMsg);
        hrNotif.setType(isLate ? "LATE" : "CHECK_IN");
        hrNotif.setDepartment("HR");
        saveNotification(hrNotif);
        logger.info("[HR NOTIFICATION] {}", hrMsg);
    }

    public void sendCheckOutNotification(Attendance attendance) {
        String agentName = attendance.getAgent().getName();
        String martName = attendance.getMart().getName();
        String timeStr = attendance.getCheckOutTime().format(formatter);

        // SALES
        String salesMsg = "✅ " + agentName + " checked out from " + martName + " at " + timeStr;
        Notification salesNotif = new Notification();
        salesNotif.setAgent(attendance.getAgent());
        salesNotif.setAgentName(agentName);
        salesNotif.setMessage(salesMsg);
        salesNotif.setType("CHECK_OUT");
        salesNotif.setDepartment("SALES");
        saveNotification(salesNotif);
        logger.info("[SALES NOTIFICATION] {}", salesMsg);

        // HR
        String hrMsg = "📋 " + agentName + " checked out at " + timeStr;
        Notification hrNotif = new Notification();
        hrNotif.setAgent(attendance.getAgent());
        hrNotif.setAgentName(agentName);
        hrNotif.setMessage(hrMsg);
        hrNotif.setType("CHECK_OUT");
        hrNotif.setDepartment("HR");
        saveNotification(hrNotif);
        logger.info("[HR NOTIFICATION] {}", hrMsg);
    }

    public void sendAbsenteeismNotification(Agent agent) {
        String agentName = agent.getName();
        String msg = "❌ " + agentName + " has no check-in today";
        
        Notification hrNotif = new Notification();
        hrNotif.setAgent(agent);
        hrNotif.setAgentName(agentName);
        hrNotif.setMessage(msg);
        hrNotif.setType("ABSENT");
        hrNotif.setDepartment("HR");
        saveNotification(hrNotif);
        logger.info("[HR NOTIFICATION] [ABSENTEEISM] {}", msg);

        // Push notify to Agent too
        sendPushNotification(agent.getId(), "❌ Absent Notice", "You were marked as absent today. No check-in was registered.");
    }

    public void sendPushNotification(Long agentId, String title, String body) {
        Agent agent = agentService.getAgentById(agentId).orElse(null);
        if (agent == null) return;

        Notification agentNotif = new Notification();
        agentNotif.setAgent(agent);
        agentNotif.setAgentName(agent.getName());
        agentNotif.setMessage(title + ": " + body);
        agentNotif.setType("PUSH");
        agentNotif.setDepartment("AGENT");
        saveNotification(agentNotif);

        logger.info("[PUSH NOTIFICATION TO AGENT id={}] Title: {} | Body: {}", agentId, title, body);
    }

    public List<Notification> getSalesNotifications() {
        return notificationRepository.findByDepartmentOrderByCreatedAtDesc("SALES");
    }

    public List<Notification> getHRNotifications() {
        return notificationRepository.findByDepartmentOrderByCreatedAtDesc("HR");
    }

    public List<Notification> getAgentNotifications(Long agentId) {
        return notificationRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
    }

    public void markAsRead(Long id) {
        notificationRepository.findById(id).ifPresent(notif -> {
            notif.setIsRead(true);
            notificationRepository.save(notif);
        });
    }

    public void deleteNotification(Long id) {
        notificationRepository.deleteById(id);
    }
}
