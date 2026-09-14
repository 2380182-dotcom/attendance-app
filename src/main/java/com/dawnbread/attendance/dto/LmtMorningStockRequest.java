package com.dawnbread.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** POST /api/lmt/stock/morning — the SALESMAN_LMT / ADMIN-only flow. */
public class LmtMorningStockRequest {

    @NotNull(message = "Agent ID is required")
    private Long agentId;

    @NotEmpty(message = "At least one product's opening stock is required")
    @Valid
    private List<LmtStockItemRequest> items;

    public LmtMorningStockRequest() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public List<LmtStockItemRequest> getItems() { return items; }
    public void setItems(List<LmtStockItemRequest> items) { this.items = items; }
}
