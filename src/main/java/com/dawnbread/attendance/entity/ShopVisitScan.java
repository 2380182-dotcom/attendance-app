package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * One row per QR-scan attempt (pass or fail) — recorded before, and
 * independently of, whether a sale/return is ever submitted for that shop.
 * agentId is denormalized (no FK), matching LmtDailyStock's existing
 * pattern for "one row per LMT per day"-shaped tables in this codebase.
 * customerShopId IS a real relation (nullable — an invalid code resolves
 * to no shop) since the admin dashboard needs the shop's name/code/area.
 */
@Entity
@Table(name = "shop_visit_scan")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
public class ShopVisitScan implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_shop_id")
    private CustomerShop customerShop;

    /** The raw code as scanned — kept even when it doesn't resolve to any shop, for the admin's failed-scan audit trail. */
    @Column(name = "scanned_code", nullable = false, length = 100)
    private String scannedCode;

    @Column(name = "scan_date", nullable = false)
    private LocalDate scanDate;

    @Column(name = "scan_time", nullable = false)
    private LocalTime scanTime;

    @Column(name = "gps_latitude", nullable = false)
    private Double gpsLatitude;

    @Column(name = "gps_longitude", nullable = false)
    private Double gpsLongitude;

    /** Null when the shop has no lat/lon/radius configured (distance could not be computed). */
    @Column(name = "distance_from_shop_meters")
    private Double distanceFromShopMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "geofence_status", nullable = false, length = 20)
    private GeofenceStatus geofenceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_status", nullable = false, length = 20)
    private ShopVisitStatus visitStatus;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public ShopVisitScan() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public CustomerShop getCustomerShop() { return customerShop; }
    public void setCustomerShop(CustomerShop customerShop) { this.customerShop = customerShop; }

    public String getScannedCode() { return scannedCode; }
    public void setScannedCode(String scannedCode) { this.scannedCode = scannedCode; }

    public LocalDate getScanDate() { return scanDate; }
    public void setScanDate(LocalDate scanDate) { this.scanDate = scanDate; }

    public LocalTime getScanTime() { return scanTime; }
    public void setScanTime(LocalTime scanTime) { this.scanTime = scanTime; }

    public Double getGpsLatitude() { return gpsLatitude; }
    public void setGpsLatitude(Double gpsLatitude) { this.gpsLatitude = gpsLatitude; }

    public Double getGpsLongitude() { return gpsLongitude; }
    public void setGpsLongitude(Double gpsLongitude) { this.gpsLongitude = gpsLongitude; }

    public Double getDistanceFromShopMeters() { return distanceFromShopMeters; }
    public void setDistanceFromShopMeters(Double distanceFromShopMeters) { this.distanceFromShopMeters = distanceFromShopMeters; }

    public GeofenceStatus getGeofenceStatus() { return geofenceStatus; }
    public void setGeofenceStatus(GeofenceStatus geofenceStatus) { this.geofenceStatus = geofenceStatus; }

    public ShopVisitStatus getVisitStatus() { return visitStatus; }
    public void setVisitStatus(ShopVisitStatus visitStatus) { this.visitStatus = visitStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
