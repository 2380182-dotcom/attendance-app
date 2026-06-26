package com.dawnbread.attendance.dto;

public class AdminStatsDTO {
    private long totalAgents;
    private long activeToday;
    private long checkInsToday;
    private long lateArrivalsToday;
    private long activeGeoFences;
    private long totalMarts;

    public AdminStatsDTO() {}

    public AdminStatsDTO(long totalAgents, long activeToday, long checkInsToday, long lateArrivalsToday, long activeGeoFences, long totalMarts) {
        this.totalAgents = totalAgents;
        this.activeToday = activeToday;
        this.checkInsToday = checkInsToday;
        this.lateArrivalsToday = lateArrivalsToday;
        this.activeGeoFences = activeGeoFences;
        this.totalMarts = totalMarts;
    }

    // Getters and Setters
    public long getTotalAgents() { return totalAgents; }
    public void setTotalAgents(long totalAgents) { this.totalAgents = totalAgents; }

    public long getActiveToday() { return activeToday; }
    public void setActiveToday(long activeToday) { this.activeToday = activeToday; }

    public long getCheckInsToday() { return checkInsToday; }
    public void setCheckInsToday(long checkInsToday) { this.checkInsToday = checkInsToday; }

    public long getLateArrivalsToday() { return lateArrivalsToday; }
    public void setLateArrivalsToday(long lateArrivalsToday) { this.lateArrivalsToday = lateArrivalsToday; }

    public long getActiveGeoFences() { return activeGeoFences; }
    public void setActiveGeoFences(long activeGeoFences) { this.activeGeoFences = activeGeoFences; }

    public long getTotalMarts() { return totalMarts; }
    public void setTotalMarts(long totalMarts) { this.totalMarts = totalMarts; }
}
