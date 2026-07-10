package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;

/**
 * Flat projection of Notification for the API boundary — the entity's
 * `agent` field is a full JPA Agent reference and must never be serialized
 * directly (same entity-at-boundary issue as Findings 01/02).
 */
public class NotificationDTO {
    private Long id;
    private Long agentId;
    private String agentName;
    private String message;
    private String type;
    private String department;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public NotificationDTO() {}

    public NotificationDTO(Long id, Long agentId, String agentName, String message, String type,
                            String department, Boolean isRead, LocalDateTime createdAt) {
        this.id = id;
        this.agentId = agentId;
        this.agentName = agentName;
        this.message = message;
        this.type = type;
        this.department = department;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
