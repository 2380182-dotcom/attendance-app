package com.dawnbread.attendance.dto;

public class CheckInRequest {
    private Long agentId;
    private Long martId;
    private Double latitude;
    private Double longitude;
    private Boolean faceVerified;

    public CheckInRequest() {}

    public CheckInRequest(Long agentId, Long martId, Double latitude, Double longitude) {
        this.agentId = agentId;
        this.martId = martId;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public Long getMartId() { return martId; }
    public void setMartId(Long martId) { this.martId = martId; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Boolean getFaceVerified() { return faceVerified; }
    public void setFaceVerified(Boolean faceVerified) { this.faceVerified = faceVerified; }
}
