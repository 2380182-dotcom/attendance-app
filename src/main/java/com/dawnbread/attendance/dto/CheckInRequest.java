package com.dawnbread.attendance.dto;

public class CheckInRequest {
    private Long agentId;
    private Long martId;
    private Double latitude;
    private Double longitude;
    
    public CheckInRequest() {}
    
    public CheckInRequest(Long agentId, Long martId, Double latitude, Double longitude) {
        this.agentId = agentId;
        this.martId = martId;
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    // Getters
    public Long getAgentId() { return agentId; }
    public Long getMartId() { return martId; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    
    // Setters
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public void setMartId(Long martId) { this.martId = martId; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
