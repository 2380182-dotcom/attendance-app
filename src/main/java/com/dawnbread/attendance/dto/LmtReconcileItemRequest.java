package com.dawnbread.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** One product line of a night reconciliation entry — see LmtReconcileRequest. */
public class LmtReconcileItemRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Returned quantity is required")
    @PositiveOrZero(message = "Returned quantity cannot be negative")
    private Integer returnedQty;

    @NotNull(message = "Unsold quantity is required")
    @PositiveOrZero(message = "Unsold quantity cannot be negative")
    private Integer unsoldQty;

    public LmtReconcileItemRequest() {}

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getReturnedQty() { return returnedQty; }
    public void setReturnedQty(Integer returnedQty) { this.returnedQty = returnedQty; }

    public Integer getUnsoldQty() { return unsoldQty; }
    public void setUnsoldQty(Integer unsoldQty) { this.unsoldQty = unsoldQty; }
}
