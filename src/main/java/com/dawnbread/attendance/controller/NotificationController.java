package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.entity.Notification;
import com.dawnbread.attendance.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping("/sales")
    public ResponseEntity<ApiResponse<List<Notification>>> getSalesNotifications() {
        try {
            List<Notification> notifications = notificationService.getSalesNotifications();
            return ResponseEntity.ok(ApiResponse.success("Sales notifications retrieved", notifications));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/hr")
    public ResponseEntity<ApiResponse<List<Notification>>> getHRNotifications() {
        try {
            List<Notification> notifications = notificationService.getHRNotifications();
            return ResponseEntity.ok(ApiResponse.success("HR notifications retrieved", notifications));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/agent/{agentId}")
    public ResponseEntity<ApiResponse<List<Notification>>> getAgentNotifications(@PathVariable Long agentId) {
        try {
            List<Notification> notifications = notificationService.getAgentNotifications(agentId);
            return ResponseEntity.ok(ApiResponse.success("Agent notifications retrieved", notifications));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long id) {
        try {
            notificationService.markAsRead(id);
            return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable Long id) {
        try {
            notificationService.deleteNotification(id);
            return ResponseEntity.ok(ApiResponse.success("Notification deleted", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
