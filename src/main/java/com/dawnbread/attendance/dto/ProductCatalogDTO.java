package com.dawnbread.attendance.dto;

public class ProductCatalogDTO {
    private Long id;
    private String name;
    private String category;
    private String unit;
    private Double price;

    public ProductCatalogDTO() {}

    public ProductCatalogDTO(Long id, String name, String category, String unit, Double price) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.price = price;
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
}
