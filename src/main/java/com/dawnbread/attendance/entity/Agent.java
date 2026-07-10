package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.AuditEntityListener;
import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent", uniqueConstraints = {
        @UniqueConstraint(name = "ux_agent_tenant_agent_id", columnNames = {"tenant_id", "agent_id"}),
        @UniqueConstraint(name = "ux_agent_tenant_email", columnNames = {"tenant_id", "email"})
})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({TenantEntityListener.class, AuditEntityListener.class})
public class Agent implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String agentId;

    private String name;

    private String email;

    private String phone;

    // Never serialize the password hash — see the audit note on
    // faceTemplate/faceEmbedding below for why field-level @JsonIgnore is the
    // floor, not the fix; controllers must also return AgentDTO, not Agent.
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    private String role;

    private String department;

    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    @Column(nullable = false)
    private Boolean isActive = true;

    // Legacy face flags (backward compatible with mobile app)
    private Boolean faceVerifyOnCheckIn = true;
    private Boolean faceVerifyOnCheckOut = true;
    private Boolean faceVerifyAnytime = true;

    // Configurable face verification
    private Boolean faceVerificationEnabled = true;
    private Integer faceVerificationFrequency = 2;
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> faceVerificationTimes = List.of("09:00", "17:00");
    private LocalDateTime faceVerifiedAt;
    private Integer faceVerificationCountToday = 0;
    private LocalDate faceLastVerificationDate;

    private Boolean faceRegistered = false;

    // Biometric data — never serialize this, in any response, to any role.
    // Field-level @JsonIgnore is a floor against an entity leaking by accident
    // (e.g. through a nested @ManyToOne on another entity, as GeoFenceLog.agent
    // did); it is not a substitute for controllers returning AgentDTO instead
    // of the raw entity, which is the actual fix applied at every call site.
    //
    // @Convert(FaceDataEncryptionConverter): AES-256-GCM at rest, transparent
    // to every other line of code that reads/writes this field — see the
    // approved design doc. Dual-read during migration: a legacy plaintext row
    // (no "ENCv" prefix) still reads correctly; every write always encrypts.
    @Column(columnDefinition = "TEXT")
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.persistence.Convert(converter = com.dawnbread.attendance.security.FaceDataEncryptionConverter.class)
    private String faceTemplate;

    /** Base64-encoded float32 array — enrolled on-device via ML Kit */
    @Column(columnDefinition = "TEXT")
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.persistence.Convert(converter = com.dawnbread.attendance.security.FaceDataEncryptionConverter.class)
    private String faceEmbedding;

    private LocalDateTime faceTemplateUpdatedAt;

    // Shift timing
    private LocalTime shiftStartTime = LocalTime.of(9, 0);
    private LocalTime shiftEndTime = LocalTime.of(17, 0);
    private Integer gracePeriodMinutes = 15;
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> workingDays = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT");

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

    public Agent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getFaceVerifyOnCheckIn() { return faceVerifyOnCheckIn; }
    public void setFaceVerifyOnCheckIn(Boolean faceVerifyOnCheckIn) { this.faceVerifyOnCheckIn = faceVerifyOnCheckIn; }

    public Boolean getFaceVerifyOnCheckOut() { return faceVerifyOnCheckOut; }
    public void setFaceVerifyOnCheckOut(Boolean faceVerifyOnCheckOut) { this.faceVerifyOnCheckOut = faceVerifyOnCheckOut; }

    public Boolean getFaceVerifyAnytime() { return faceVerifyAnytime; }
    public void setFaceVerifyAnytime(Boolean faceVerifyAnytime) { this.faceVerifyAnytime = faceVerifyAnytime; }

    public Boolean getFaceVerificationEnabled() { return faceVerificationEnabled; }
    public void setFaceVerificationEnabled(Boolean faceVerificationEnabled) { this.faceVerificationEnabled = faceVerificationEnabled; }

    public Integer getFaceVerificationFrequency() { return faceVerificationFrequency; }
    public void setFaceVerificationFrequency(Integer faceVerificationFrequency) { this.faceVerificationFrequency = faceVerificationFrequency; }

    public List<String> getFaceVerificationTimes() { return faceVerificationTimes; }
    public void setFaceVerificationTimes(List<String> faceVerificationTimes) { this.faceVerificationTimes = faceVerificationTimes; }

    public LocalDateTime getFaceVerifiedAt() { return faceVerifiedAt; }
    public void setFaceVerifiedAt(LocalDateTime faceVerifiedAt) { this.faceVerifiedAt = faceVerifiedAt; }

    public Integer getFaceVerificationCountToday() { return faceVerificationCountToday; }
    public void setFaceVerificationCountToday(Integer faceVerificationCountToday) { this.faceVerificationCountToday = faceVerificationCountToday; }

    public LocalDate getFaceLastVerificationDate() { return faceLastVerificationDate; }
    public void setFaceLastVerificationDate(LocalDate faceLastVerificationDate) { this.faceLastVerificationDate = faceLastVerificationDate; }

    public Boolean getFaceRegistered() { return faceRegistered; }
    public void setFaceRegistered(Boolean faceRegistered) { this.faceRegistered = faceRegistered; }

    public String getFaceTemplate() { return faceTemplate; }
    public void setFaceTemplate(String faceTemplate) { this.faceTemplate = faceTemplate; }

    public String getFaceEmbedding() { return faceEmbedding; }
    public void setFaceEmbedding(String faceEmbedding) { this.faceEmbedding = faceEmbedding; }

    public LocalDateTime getFaceTemplateUpdatedAt() { return faceTemplateUpdatedAt; }
    public void setFaceTemplateUpdatedAt(LocalDateTime faceTemplateUpdatedAt) { this.faceTemplateUpdatedAt = faceTemplateUpdatedAt; }

    public LocalTime getShiftStartTime() { return shiftStartTime; }
    public void setShiftStartTime(LocalTime shiftStartTime) { this.shiftStartTime = shiftStartTime; }

    public LocalTime getShiftEndTime() { return shiftEndTime; }
    public void setShiftEndTime(LocalTime shiftEndTime) { this.shiftEndTime = shiftEndTime; }

    public Integer getGracePeriodMinutes() { return gracePeriodMinutes; }
    public void setGracePeriodMinutes(Integer gracePeriodMinutes) { this.gracePeriodMinutes = gracePeriodMinutes; }

    public List<String> getWorkingDays() { return workingDays; }
    public void setWorkingDays(List<String> workingDays) { this.workingDays = workingDays; }

    public List<Attendance> getAttendances() { return attendances; }
    public void setAttendances(List<Attendance> attendances) { this.attendances = attendances; }

    public List<SalesRecord> getSalesRecords() { return salesRecords; }
    public void setSalesRecords(List<SalesRecord> salesRecords) { this.salesRecords = salesRecords; }

    public List<Notification> getNotifications() { return notifications; }
    public void setNotifications(List<Notification> notifications) { this.notifications = notifications; }

    public List<GeoFenceLog> getGeoFenceLogs() { return geoFenceLogs; }
    public void setGeoFenceLogs(List<GeoFenceLog> geoFenceLogs) { this.geoFenceLogs = geoFenceLogs; }
}
