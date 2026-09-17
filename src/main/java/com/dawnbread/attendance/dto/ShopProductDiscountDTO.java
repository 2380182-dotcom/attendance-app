package com.dawnbread.attendance.dto;

/** One per-product (SKU) discount override for a shop — wins over that shop's overall discountPercent. */
public class ShopProductDiscountDTO {
    private Long productId;
    private String productName;
    private Double discountPercent;

    public ShopProductDiscountDTO() {}

    public ShopProductDiscountDTO(Long productId, String productName, Double discountPercent) {
        this.productId = productId;
        this.productName = productName;
        this.discountPercent = discountPercent;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Double discountPercent) { this.discountPercent = discountPercent; }
}
