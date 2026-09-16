package com.dawnbread.attendance.dto;

public class ProductCatalogDTO {
    private Long id;
    private String name;
    private String category;
    private String unit;
    /**
     * P2: kept for backward compatibility with the mobile screens that
     * still read a single display price — set to agentPrice. P3 switches
     * SalesEntryScreen/RecordVisitScreen to read agentPrice/salesmanPrice
     * explicitly instead, at which point this field can be removed.
     */
    private Double price;
    private Double agentPrice;
    private Double salesmanPrice;

    public ProductCatalogDTO() {}

    public ProductCatalogDTO(Long id, String name, String category, String unit, Double price) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.price = price;
    }

    public ProductCatalogDTO(Long id, String name, String category, String unit, Double agentPrice, Double salesmanPrice) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.price = agentPrice;
        this.agentPrice = agentPrice;
        this.salesmanPrice = salesmanPrice;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Double getAgentPrice() { return agentPrice; }
    public void setAgentPrice(Double agentPrice) { this.agentPrice = agentPrice; }

    public Double getSalesmanPrice() { return salesmanPrice; }
    public void setSalesmanPrice(Double salesmanPrice) { this.salesmanPrice = salesmanPrice; }
}
