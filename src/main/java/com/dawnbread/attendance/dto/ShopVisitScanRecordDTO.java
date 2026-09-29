package com.dawnbread.attendance.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One row of the admin's "QR / Shop Visits" report (Q2) — every scan
 * attempt, pass or fail. agentId is deliberately not joined to a name here
 * (ShopVisitScan denormalizes it, no relation) — the dashboard already has
 * an established pattern for joining agent names from the roster
 * client-side (see LmtReconciliationPage), reused here for consistency.
 */
public class ShopVisitScanRecordDTO {
    private Long visitId;
    private Long agentId;
    private String scannedCode;
    private Long shopId;
    private String shopCode;
    private String shopName;
    private String areaName;
    /** Parsed from the shop code's CITY-SECTOR-NUMBER convention (e.g. "ISB" from "ISB-I14-001") — null if the code doesn't resolve to a shop, or has no "-". */
    private String city;
    private LocalDate scanDate;
    private LocalTime scanTime;
    private Double distanceMeters;
    /** INSIDE | OUTSIDE | NOT_EVALUATED */
    private String geofenceStatus;
    /** SUCCESS | INVALID_CODE | SHOP_INACTIVE | OUTSIDE_GEOFENCE */
    private String visitStatus;

    public ShopVisitScanRecordDTO() {}

    public Long getVisitId() { return visitId; }
    public void setVisitId(Long visitId) { this.visitId = visitId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getScannedCode() { return scannedCode; }
    public void setScannedCode(String scannedCode) { this.scannedCode = scannedCode; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public LocalDate getScanDate() { return scanDate; }
    public void setScanDate(LocalDate scanDate) { this.scanDate = scanDate; }

    public LocalTime getScanTime() { return scanTime; }
    public void setScanTime(LocalTime scanTime) { this.scanTime = scanTime; }

    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }

    public String getGeofenceStatus() { return geofenceStatus; }
    public void setGeofenceStatus(String geofenceStatus) { this.geofenceStatus = geofenceStatus; }

    public String getVisitStatus() { return visitStatus; }
    public void setVisitStatus(String visitStatus) { this.visitStatus = visitStatus; }
}
