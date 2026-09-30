package com.dawnbread.attendance.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Task 3: a SalesRecord exists for this agent/shop/day, but no successful QR scan does. */
public class VoucherWithoutScanDTO {
    private Long salesRecordId;
    private Long agentId;
    private Long shopId;
    private String shopCode;
    private String shopName;
    private LocalDate saleDate;
    private LocalTime saleTime;
    private Double totalAmount;

    public VoucherWithoutScanDTO() {}

    public Long getSalesRecordId() { return salesRecordId; }
    public void setSalesRecordId(Long salesRecordId) { this.salesRecordId = salesRecordId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }

    public LocalTime getSaleTime() { return saleTime; }
    public void setSaleTime(LocalTime saleTime) { this.saleTime = saleTime; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
}
