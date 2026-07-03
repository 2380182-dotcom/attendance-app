package com.dawnbread.attendance.dto;

import java.util.List;

public class SalesEntryResponseDTO {
    private Long salesRecordId;
    private Integer totalUnits;
    private Double totalAmount;
    private List<ItemDetail> items;

    public SalesEntryResponseDTO() {}

    public SalesEntryResponseDTO(Long salesRecordId, Integer totalUnits, Double totalAmount, List<ItemDetail> items) {
        this.salesRecordId = salesRecordId;
        this.totalUnits = totalUnits;
        this.totalAmount = totalAmount;
        this.items = items;
    }

    public Long getSalesRecordId() { return salesRecordId; }
    public void setSalesRecordId(Long salesRecordId) { this.salesRecordId = salesRecordId; }

    public Integer getTotalUnits() { return totalUnits; }
    public void setTotalUnits(Integer totalUnits) { this.totalUnits = totalUnits; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public List<ItemDetail> getItems() { return items; }
    public void setItems(List<ItemDetail> items) { this.items = items; }

    public static class ItemDetail {
        private Long productId;
        private String productName;
        private Integer quantity;
        private Double unitPrice;
        private Double totalPrice;

        public ItemDetail() {}

        public ItemDetail(Long productId, String productName, Integer quantity, Double unitPrice, Double totalPrice) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.totalPrice = totalPrice;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public Double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

        public Double getTotalPrice() { return totalPrice; }
        public void setTotalPrice(Double totalPrice) { this.totalPrice = totalPrice; }
    }
}
