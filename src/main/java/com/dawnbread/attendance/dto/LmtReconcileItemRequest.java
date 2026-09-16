package com.dawnbread.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * One product line of a night reconciliation entry — see LmtReconcileRequest.
 *
 * No returnedQty here — LMT flow refinement: Returned is no longer a
 * manual day-level entry. It's computed server-side from that day's
 * per-shop RETURN line items (see LmtStockService.reconcile and
 * SaleItemRepository.sumReturnedQuantityByAgentAndDate), the same way
 * Sold already is. Only Unsold is still entered here.
 */
public class LmtReconcileItemRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Unsold quantity is required")
    @PositiveOrZero(message = "Unsold quantity cannot be negative")
    private Integer unsoldQty;

    public LmtReconcileItemRequest() {}

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getUnsoldQty() { return unsoldQty; }
    public void setUnsoldQty(Integer unsoldQty) { this.unsoldQty = unsoldQty; }
}
