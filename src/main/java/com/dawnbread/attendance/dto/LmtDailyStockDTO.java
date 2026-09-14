package com.dawnbread.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class LmtDailyStockDTO {

    private Long id;
    private Long agentId;
    private LocalDate stockDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime reconciledAt;
    private List<LmtDailyStockItemDTO> items;

    public LmtDailyStockDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public LocalDate getStockDate() { return stockDate; }
    public void setStockDate(LocalDate stockDate) { this.stockDate = stockDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(LocalDateTime reconciledAt) { this.reconciledAt = reconciledAt; }

    public List<LmtDailyStockItemDTO> getItems() { return items; }
    public void setItems(List<LmtDailyStockItemDTO> items) { this.items = items; }
}
