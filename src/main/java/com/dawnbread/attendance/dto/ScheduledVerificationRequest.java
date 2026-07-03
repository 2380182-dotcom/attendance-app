package com.dawnbread.attendance.dto;

public class ScheduledVerificationRequest {
    private Long agentId;
    private String imageBase64;

    public ScheduledVerificationRequest() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
}
