package com.dawnbread.attendance.dto;

public class CheckOutRequest {
    private Long agentId;
    private Double latitude;
    private Double longitude;
    
    public CheckOutRequest() {}
    
    public CheckOutRequest(Long agentId, Double latitude, Double longitude) {
        this.agentId = agentId;
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    // Getters
    public Long getAgentId() { return agentId; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    
    // Setters
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
