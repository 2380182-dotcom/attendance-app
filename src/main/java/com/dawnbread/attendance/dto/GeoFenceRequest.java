package com.dawnbread.attendance.dto;

public class GeoFenceRequest {
    private Long agentId;
    private Double latitude;
    private Double longitude;

    public GeoFenceRequest() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
