package com.dawnbread.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class SalesDTO {
    private Long id;
    private Long agentId;
    private String agentName;
    private String employeeId;
    private Double totalAmount;
    private LocalDate saleDate;
    private LocalTime saleTime;
    private String location;
    private List<SaleItemDTO> items;
    private LocalDateTime modifiedAt;
    private String modifiedBy;
    private String overrideReason;

    public SalesDTO() {}

    public SalesDTO(Long id, Long agentId, String agentName, String employeeId, Double totalAmount, LocalDate saleDate, LocalTime saleTime, String location, List<SaleItemDTO> items, LocalDateTime modifiedAt, String modifiedBy, String overrideReason) {
        this.id = id;
        this.agentId = agentId;
        this.agentName = agentName;
        this.employeeId = employeeId;
        this.totalAmount = totalAmount;
        this.saleDate = saleDate;
        this.saleTime = saleTime;
        this.location = location;
        this.items = items;
        this.modifiedAt = modifiedAt;
        this.modifiedBy = modifiedBy;
        this.overrideReason = overrideReason;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }

    public LocalTime getSaleTime() { return saleTime; }
    public void setSaleTime(LocalTime saleTime) { this.saleTime = saleTime; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public List<SaleItemDTO> getItems() { return items; }
    public void setItems(List<SaleItemDTO> items) { this.items = items; }

    public LocalDateTime getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(LocalDateTime modifiedAt) { this.modifiedAt = modifiedAt; }

    public String getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }

    public String getOverrideReason() { return overrideReason; }
    public void setOverrideReason(String overrideReason) { this.overrideReason = overrideReason; }
}
