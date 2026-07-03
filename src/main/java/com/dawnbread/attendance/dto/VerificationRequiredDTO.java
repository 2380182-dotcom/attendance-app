package com.dawnbread.attendance.dto;

import java.time.LocalDate;
import java.util.List;

public class VerificationRequiredDTO {
    private boolean required;
    private String nextRequiredTime;
    private LocalDate date;
    private List<String> schedule;

    public VerificationRequiredDTO() {}

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getNextRequiredTime() { return nextRequiredTime; }
    public void setNextRequiredTime(String nextRequiredTime) { this.nextRequiredTime = nextRequiredTime; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public List<String> getSchedule() { return schedule; }
    public void setSchedule(List<String> schedule) { this.schedule = schedule; }
}
