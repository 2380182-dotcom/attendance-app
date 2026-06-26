package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;

public class AgentDTO {
    private Long id;
    private String agentId;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String department;
    private LocalDateTime createdAt;
    
    // Constructors
    public AgentDTO() {}
    
    public AgentDTO(Long id, String agentId, String name, String email, String phone, String role, String department, LocalDateTime createdAt) {
        this.id = id;
        this.agentId = agentId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.department = department;
        this.createdAt = createdAt;
    }
    
    // Getters
    public Long getId() { return id; }
    public String getAgentId() { return agentId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public String getDepartment() { return department; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    
    // Setters
    public void setId(Long id) { this.id = id; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setRole(String role) { this.role = role; }
    public void setDepartment(String department) { this.department = department; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
