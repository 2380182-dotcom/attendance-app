package com.dawnbread.attendance.dto;

/**
 * "Salesman updates" feature: one shop's Return-Before-Expiry (replaced)
 * quantity for one product on one day — same shape and populated the same
 * way as LmtShopReturnDTO, on LmtDailyStockItemDTO.rbeByShop, management
 * report only.
 */
public class LmtShopRbeDTO {

    private Long shopId;
    private String shopCode;
    private String shopName;
    private Integer rbeQty;

    public LmtShopRbeDTO() {}

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public Integer getRbeQty() { return rbeQty; }
    public void setRbeQty(Integer rbeQty) { this.rbeQty = rbeQty; }
}
