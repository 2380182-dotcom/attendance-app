package com.dawnbread.attendance.dto;

/**
 * One shop's Returned quantity for one product on one day — the Sales
 * Department's per-shop return visibility. Populated only on
 * LmtDailyStockItemDTO.returnsByShop when returned by
 * LmtStockService.getReconciliationReport (the management-only report);
 * never populated on the LMT's own self-service endpoints (/today,
 * /morning, /reconcile).
 */
public class LmtShopReturnDTO {

    private Long shopId;
    private String shopCode;
    private String shopName;
    private Integer returnedQty;

    public LmtShopReturnDTO() {}

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public Integer getReturnedQty() { return returnedQty; }
    public void setReturnedQty(Integer returnedQty) { this.returnedQty = returnedQty; }
}
