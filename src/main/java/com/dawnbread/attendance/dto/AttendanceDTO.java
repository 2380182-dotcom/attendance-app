package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;

public class AttendanceDTO {
    private Long id;
    private Long agentId;
    private String agentName;
    private Long martId;
    private String martName;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private String status;
    private Double checkInLatitude;
    private Double checkInLongitude;
    private Double checkOutLatitude;
    private Double checkOutLongitude;
    private Double distanceFromMart;
    private LocalDateTime midDayVerificationTime;
    
    // Constructors
    public AttendanceDTO() {}
    
    public AttendanceDTO(Long id, Long agentId, String agentName, Long martId, String martName,
                         LocalDateTime checkInTime, LocalDateTime checkOutTime, String status,
                         Double checkInLatitude, Double checkInLongitude, Double checkOutLatitude,
                         Double checkOutLongitude, Double distanceFromMart) {
        this.id = id;
        this.agentId = agentId;
        this.agentName = agentName;
        this.martId = martId;
        this.martName = martName;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.status = status;
        this.checkInLatitude = checkInLatitude;
        this.checkInLongitude = checkInLongitude;
        this.checkOutLatitude = checkOutLatitude;
        this.checkOutLongitude = checkOutLongitude;
        this.distanceFromMart = distanceFromMart;
    }

    public AttendanceDTO(Long id, Long agentId, String agentName, Long martId, String martName,
                         LocalDateTime checkInTime, LocalDateTime checkOutTime, String status,
                         Double checkInLatitude, Double checkInLongitude, Double checkOutLatitude,
                         Double checkOutLongitude, Double distanceFromMart, LocalDateTime midDayVerificationTime) {
        this.id = id;
        this.agentId = agentId;
        this.agentName = agentName;
        this.martId = martId;
        this.martName = martName;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.status = status;
        this.checkInLatitude = checkInLatitude;
        this.checkInLongitude = checkInLongitude;
        this.checkOutLatitude = checkOutLatitude;
        this.checkOutLongitude = checkOutLongitude;
        this.distanceFromMart = distanceFromMart;
        this.midDayVerificationTime = midDayVerificationTime;
    }
    
    // Getters
    public Long getId() { return id; }
    public Long getAgentId() { return agentId; }
    public String getAgentName() { return agentName; }
    public Long getMartId() { return martId; }
    public String getMartName() { return martName; }
    public LocalDateTime getCheckInTime() { return checkInTime; }
    public LocalDateTime getCheckOutTime() { return checkOutTime; }
    public String getStatus() { return status; }
    public Double getCheckInLatitude() { return checkInLatitude; }
    public Double getCheckInLongitude() { return checkInLongitude; }
    public Double getCheckOutLatitude() { return checkOutLatitude; }
    public Double getCheckOutLongitude() { return checkOutLongitude; }
    public Double getDistanceFromMart() { return distanceFromMart; }
    public LocalDateTime getMidDayVerificationTime() { return midDayVerificationTime; }
    
    // Setters
    public void setId(Long id) { this.id = id; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public void setMartId(Long martId) { this.martId = martId; }
    public void setMartName(String martName) { this.martName = martName; }
    public void setCheckInTime(LocalDateTime checkInTime) { this.checkInTime = checkInTime; }
    public void setCheckOutTime(LocalDateTime checkOutTime) { this.checkOutTime = checkOutTime; }
    public void setStatus(String status) { this.status = status; }
    public void setCheckInLatitude(Double checkInLatitude) { this.checkInLatitude = checkInLatitude; }
    public void setCheckInLongitude(Double checkInLongitude) { this.checkInLongitude = checkInLongitude; }
    public void setCheckOutLatitude(Double checkOutLatitude) { this.checkOutLatitude = checkOutLatitude; }
    public void setCheckOutLongitude(Double checkOutLongitude) { this.checkOutLongitude = checkOutLongitude; }
    public void setDistanceFromMart(Double distanceFromMart) { this.distanceFromMart = distanceFromMart; }
    public void setMidDayVerificationTime(LocalDateTime midDayVerificationTime) { this.midDayVerificationTime = midDayVerificationTime; }
}
