package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;

public class AttendanceWithShiftDTO {
    private AttendanceDTO attendance;
    private ShiftScheduleDTO shiftSchedule;
    private boolean withinShift;
    private boolean workingDay;
    private boolean lateArrival;
    private Integer lateMinutes;
    private boolean faceVerified;
    private String shiftCompliance;

    public AttendanceWithShiftDTO() {}

    public AttendanceDTO getAttendance() { return attendance; }
    public void setAttendance(AttendanceDTO attendance) { this.attendance = attendance; }

    public ShiftScheduleDTO getShiftSchedule() { return shiftSchedule; }
    public void setShiftSchedule(ShiftScheduleDTO shiftSchedule) { this.shiftSchedule = shiftSchedule; }

    public boolean isWithinShift() { return withinShift; }
    public void setWithinShift(boolean withinShift) { this.withinShift = withinShift; }

    public boolean isWorkingDay() { return workingDay; }
    public void setWorkingDay(boolean workingDay) { this.workingDay = workingDay; }

    public boolean isLateArrival() { return lateArrival; }
    public void setLateArrival(boolean lateArrival) { this.lateArrival = lateArrival; }

    public Integer getLateMinutes() { return lateMinutes; }
    public void setLateMinutes(Integer lateMinutes) { this.lateMinutes = lateMinutes; }

    public boolean isFaceVerified() { return faceVerified; }
    public void setFaceVerified(boolean faceVerified) { this.faceVerified = faceVerified; }

    public String getShiftCompliance() { return shiftCompliance; }
    public void setShiftCompliance(String shiftCompliance) { this.shiftCompliance = shiftCompliance; }

    public static class AttendanceDTO {
        private Long id;
        private Long agentId;
        private String agentName;
        private LocalDateTime checkInTime;
        private LocalDateTime checkOutTime;
        private String status;
        private Integer lateMinutes;
        private Boolean faceVerifiedCheckin;
        private Boolean faceVerifiedCheckout;

        public AttendanceDTO() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getAgentId() { return agentId; }
        public void setAgentId(Long agentId) { this.agentId = agentId; }

        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }

        public LocalDateTime getCheckInTime() { return checkInTime; }
        public void setCheckInTime(LocalDateTime checkInTime) { this.checkInTime = checkInTime; }

        public LocalDateTime getCheckOutTime() { return checkOutTime; }
        public void setCheckOutTime(LocalDateTime checkOutTime) { this.checkOutTime = checkOutTime; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Integer getLateMinutes() { return lateMinutes; }
        public void setLateMinutes(Integer lateMinutes) { this.lateMinutes = lateMinutes; }

        public Boolean getFaceVerifiedCheckin() { return faceVerifiedCheckin; }
        public void setFaceVerifiedCheckin(Boolean faceVerifiedCheckin) { this.faceVerifiedCheckin = faceVerifiedCheckin; }

        public Boolean getFaceVerifiedCheckout() { return faceVerifiedCheckout; }
        public void setFaceVerifiedCheckout(Boolean faceVerifiedCheckout) { this.faceVerifiedCheckout = faceVerifiedCheckout; }
    }
}
