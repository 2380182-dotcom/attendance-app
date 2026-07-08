package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "attendance")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
public class Attendance implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

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

    private LocalTime shiftStartTime;
    private LocalTime shiftEndTime;
    private Integer lateMinutes = 0;
    private Boolean faceVerifiedCheckin = false;
    private Boolean faceVerifiedCheckout = false;

    public Attendance() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }

    public Mart getMart() { return mart; }
    public void setMart(Mart mart) { this.mart = mart; }

    public LocalDateTime getCheckInTime() { return checkInTime; }
    public void setCheckInTime(LocalDateTime checkInTime) { this.checkInTime = checkInTime; }

    public LocalDateTime getCheckOutTime() { return checkOutTime; }
    public void setCheckOutTime(LocalDateTime checkOutTime) { this.checkOutTime = checkOutTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getCheckInLatitude() { return checkInLatitude; }
    public void setCheckInLatitude(Double checkInLatitude) { this.checkInLatitude = checkInLatitude; }

    public Double getCheckInLongitude() { return checkInLongitude; }
    public void setCheckInLongitude(Double checkInLongitude) { this.checkInLongitude = checkInLongitude; }

    public Double getCheckOutLatitude() { return checkOutLatitude; }
    public void setCheckOutLatitude(Double checkOutLatitude) { this.checkOutLatitude = checkOutLatitude; }

    public Double getCheckOutLongitude() { return checkOutLongitude; }
    public void setCheckOutLongitude(Double checkOutLongitude) { this.checkOutLongitude = checkOutLongitude; }

    public Double getDistanceFromMart() { return distanceFromMart; }
    public void setDistanceFromMart(Double distanceFromMart) { this.distanceFromMart = distanceFromMart; }

    public LocalDateTime getMidDayVerificationTime() { return midDayVerificationTime; }
    public void setMidDayVerificationTime(LocalDateTime midDayVerificationTime) { this.midDayVerificationTime = midDayVerificationTime; }

    public LocalTime getShiftStartTime() { return shiftStartTime; }
    public void setShiftStartTime(LocalTime shiftStartTime) { this.shiftStartTime = shiftStartTime; }

    public LocalTime getShiftEndTime() { return shiftEndTime; }
    public void setShiftEndTime(LocalTime shiftEndTime) { this.shiftEndTime = shiftEndTime; }

    public Integer getLateMinutes() { return lateMinutes; }
    public void setLateMinutes(Integer lateMinutes) { this.lateMinutes = lateMinutes; }

    public Boolean getFaceVerifiedCheckin() { return faceVerifiedCheckin; }
    public void setFaceVerifiedCheckin(Boolean faceVerifiedCheckin) { this.faceVerifiedCheckin = faceVerifiedCheckin; }

    public Boolean getFaceVerifiedCheckout() { return faceVerifiedCheckout; }
    public void setFaceVerifiedCheckout(Boolean faceVerifiedCheckout) { this.faceVerifiedCheckout = faceVerifiedCheckout; }
}
