package com.dawnbread.attendance.dto;

import java.time.LocalTime;
import java.util.List;

public class AgentRegistrationDTO {
    private String agentId;
    private String name;
    private String email;
    private String phone;
    private String password;
    private String role;
    private String department;
    private Boolean isActive;
    private Boolean faceVerificationEnabled;
    private Integer faceVerificationFrequency;
    private List<String> faceVerificationTimes;
    private LocalTime shiftStartTime;
    private LocalTime shiftEndTime;
    private Integer gracePeriodMinutes;
    private List<String> workingDays;

    public AgentRegistrationDTO() {}

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getFaceVerificationEnabled() { return faceVerificationEnabled; }
    public void setFaceVerificationEnabled(Boolean faceVerificationEnabled) { this.faceVerificationEnabled = faceVerificationEnabled; }

    public Integer getFaceVerificationFrequency() { return faceVerificationFrequency; }
    public void setFaceVerificationFrequency(Integer faceVerificationFrequency) { this.faceVerificationFrequency = faceVerificationFrequency; }

    public List<String> getFaceVerificationTimes() { return faceVerificationTimes; }
    public void setFaceVerificationTimes(List<String> faceVerificationTimes) { this.faceVerificationTimes = faceVerificationTimes; }

    public LocalTime getShiftStartTime() { return shiftStartTime; }
    public void setShiftStartTime(LocalTime shiftStartTime) { this.shiftStartTime = shiftStartTime; }

    public LocalTime getShiftEndTime() { return shiftEndTime; }
    public void setShiftEndTime(LocalTime shiftEndTime) { this.shiftEndTime = shiftEndTime; }

    public Integer getGracePeriodMinutes() { return gracePeriodMinutes; }
    public void setGracePeriodMinutes(Integer gracePeriodMinutes) { this.gracePeriodMinutes = gracePeriodMinutes; }

    public List<String> getWorkingDays() { return workingDays; }
    public void setWorkingDays(List<String> workingDays) { this.workingDays = workingDays; }
}
