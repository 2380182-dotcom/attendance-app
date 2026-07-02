package com.dawnbread.attendance.dto;

import java.util.List;

public class HRDashboardDTO {
    private Integer totalAgents;
    private Integer checkedInCount;
    private Double checkedInPercent;
    private Integer lateCount;
    private Double latePercent;
    private Integer absentCount;
    private Double absentPercent;
    private Integer complianceAll3Count;
    private Double complianceAll3Percent;
    private Integer complianceMissing1Count;
    private Double complianceMissing1Percent;
    private Integer complianceMissing2PlusCount;
    private Double complianceMissing2PlusPercent;
    private List<AttendanceSalesRow> attendanceSalesSheet;
    private List<AgentPerformanceDTO> topPerformers;

    public HRDashboardDTO() {}

    public HRDashboardDTO(Integer totalAgents, Integer checkedInCount, Double checkedInPercent, Integer lateCount, Double latePercent, Integer absentCount, Double absentPercent, Integer complianceAll3Count, Double complianceAll3Percent, Integer complianceMissing1Count, Double complianceMissing1Percent, Integer complianceMissing2PlusCount, Double complianceMissing2PlusPercent, List<AttendanceSalesRow> attendanceSalesSheet, List<AgentPerformanceDTO> topPerformers) {
        this.totalAgents = totalAgents;
        this.checkedInCount = checkedInCount;
        this.checkedInPercent = checkedInPercent;
        this.lateCount = lateCount;
        this.latePercent = latePercent;
        this.absentCount = absentCount;
        this.absentPercent = absentPercent;
        this.complianceAll3Count = complianceAll3Count;
        this.complianceAll3Percent = complianceAll3Percent;
        this.complianceMissing1Count = complianceMissing1Count;
        this.complianceMissing1Percent = complianceMissing1Percent;
        this.complianceMissing2PlusCount = complianceMissing2PlusCount;
        this.complianceMissing2PlusPercent = complianceMissing2PlusPercent;
        this.attendanceSalesSheet = attendanceSalesSheet;
        this.topPerformers = topPerformers;
    }

    // Getters and Setters
    public Integer getTotalAgents() { return totalAgents; }
    public void setTotalAgents(Integer totalAgents) { this.totalAgents = totalAgents; }

    public Integer getCheckedInCount() { return checkedInCount; }
    public void setCheckedInCount(Integer checkedInCount) { this.checkedInCount = checkedInCount; }

    public Double getCheckedInPercent() { return checkedInPercent; }
    public void setCheckedInPercent(Double checkedInPercent) { this.checkedInPercent = checkedInPercent; }

    public Integer getLateCount() { return lateCount; }
    public void setLateCount(Integer lateCount) { this.lateCount = lateCount; }

    public Double getLatePercent() { return latePercent; }
    public void setLatePercent(Double latePercent) { this.latePercent = latePercent; }

    public Integer getAbsentCount() { return absentCount; }
    public void setAbsentCount(Integer absentCount) { this.absentCount = absentCount; }

    public Double getAbsentPercent() { return absentPercent; }
    public void setAbsentPercent(Double absentPercent) { this.absentPercent = absentPercent; }

    public Integer getComplianceAll3Count() { return complianceAll3Count; }
    public void setComplianceAll3Count(Integer complianceAll3Count) { this.complianceAll3Count = complianceAll3Count; }

    public Double getComplianceAll3Percent() { return complianceAll3Percent; }
    public void setComplianceAll3Percent(Double complianceAll3Percent) { this.complianceAll3Percent = complianceAll3Percent; }

    public Integer getComplianceMissing1Count() { return complianceMissing1Count; }
    public void setComplianceMissing1Count(Integer complianceMissing1Count) { this.complianceMissing1Count = complianceMissing1Count; }

    public Double getComplianceMissing1Percent() { return complianceMissing1Percent; }
    public void setComplianceMissing1Percent(Double complianceMissing1Percent) { this.complianceMissing1Percent = complianceMissing1Percent; }

    public Integer getComplianceMissing2PlusCount() { return complianceMissing2PlusCount; }
    public void setComplianceMissing2PlusCount(Integer complianceMissing2PlusCount) { this.complianceMissing2PlusCount = complianceMissing2PlusCount; }

    public Double getComplianceMissing2PlusPercent() { return complianceMissing2PlusPercent; }
    public void setComplianceMissing2PlusPercent(Double complianceMissing2PlusPercent) { this.complianceMissing2PlusPercent = complianceMissing2PlusPercent; }

    public List<AttendanceSalesRow> getAttendanceSalesSheet() { return attendanceSalesSheet; }
    public void setAttendanceSalesSheet(List<AttendanceSalesRow> attendanceSalesSheet) { this.attendanceSalesSheet = attendanceSalesSheet; }

    public List<AgentPerformanceDTO> getTopPerformers() { return topPerformers; }
    public void setTopPerformers(List<AgentPerformanceDTO> topPerformers) { this.topPerformers = topPerformers; }

    // Inner class
    public static class AttendanceSalesRow {
        private String agentName;
        private String checkInTime; // e.g. "08:45 ✓" or "09:15 ⚠️" or "❌"
        private String midDayTime;  // e.g. "13:00 ✓" or "❌"
        private String checkOutTime; // e.g. "17:00 ✓" or "❌"
        private Integer units;

        public AttendanceSalesRow() {}

        public AttendanceSalesRow(String agentName, String checkInTime, String midDayTime, String checkOutTime, Integer units) {
            this.agentName = agentName;
            this.checkInTime = checkInTime;
            this.midDayTime = midDayTime;
            this.checkOutTime = checkOutTime;
            this.units = units;
        }

        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }

        public String getCheckInTime() { return checkInTime; }
        public void setCheckInTime(String checkInTime) { this.checkInTime = checkInTime; }

        public String getMidDayTime() { return midDayTime; }
        public void setMidDayTime(String midDayTime) { this.midDayTime = midDayTime; }

        public String getCheckOutTime() { return checkOutTime; }
        public void setCheckOutTime(String checkOutTime) { this.checkOutTime = checkOutTime; }

        public Integer getUnits() { return units; }
        public void setUnits(Integer units) { this.units = units; }
    }
}
