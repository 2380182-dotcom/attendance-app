package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * "Salesman updates" feature: a salesman reached a shop but couldn't make
 * a sale, so he records why instead. Deliberately a separate table from
 * ShopVisitScan — that entity's scannedCode/visitStatus are QR-scan-specific
 * (NOT NULL scannedCode, SUCCESS/INVALID_CODE/... statuses) and feedback
 * explicitly skips the QR step entirely. GPS/geofence are still checked
 * (same buffered-gate rule as a real visit — see ShopFeedbackService),
 * recorded here for audit the same way a visit's distance is. Creates no
 * SalesRecord — structurally impossible to conflate with a voucher since
 * there is no relation between this table and sales_records.
 */
@Entity
@Table(name = "shop_feedback")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
public class ShopFeedback implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_shop_id", nullable = false)
    private CustomerShop customerShop;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private ShopFeedbackReason reason;

    /** Required when reason = OTHER; optional (the salesman's own words) for every other reason — see ShopFeedbackRequest. */
    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "gps_latitude", nullable = false)
    private Double gpsLatitude;

    @Column(name = "gps_longitude", nullable = false)
    private Double gpsLongitude;

    /** Null when the shop has no lat/lon/radius configured (distance could not be computed) — same convention as ShopVisitScan. */
    @Column(name = "distance_from_shop_meters")
    private Double distanceFromShopMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "geofence_status", nullable = false, length = 20)
    private GeofenceStatus geofenceStatus;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "visit_time", nullable = false)
    private LocalTime visitTime;

    // Idempotency key, same pattern as SalesRecord.requestId — optional so
    // an old app build that never sends one keeps working; a unique index
    // (V32) is the real race-proof guard.
    @Column(name = "request_id", length = 64, unique = true)
    private String requestId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public ShopFeedback() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public CustomerShop getCustomerShop() { return customerShop; }
    public void setCustomerShop(CustomerShop customerShop) { this.customerShop = customerShop; }

    public ShopFeedbackReason getReason() { return reason; }
    public void setReason(ShopFeedbackReason reason) { this.reason = reason; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Double getGpsLatitude() { return gpsLatitude; }
    public void setGpsLatitude(Double gpsLatitude) { this.gpsLatitude = gpsLatitude; }

    public Double getGpsLongitude() { return gpsLongitude; }
    public void setGpsLongitude(Double gpsLongitude) { this.gpsLongitude = gpsLongitude; }

    public Double getDistanceFromShopMeters() { return distanceFromShopMeters; }
    public void setDistanceFromShopMeters(Double distanceFromShopMeters) { this.distanceFromShopMeters = distanceFromShopMeters; }

    public GeofenceStatus getGeofenceStatus() { return geofenceStatus; }
    public void setGeofenceStatus(GeofenceStatus geofenceStatus) { this.geofenceStatus = geofenceStatus; }

    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }

    public LocalTime getVisitTime() { return visitTime; }
    public void setVisitTime(LocalTime visitTime) { this.visitTime = visitTime; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
