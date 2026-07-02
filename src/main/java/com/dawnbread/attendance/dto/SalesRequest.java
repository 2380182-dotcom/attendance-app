package com.dawnbread.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class SalesRequest {

    @NotNull(message = "Agent ID is required")
    private Long agentId;

    private String location;

    @NotEmpty(message = "Cart cannot be empty")
    @Valid
    private List<SaleItemRequest> items;

    public SalesRequest() {}

    public SalesRequest(Long agentId, String location, List<SaleItemRequest> items) {
        this.agentId = agentId;
        this.location = location;
        this.items = items;
    }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public List<SaleItemRequest> getItems() { return items; }
    public void setItems(List<SaleItemRequest> items) { this.items = items; }
}
