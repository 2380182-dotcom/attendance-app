package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class AgentDTO {
    private Long id;
    private String agentId;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String department;
    private LocalDateTime createdAt;
    private Boolean faceVerifyOnCheckIn;
    private Boolean faceVerifyOnCheckOut;
    private Boolean faceVerifyAnytime;
    private Boolean faceRegistered;
    private String faceTemplate;
    private LocalTime shiftStartTime;
    private LocalTime shiftEndTime;
    private Integer gracePeriodMinutes;
    private List<String> workingDays;
    
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

    public AgentDTO(Long id, String agentId, String name, String email, String phone, String role, String department, LocalDateTime createdAt,
                    Boolean faceVerifyOnCheckIn, Boolean faceVerifyOnCheckOut, Boolean faceVerifyAnytime, Boolean faceRegistered, String faceTemplate) {
        this.id = id;
        this.agentId = agentId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.department = department;
        this.createdAt = createdAt;
        this.faceVerifyOnCheckIn = faceVerifyOnCheckIn;
        this.faceVerifyOnCheckOut = faceVerifyOnCheckOut;
        this.faceVerifyAnytime = faceVerifyAnytime;
        this.faceRegistered = faceRegistered;
        this.faceTemplate = faceTemplate;
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
    public Boolean getFaceVerifyOnCheckIn() { return faceVerifyOnCheckIn; }
    public Boolean getFaceVerifyOnCheckOut() { return faceVerifyOnCheckOut; }
    public Boolean getFaceVerifyAnytime() { return faceVerifyAnytime; }
    public Boolean getFaceRegistered() { return faceRegistered; }
    public String getFaceTemplate() { return faceTemplate; }
    public LocalTime getShiftStartTime() { return shiftStartTime; }
    public LocalTime getShiftEndTime() { return shiftEndTime; }
    public Integer getGracePeriodMinutes() { return gracePeriodMinutes; }
    public List<String> getWorkingDays() { return workingDays; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setRole(String role) { this.role = role; }
    public void setDepartment(String department) { this.department = department; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setFaceVerifyOnCheckIn(Boolean faceVerifyOnCheckIn) { this.faceVerifyOnCheckIn = faceVerifyOnCheckIn; }
    public void setFaceVerifyOnCheckOut(Boolean faceVerifyOnCheckOut) { this.faceVerifyOnCheckOut = faceVerifyOnCheckOut; }
    public void setFaceVerifyAnytime(Boolean faceVerifyAnytime) { this.faceVerifyAnytime = faceVerifyAnytime; }
    public void setFaceRegistered(Boolean faceRegistered) { this.faceRegistered = faceRegistered; }
    public void setFaceTemplate(String faceTemplate) { this.faceTemplate = faceTemplate; }
    public void setShiftStartTime(LocalTime shiftStartTime) { this.shiftStartTime = shiftStartTime; }
    public void setShiftEndTime(LocalTime shiftEndTime) { this.shiftEndTime = shiftEndTime; }
    public void setGracePeriodMinutes(Integer gracePeriodMinutes) { this.gracePeriodMinutes = gracePeriodMinutes; }
    public void setWorkingDays(List<String> workingDays) { this.workingDays = workingDays; }
}
