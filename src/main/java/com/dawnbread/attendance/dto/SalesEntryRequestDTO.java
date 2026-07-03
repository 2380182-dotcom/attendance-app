package com.dawnbread.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public class SalesEntryRequestDTO {
    @NotNull
    private Long agentId;
    private String storeName;
    private LocalDate saleDate;
    @NotEmpty
    @Valid
    private List<SalesEntryItemDTO> items;

    public SalesEntryRequestDTO() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }

    public List<SalesEntryItemDTO> getItems() { return items; }
    public void setItems(List<SalesEntryItemDTO> items) { this.items = items; }

    public static class SalesEntryItemDTO {
        @NotNull
        private Long productId;
        @NotNull
        private Integer quantity;

        public SalesEntryItemDTO() {}

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
