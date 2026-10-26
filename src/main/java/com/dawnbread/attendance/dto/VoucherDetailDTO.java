package com.dawnbread.attendance.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Task 4: the single-voucher detail page and PDF. Unlike the shop-list and
 * per-shop list DTOs, this one DOES carry the full item list — safe here
 * because it's exactly one SalesRecord (fetch-joined by id, never
 * paginated), not a collection fetch-join across a page of records.
 */
public class VoucherDetailDTO {
    private Long voucherId;
    private String shopCode;
    private String shopName;
    private String branch;
    private String agentName;
    private String agentRole;
    private LocalDate saleDate;
    private LocalTime saleTime;
    private Double distanceFromShopMeters;
    private String status;
    private String requestId;
    private List<Item> items;

    public static class Item {
        private String productName;
        private String transactionType;
        private Integer quantity;
        private Double unitPrice;
        private Double totalPrice;
        private Double discountPercent;

        public Item() {}

        public Item(String productName, String transactionType, Integer quantity,
                     Double unitPrice, Double totalPrice, Double discountPercent) {
            this.productName = productName;
            this.transactionType = transactionType;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.totalPrice = totalPrice;
            this.discountPercent = discountPercent;
        }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public String getTransactionType() { return transactionType; }
        public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public Double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

        public Double getTotalPrice() { return totalPrice; }
        public void setTotalPrice(Double totalPrice) { this.totalPrice = totalPrice; }

        public Double getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(Double discountPercent) { this.discountPercent = discountPercent; }
    }

    public Long getVoucherId() { return voucherId; }
    public void setVoucherId(Long voucherId) { this.voucherId = voucherId; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getAgentRole() { return agentRole; }
    public void setAgentRole(String agentRole) { this.agentRole = agentRole; }

    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }

    public LocalTime getSaleTime() { return saleTime; }
    public void setSaleTime(LocalTime saleTime) { this.saleTime = saleTime; }

    public Double getDistanceFromShopMeters() { return distanceFromShopMeters; }
    public void setDistanceFromShopMeters(Double distanceFromShopMeters) { this.distanceFromShopMeters = distanceFromShopMeters; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    // "Salesman updates" fix: these breakdown totals are always computed
    // live from `items` (never a stored field) so they're correct whether
    // this is an old or new voucher. saleTotal/returnTotal/netTotal mirror
    // VoucherListItemDTO's saleAmount/returnAmount/netAmount; soldUnits is
    // the SALE-only replacement for the old inflated totalUnits semantics.

    public Double getSaleTotal() { return sumPriceByType("SALE"); }
    public Double getReturnTotal() { return sumPriceByType("RETURN"); }
    public Double getNetTotal() { return getSaleTotal() - getReturnTotal(); }

    public Integer getSoldUnits() { return sumQuantityByType("SALE"); }
    public Integer getReturnedUnits() { return sumQuantityByType("RETURN"); }
    public Integer getUnsoldUnits() { return sumQuantityByType("UNSOLD"); }

    /** "Salesman updates" feature: fresh bread handed over as a Return-Before-Expiry replacement — does not affect saleTotal/returnTotal/netTotal. */
    public Integer getReturnBeforeExpiryUnits() { return sumQuantityByType("RETURN_BEFORE_EXPIRY"); }

    private double sumPriceByType(String type) {
        if (items == null) return 0.0;
        return items.stream()
                .filter(i -> type.equals(i.getTransactionType()))
                .mapToDouble(i -> i.getTotalPrice() != null ? i.getTotalPrice() : 0.0)
                .sum();
    }

    private int sumQuantityByType(String type) {
        if (items == null) return 0;
        return items.stream()
                .filter(i -> type.equals(i.getTransactionType()))
                .mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 0)
                .sum();
    }
}
