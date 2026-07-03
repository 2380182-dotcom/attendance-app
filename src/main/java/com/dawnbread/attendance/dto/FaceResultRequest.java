package com.dawnbread.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class FaceResultRequest {

    @NotNull
    private Long agentId;

    @NotBlank
    private String verificationResult; // PASS or FAIL

    @NotNull
    private Float confidenceScore;

    @NotBlank
    private String checkpointType; // CHECKIN, MIDSHIFT, CHECKOUT

    private LocalDateTime timestamp;

    public FaceResultRequest() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getVerificationResult() { return verificationResult; }
    public void setVerificationResult(String verificationResult) { this.verificationResult = verificationResult; }

    public Float getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Float confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getCheckpointType() { return checkpointType; }
    public void setCheckpointType(String checkpointType) { this.checkpointType = checkpointType; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
