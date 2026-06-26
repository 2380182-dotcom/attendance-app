package com.dawnbread.attendance.dto;

import java.time.LocalDate;

public class ReportOptions {
    private LocalDate date;
    private Long agentId;
    private Long martId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer year;
    private Integer month;

    public ReportOptions() {}

    // Getters and Setters
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public Long getMartId() { return martId; }
    public void setMartId(Long martId) { this.martId = martId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }
}
