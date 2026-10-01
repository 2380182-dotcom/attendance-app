package com.dawnbread.attendance.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Task 4: one row of a shop's paginated voucher list. Deliberately carries
 * no `items` collection — the query behind this (SalesRecordRepository)
 * never JOIN FETCHes SalesRecord.items, so Hibernate paginates this at the
 * database level (no HHH90003004 in-memory pagination warning). Per-voucher
 * sale/return totals come from a GROUP BY aggregate over SaleItem, same
 * technique as the shop-list summary. Line items are only ever loaded on
 * the single-voucher detail page/PDF.
 */
public class VoucherListItemDTO {
    private Long voucherId;
    private LocalDate saleDate;
    private LocalTime saleTime;
    private String agentName;
    private Double saleAmount;
    private Double returnAmount;
    private Long totalUnits;
    private Double distanceFromShopMeters;
    private String status;

    public VoucherListItemDTO() {}

    public VoucherListItemDTO(Long voucherId, LocalDate saleDate, LocalTime saleTime, String agentName,
                               Double saleAmount, Double returnAmount, Long totalUnits,
                               Double distanceFromShopMeters, String status) {
        this.voucherId = voucherId;
        this.saleDate = saleDate;
        this.saleTime = saleTime;
        this.agentName = agentName;
        this.saleAmount = saleAmount;
        this.returnAmount = returnAmount;
        this.totalUnits = totalUnits;
        this.distanceFromShopMeters = distanceFromShopMeters;
        this.status = status;
    }

    public Long getVoucherId() { return voucherId; }
    public void setVoucherId(Long voucherId) { this.voucherId = voucherId; }

    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }

    public LocalTime getSaleTime() { return saleTime; }
    public void setSaleTime(LocalTime saleTime) { this.saleTime = saleTime; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

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

    public Double getDistanceFromShopMeters() { return distanceFromShopMeters; }
    public void setDistanceFromShopMeters(Double distanceFromShopMeters) { this.distanceFromShopMeters = distanceFromShopMeters; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
