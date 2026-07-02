package com.dawnbread.attendance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Attendance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;
    
    @ManyToOne
    @JoinColumn(name = "mart_id", nullable = false)
    private Mart mart;
    
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
    public Attendance() {}
    
    // Getters
    public Long getId() { return id; }
    public Agent getAgent() { return agent; }
    public Mart getMart() { return mart; }
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
    public void setAgent(Agent agent) { this.agent = agent; }
    public void setMart(Mart mart) { this.mart = mart; }
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
