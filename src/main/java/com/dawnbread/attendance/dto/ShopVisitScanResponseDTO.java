package com.dawnbread.attendance.dto;

/** Response of POST /api/lmt/shop-visits/scan — success or one of the rejection reasons, always recorded either way. */
public class ShopVisitScanResponseDTO {
    private Long visitId;
    /** SUCCESS | INVALID_CODE | SHOP_INACTIVE | OUTSIDE_GEOFENCE */
    private String visitStatus;
    /** INSIDE | OUTSIDE | NOT_EVALUATED */
    private String geofenceStatus;
    private Double distanceMeters;
    private String message;
    /** Null when the scanned code didn't resolve to any shop. Same shape RecordVisitScreen/LocalEntryScreen already receive from the nearby-shops list. */
    private CustomerShopDTO shop;

    public ShopVisitScanResponseDTO() {}

    public Long getVisitId() { return visitId; }
    public void setVisitId(Long visitId) { this.visitId = visitId; }

    public String getVisitStatus() { return visitStatus; }
    public void setVisitStatus(String visitStatus) { this.visitStatus = visitStatus; }

    public String getGeofenceStatus() { return geofenceStatus; }
    public void setGeofenceStatus(String geofenceStatus) { this.geofenceStatus = geofenceStatus; }

    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public CustomerShopDTO getShop() { return shop; }
    public void setShop(CustomerShopDTO shop) { this.shop = shop; }
}
