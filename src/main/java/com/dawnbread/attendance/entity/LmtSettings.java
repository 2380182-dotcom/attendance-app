package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;

/**
 * One row per tenant. geofenceBufferMeters is added to a shop's own radius
 * when hard-gating a shop-visit geofence check (a later stage) — attendance
 * check-in's existing hard block against Mart.radius is untouched and never
 * reads this table.
 */
@Entity
@Table(name = "lmt_settings")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({TenantEntityListener.class})
public class LmtSettings implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "geofence_buffer_meters", nullable = false)
    private Double geofenceBufferMeters = 50.0;

    // QR shop-visit flow: tenant-wide master overrides. PER_SHOP (the
    // default) changes nothing — each shop's own geoFencingEnabled/
    // qrRequired toggle applies exactly as before. See
    // ShopRequirementService, the single place these are resolved.
    @Enumerated(EnumType.STRING)
    @Column(name = "geofence_mode", nullable = false)
    private GlobalToggleMode geofenceMode = GlobalToggleMode.PER_SHOP;

    @Enumerated(EnumType.STRING)
    @Column(name = "qr_mode", nullable = false)
    private GlobalToggleMode qrMode = GlobalToggleMode.PER_SHOP;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public LmtSettings() {}

    // Getters
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Double getGeofenceBufferMeters() { return geofenceBufferMeters; }
    public GlobalToggleMode getGeofenceMode() { return geofenceMode; }
    public GlobalToggleMode getQrMode() { return qrMode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public void setGeofenceBufferMeters(Double geofenceBufferMeters) { this.geofenceBufferMeters = geofenceBufferMeters; }
    public void setGeofenceMode(GlobalToggleMode geofenceMode) { this.geofenceMode = geofenceMode; }
    public void setQrMode(GlobalToggleMode qrMode) { this.qrMode = qrMode; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
