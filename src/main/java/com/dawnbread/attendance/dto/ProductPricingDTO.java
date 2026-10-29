package com.dawnbread.attendance.dto;

/** GET/PUT /api/products/pricing — the Sales Dashboard's product-pricing management view. */
public class ProductPricingDTO {

    private Long id;
    private String name;
    private String category;
    private Double agentPrice;
    private Double salesmanPrice;
    // Hidden products (active = false) are not offered to salesmen/agents;
    // a null price means "Not set".
    private boolean active;
    private String unit;

    public ProductPricingDTO() {}

    public ProductPricingDTO(Long id, String name, String category, Double agentPrice, Double salesmanPrice) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.agentPrice = agentPrice;
        this.salesmanPrice = salesmanPrice;
    }

    public ProductPricingDTO(Long id, String name, String category, String unit, Double agentPrice, Double salesmanPrice,
                             boolean active) {
        this(id, name, category, agentPrice, salesmanPrice);
        this.unit = unit;
        this.active = active;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getAgentPrice() { return agentPrice; }
    public void setAgentPrice(Double agentPrice) { this.agentPrice = agentPrice; }

    public Double getSalesmanPrice() { return salesmanPrice; }
    public void setSalesmanPrice(Double salesmanPrice) { this.salesmanPrice = salesmanPrice; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
