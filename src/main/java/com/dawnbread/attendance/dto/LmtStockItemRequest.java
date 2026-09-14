package com.dawnbread.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** One product line of a morning stock entry — see LmtMorningStockRequest. */
public class LmtStockItemRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Opening stock is required")
    @PositiveOrZero(message = "Opening stock cannot be negative")
    private Integer openingStock;

    public LmtStockItemRequest() {}

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getOpeningStock() { return openingStock; }
    public void setOpeningStock(Integer openingStock) { this.openingStock = openingStock; }
}
