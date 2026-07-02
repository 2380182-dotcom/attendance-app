package com.dawnbread.attendance.dto;

public class AgentPerformanceDTO {
    private Long agentId;
    private String agentName;
    private String employeeId;
    private Double attendancePercent;
    private Integer totalSalesUnits;
    private Double totalSalesRevenue;
    private String status;

    public AgentPerformanceDTO() {}

    public AgentPerformanceDTO(Long agentId, String agentName, String employeeId, Double attendancePercent, Integer totalSalesUnits, Double totalSalesRevenue, String status) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.employeeId = employeeId;
        this.attendancePercent = attendancePercent;
        this.totalSalesUnits = totalSalesUnits;
        this.totalSalesRevenue = totalSalesRevenue;
        this.status = status;
    }

    // Getters and Setters
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public Double getAttendancePercent() { return attendancePercent; }
    public void setAttendancePercent(Double attendancePercent) { this.attendancePercent = attendancePercent; }

    public Integer getTotalSalesUnits() { return totalSalesUnits; }
    public void setTotalSalesUnits(Integer totalSalesUnits) { this.totalSalesUnits = totalSalesUnits; }

    public Double getTotalSalesRevenue() { return totalSalesRevenue; }
    public void setTotalSalesRevenue(Double totalSalesRevenue) { this.totalSalesRevenue = totalSalesRevenue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
