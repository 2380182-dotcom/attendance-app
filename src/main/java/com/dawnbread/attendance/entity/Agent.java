package com.dawnbread.attendance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Agent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String agentId;
    
    private String name;
    
    @Column(unique = true)
    private String email;
    
    private String phone;
    
    private String password;

    private String role;

    private String department;
    
    private LocalDateTime createdAt;
    
    private String createdBy;

    @Column(nullable = false)
    private Boolean isActive = true;

    private Boolean faceVerifyOnCheckIn = true;
    private Boolean faceVerifyOnCheckOut = true;
    private Boolean faceVerifyAnytime = true;
    private Boolean faceRegistered = false;

    @Column(columnDefinition = "TEXT")
    private String faceTemplate;
    
    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Attendance> attendances = new ArrayList<>();

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesRecord> salesRecords = new ArrayList<>();

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Notification> notifications = new ArrayList<>();

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GeoFenceLog> geoFenceLogs = new ArrayList<>();
    
    // Constructors
    public Agent() {}
    
    // Getters
    public Long getId() { return id; }
    public String getAgentId() { return agentId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPassword() { return password; }
    public String getRole() { return role; }
    public String getDepartment() { return department; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<Attendance> getAttendances() { return attendances; }
    public String getCreatedBy() { return createdBy; }
    public Boolean getIsActive() { return isActive; }
    public Boolean getFaceVerifyOnCheckIn() { return faceVerifyOnCheckIn; }
    public Boolean getFaceVerifyOnCheckOut() { return faceVerifyOnCheckOut; }
    public Boolean getFaceVerifyAnytime() { return faceVerifyAnytime; }
    public Boolean getFaceRegistered() { return faceRegistered; }
    public String getFaceTemplate() { return faceTemplate; }
    public List<SalesRecord> getSalesRecords() { return salesRecords; }
    public List<Notification> getNotifications() { return notifications; }
    public List<GeoFenceLog> getGeoFenceLogs() { return geoFenceLogs; }
    
    // Setters
    public void setId(Long id) { this.id = id; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setPassword(String password) { this.password = password; }
    public void setRole(String role) { this.role = role; }
    public void setDepartment(String department) { this.department = department; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setAttendances(List<Attendance> attendances) { this.attendances = attendances; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public void setFaceVerifyOnCheckIn(Boolean faceVerifyOnCheckIn) { this.faceVerifyOnCheckIn = faceVerifyOnCheckIn; }
    public void setFaceVerifyOnCheckOut(Boolean faceVerifyOnCheckOut) { this.faceVerifyOnCheckOut = faceVerifyOnCheckOut; }
    public void setFaceVerifyAnytime(Boolean faceVerifyAnytime) { this.faceVerifyAnytime = faceVerifyAnytime; }
    public void setFaceRegistered(Boolean faceRegistered) { this.faceRegistered = faceRegistered; }
    public void setFaceTemplate(String faceTemplate) { this.faceTemplate = faceTemplate; }
    public void setSalesRecords(List<SalesRecord> salesRecords) { this.salesRecords = salesRecords; }
    public void setNotifications(List<Notification> notifications) { this.notifications = notifications; }
    public void setGeoFenceLogs(List<GeoFenceLog> geoFenceLogs) { this.geoFenceLogs = geoFenceLogs; }
}
