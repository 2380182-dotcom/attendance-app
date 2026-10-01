package com.dawnbread.attendance.dto;

import java.time.LocalDate;

/**
 * Task 4: one row of the Local/LMT Sales Voucher shop-list page. Every
 * total here is a database aggregate (SUM/COUNT over SaleItem, GROUP BY
 * shop) — never computed by loading vouchers/items into Java, per the
 * server's 512MB RAM limit (see SalesRecordRepository's shop-summary
 * query). saleAmount/returnAmount are independent item-level sums, not a
 * subtraction of anything already fanned out through a join.
 */
public class VoucherShopSummaryDTO {
    private Long shopId;
    private String shopCode;
    private String shopName;
    private String branch;
    private Long voucherCount;
    private Double saleAmount;
    private Double returnAmount;
    private Long totalUnits;
    private LocalDate lastVisitDate;

    public VoucherShopSummaryDTO() {}

    public VoucherShopSummaryDTO(Long shopId, String shopCode, String shopName, String branch,
                                  Long voucherCount, Double saleAmount, Double returnAmount,
                                  Long totalUnits, LocalDate lastVisitDate) {
        this.shopId = shopId;
        this.shopCode = shopCode;
        this.shopName = shopName;
        this.branch = branch;
        this.voucherCount = voucherCount;
        this.saleAmount = saleAmount;
        this.returnAmount = returnAmount;
        this.totalUnits = totalUnits;
        this.lastVisitDate = lastVisitDate;
    }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public Long getVoucherCount() { return voucherCount; }
    public void setVoucherCount(Long voucherCount) { this.voucherCount = voucherCount; }

    public Double getSaleAmount() { return saleAmount; }
    public void setSaleAmount(Double saleAmount) { this.saleAmount = saleAmount; }

    public Double getReturnAmount() { return returnAmount; }
    public void setReturnAmount(Double returnAmount) { this.returnAmount = returnAmount; }

    public Double getNetAmount() {
        double sale = saleAmount != null ? saleAmount : 0.0;
        double ret = returnAmount != null ? returnAmount : 0.0;
        return sale - ret;
    }

    public Long getTotalUnits() { return totalUnits; }
    public void setTotalUnits(Long totalUnits) { this.totalUnits = totalUnits; }

    public LocalDate getLastVisitDate() { return lastVisitDate; }
    public void setLastVisitDate(LocalDate lastVisitDate) { this.lastVisitDate = lastVisitDate; }
}
