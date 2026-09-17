package com.dawnbread.attendance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** PUT /api/lmt/customer-shops/{id}/product-discounts/{productId} body. */
public class ShopProductDiscountUpdateDTO {

    @NotNull(message = "Discount percent is required")
    @PositiveOrZero(message = "Discount percent cannot be negative")
    @DecimalMax(value = "100.0", message = "Discount percent cannot exceed 100")
    private Double discountPercent;

    public ShopProductDiscountUpdateDTO() {}

    public Double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Double discountPercent) { this.discountPercent = discountPercent; }
}
