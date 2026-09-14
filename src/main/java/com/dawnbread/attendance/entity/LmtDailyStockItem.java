package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "lmt_daily_stock_item", uniqueConstraints = {
        @UniqueConstraint(name = "ux_lmt_daily_stock_item_stock_product",
                columnNames = {"lmt_daily_stock_id", "product_id"})
})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
public class LmtDailyStockItem implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lmt_daily_stock_id", nullable = false)
    private LmtDailyStock lmtDailyStock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "opening_stock", nullable = false)
    private Integer openingStock = 0;

    // Entered by the LMT at night reconciliation — defaults to 0 until then.
    @Column(name = "returned_qty", nullable = false)
    private Integer returnedQty = 0;

    // Entered by the LMT at night reconciliation — defaults to 0 until then.
    @Column(name = "unsold_qty", nullable = false)
    private Integer unsoldQty = 0;

    // Computed at reconciliation time from that day's SaleItem rows
    // (transaction_type = SALE) for this agent/product/date — not entered
    // by the LMT, stored here so the reconciled figure is a stable
    // historical record rather than re-derived on every read.
    @Column(name = "sold_qty", nullable = false)
    private Integer soldQty = 0;

    // Computed at reconciliation time: opening_stock - sold - returned -
    // unsold. Deliberately allowed to go negative (an LMT selling more
    // than their declared opening stock) — recorded as a real discrepancy
    // for the Sales Department to see, never rejected or clamped.
    @Column(name = "missing_qty", nullable = false)
    private Integer missingQty = 0;

    public LmtDailyStockItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public LmtDailyStock getLmtDailyStock() { return lmtDailyStock; }
    public void setLmtDailyStock(LmtDailyStock lmtDailyStock) { this.lmtDailyStock = lmtDailyStock; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

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
}
