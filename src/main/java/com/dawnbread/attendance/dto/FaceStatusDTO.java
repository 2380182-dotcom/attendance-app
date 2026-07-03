package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;
import java.util.List;

public class FaceStatusDTO {

    private Long agentId;
    private boolean registered;
    private LocalDateTime templateUpdatedAt;
    private Boolean faceVerifyOnCheckIn;
    private Boolean faceVerifyOnCheckOut;
    private Boolean faceVerifyAnytime;
    private Boolean faceVerificationEnabled;
    private Integer faceVerificationFrequency;
    private List<String> faceVerificationTimes;
    private Integer faceVerificationCountToday;
    private Float confidenceThreshold;
    private FaceVerificationStatusDTO verificationStatus;

    public FaceStatusDTO() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public boolean isRegistered() { return registered; }
    public void setRegistered(boolean registered) { this.registered = registered; }

    public LocalDateTime getTemplateUpdatedAt() { return templateUpdatedAt; }
    public void setTemplateUpdatedAt(LocalDateTime templateUpdatedAt) { this.templateUpdatedAt = templateUpdatedAt; }

    public Boolean getFaceVerifyOnCheckIn() { return faceVerifyOnCheckIn; }
    public void setFaceVerifyOnCheckIn(Boolean faceVerifyOnCheckIn) { this.faceVerifyOnCheckIn = faceVerifyOnCheckIn; }

    public Boolean getFaceVerifyOnCheckOut() { return faceVerifyOnCheckOut; }
    public void setFaceVerifyOnCheckOut(Boolean faceVerifyOnCheckOut) { this.faceVerifyOnCheckOut = faceVerifyOnCheckOut; }

    public Boolean getFaceVerifyAnytime() { return faceVerifyAnytime; }
    public void setFaceVerifyAnytime(Boolean faceVerifyAnytime) { this.faceVerifyAnytime = faceVerifyAnytime; }

    public Boolean getFaceVerificationEnabled() { return faceVerificationEnabled; }
    public void setFaceVerificationEnabled(Boolean faceVerificationEnabled) { this.faceVerificationEnabled = faceVerificationEnabled; }

    public Integer getFaceVerificationFrequency() { return faceVerificationFrequency; }
    public void setFaceVerificationFrequency(Integer faceVerificationFrequency) { this.faceVerificationFrequency = faceVerificationFrequency; }

    public List<String> getFaceVerificationTimes() { return faceVerificationTimes; }
    public void setFaceVerificationTimes(List<String> faceVerificationTimes) { this.faceVerificationTimes = faceVerificationTimes; }

    public Integer getFaceVerificationCountToday() { return faceVerificationCountToday; }
    public void setFaceVerificationCountToday(Integer faceVerificationCountToday) { this.faceVerificationCountToday = faceVerificationCountToday; }

    public Float getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(Float confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public FaceVerificationStatusDTO getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(FaceVerificationStatusDTO verificationStatus) { this.verificationStatus = verificationStatus; }
}
