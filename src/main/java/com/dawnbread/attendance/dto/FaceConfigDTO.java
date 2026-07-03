package com.dawnbread.attendance.dto;

import java.util.List;

public class FaceConfigDTO {
    private Boolean enabled;
    private Integer frequency;
    private List<String> verificationTimes;

    public FaceConfigDTO() {}

    public FaceConfigDTO(Boolean enabled, Integer frequency, List<String> verificationTimes) {
        this.enabled = enabled;
        this.frequency = frequency;
        this.verificationTimes = verificationTimes;
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Integer getFrequency() { return frequency; }
    public void setFrequency(Integer frequency) { this.frequency = frequency; }

    public List<String> getVerificationTimes() { return verificationTimes; }
    public void setVerificationTimes(List<String> verificationTimes) { this.verificationTimes = verificationTimes; }
}
