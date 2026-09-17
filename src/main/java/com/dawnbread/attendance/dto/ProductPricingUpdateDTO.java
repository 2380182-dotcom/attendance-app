package com.dawnbread.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** PUT /api/products/{id}/pricing body. */
public class ProductPricingUpdateDTO {

    @NotNull(message = "Agent price is required")
    @PositiveOrZero(message = "Agent price cannot be negative")
    private Double agentPrice;

    @NotNull(message = "Salesman price is required")
    @PositiveOrZero(message = "Salesman price cannot be negative")
    private Double salesmanPrice;

    public ProductPricingUpdateDTO() {}

    public Double getAgentPrice() { return agentPrice; }
    public void setAgentPrice(Double agentPrice) { this.agentPrice = agentPrice; }

    public Double getSalesmanPrice() { return salesmanPrice; }
    public void setSalesmanPrice(Double salesmanPrice) { this.salesmanPrice = salesmanPrice; }
}
