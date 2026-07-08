package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.entity.Notification;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private HttpServletRequest request;

    @GetMapping("/sales")
    public ResponseEntity<ApiResponse<List<Notification>>> getSalesNotifications() {
        if (!AccessControl.hasRole(request, "ADMIN", "SALES")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only Sales or an administrator can view Sales notifications."));
        }
        try {
            List<Notification> notifications = notificationService.getSalesNotifications();
            return ResponseEntity.ok(ApiResponse.success("Sales notifications retrieved", notifications));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/hr")
    public ResponseEntity<ApiResponse<List<Notification>>> getHRNotifications() {
        if (!AccessControl.hasRole(request, "ADMIN", "HR")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only HR or an administrator can view HR notifications."));
        }
        try {
            List<Notification> notifications = notificationService.getHRNotifications();
            return ResponseEntity.ok(ApiResponse.success("HR notifications retrieved", notifications));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * An agent may only read their own notifications — not any other agent's.
     * Management roles (Admin/HR/Sales) can read any agent's, for oversight.
     */
    @GetMapping("/agent/{agentId}")
    public ResponseEntity<ApiResponse<List<Notification>>> getAgentNotifications(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId, "ADMIN", "HR", "SALES")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You can only view your own notifications."));
        }
        try {
            List<Notification> notifications = notificationService.getAgentNotifications(agentId);
            return ResponseEntity.ok(ApiResponse.success("Agent notifications retrieved", notifications));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Self-or-management, not strictly self-only: confirmed HRReportScreen is
     * a real, live use case where HR marks-as-read/deletes notifications
     * about OTHER agents from the shared HR queue (the notification's
     * "owner" for this purpose is who it's ABOUT — Notification.agent — not
     * who's viewing it). Kept ADMIN/HR/SALES symmetric with every other
     * elevated-role check in this controller (GET /agent/{agentId} already
     * uses the same three), even though only the HR screen currently
     * exercises it — /notifications/sales is an equally real department
     * queue with no reason to be less capable than HR's.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long id) {
        var notification = notificationService.getNotificationById(id);
        if (notification.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Notification not found with id: " + id));
        }
        Long ownerAgentId = notification.get().getAgent() != null ? notification.get().getAgent().getId() : null;
        if (!AccessControl.isSelfOrRole(request, ownerAgentId, "ADMIN", "HR", "SALES")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You can only manage your own notifications."));
        }
        try {
            notificationService.markAsRead(id);
            return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable Long id) {
        var notification = notificationService.getNotificationById(id);
        if (notification.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Notification not found with id: " + id));
        }
        Long ownerAgentId = notification.get().getAgent() != null ? notification.get().getAgent().getId() : null;
        if (!AccessControl.isSelfOrRole(request, ownerAgentId, "ADMIN", "HR", "SALES")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You can only manage your own notifications."));
        }
        try {
            notificationService.deleteNotification(id);
            return ResponseEntity.ok(ApiResponse.success("Notification deleted", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
