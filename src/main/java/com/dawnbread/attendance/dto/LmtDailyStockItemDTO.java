package com.dawnbread.attendance.dto;

import java.util.List;

public class LmtDailyStockItemDTO {

    private Long id;
    private Long productId;
    private String productName;
    private Integer openingStock;
    private Integer returnedQty;
    private Integer unsoldQty;
    private Integer soldQty;
    private Integer missingQty;
    /**
     * Per-shop breakdown of this product's Returned total — populated only
     * by the management-only reconciliation report (see
     * LmtStockService.getReconciliationReport); null on every LMT
     * self-service response (/today, /morning, /reconcile).
     */
    private List<LmtShopReturnDTO> returnsByShop;

    public LmtDailyStockItemDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getOpeningStock() { return openingStock; }
    public void setOpeningStock(Integer openingStock) { this.openingStock = openingStock; }

    public Integer getReturnedQty() { return returnedQty; }
    public void setReturnedQty(Integer returnedQty) { this.returnedQty = returnedQty; }

    public Integer getUnsoldQty() { return unsoldQty; }
    public void setUnsoldQty(Integer unsoldQty) { this.unsoldQty = unsoldQty; }

    public Integer getSoldQty() { return soldQty; }
    public void setSoldQty(Integer soldQty) { this.soldQty = soldQty; }

    public Integer getMissingQty() { return missingQty; }
    public void setMissingQty(Integer missingQty) { this.missingQty = missingQty; }

    public List<LmtShopReturnDTO> getReturnsByShop() { return returnsByShop; }
    public void setReturnsByShop(List<LmtShopReturnDTO> returnsByShop) { this.returnsByShop = returnsByShop; }
}
