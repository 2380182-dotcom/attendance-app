package com.dawnbread.attendance.dto;

/** One explicit per-shop price for a product — absent means "uses the global salesman price". */
public class ShopProductPriceDTO {
    private Long productId;
    private String productName;
    private Double price;

    public ShopProductPriceDTO() {}

    public ShopProductPriceDTO(Long productId, String productName, Double price) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
}
