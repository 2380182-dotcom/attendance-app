package com.dawnbread.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class FaceVerificationStatusDTO {
    private Long agentId;
    private LocalDate date;
    private boolean registered;
    private boolean verificationRequired;
    private String nextRequiredTime;
    private List<VerificationSlot> verifications;

    public FaceVerificationStatusDTO() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public boolean isRegistered() { return registered; }
    public void setRegistered(boolean registered) { this.registered = registered; }

    public boolean isVerificationRequired() { return verificationRequired; }
    public void setVerificationRequired(boolean verificationRequired) { this.verificationRequired = verificationRequired; }

    public String getNextRequiredTime() { return nextRequiredTime; }
    public void setNextRequiredTime(String nextRequiredTime) { this.nextRequiredTime = nextRequiredTime; }

    public List<VerificationSlot> getVerifications() { return verifications; }
    public void setVerifications(List<VerificationSlot> verifications) { this.verifications = verifications; }

    public static class VerificationSlot {
        private String time;
        private String status;
        private LocalDateTime verifiedAt;

        public VerificationSlot() {}

        public VerificationSlot(String time, String status, LocalDateTime verifiedAt) {
            this.time = time;
            this.status = status;
            this.verifiedAt = verifiedAt;
        }

        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getVerifiedAt() { return verifiedAt; }
        public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    }
}
