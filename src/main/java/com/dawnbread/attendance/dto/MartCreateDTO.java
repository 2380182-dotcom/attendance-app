package com.dawnbread.attendance.dto;

/**
 * Input-only shape for POST /api/marts and /api/admin/marts. Deliberately
 * has no `id` and no `tenantId` — those must never come from the client.
 * See MartController.createMart / AdminController.createMart.
 */
public class MartCreateDTO {
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double radius;
    private Boolean geoFencingEnabled;

    public MartCreateDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getRadius() { return radius; }
    public void setRadius(Double radius) { this.radius = radius; }

    public Boolean getGeoFencingEnabled() { return geoFencingEnabled; }
    public void setGeoFencingEnabled(Boolean geoFencingEnabled) { this.geoFencingEnabled = geoFencingEnabled; }
}
