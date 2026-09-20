package com.dawnbread.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * PUT /api/lmt/customer-shops/{id}/product-prices body. Each entry sets a
 * shop price for one product; a null price removes that product's shop
 * price (falls back to the global salesman price). Products not listed
 * are left as they are.
 */
public class ShopProductPricesUpdateDTO {

    @Valid
    @NotNull(message = "Prices list is required")
    private List<Entry> prices;

    public ShopProductPricesUpdateDTO() {}

    public List<Entry> getPrices() { return prices; }
    public void setPrices(List<Entry> prices) { this.prices = prices; }

    public static class Entry {
        @NotNull(message = "Product id is required")
        private Long productId;

        @PositiveOrZero(message = "Price cannot be negative")
        private Double price;

        public Entry() {}

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
    }
}
