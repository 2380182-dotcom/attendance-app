package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;

public class HierarchyPersonDTO {
    private Long id;
    private String name;
    private String roleLabel;
    private String contact;
    private Boolean isActive;
    private LocalDateTime createdAt;

    public HierarchyPersonDTO() {}

    public HierarchyPersonDTO(Long id, String name, String roleLabel, String contact, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.roleLabel = roleLabel;
        this.contact = contact;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRoleLabel() { return roleLabel; }
    public void setRoleLabel(String roleLabel) { this.roleLabel = roleLabel; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
