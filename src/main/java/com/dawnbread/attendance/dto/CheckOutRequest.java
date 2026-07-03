package com.dawnbread.attendance.dto;

public class CheckOutRequest {
    private Long agentId;
    private Double latitude;
    private Double longitude;
    private Boolean faceVerified;

    public CheckOutRequest() {}

    public CheckOutRequest(Long agentId, Double latitude, Double longitude) {
        this.agentId = agentId;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Boolean getFaceVerified() { return faceVerified; }
    public void setFaceVerified(Boolean faceVerified) { this.faceVerified = faceVerified; }
}
