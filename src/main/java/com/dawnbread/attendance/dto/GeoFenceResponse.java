package com.dawnbread.attendance.dto;

import com.dawnbread.attendance.entity.Attendance;

public class GeoFenceResponse {
    private String status;
    private String message;
    private Attendance attendance;

    public GeoFenceResponse() {}

    public GeoFenceResponse(String status, String message, Attendance attendance) {
        this.status = status;
        this.message = message;
        this.attendance = attendance;
    }

    // Getters and Setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Attendance getAttendance() { return attendance; }
    public void setAttendance(Attendance attendance) { this.attendance = attendance; }
}
