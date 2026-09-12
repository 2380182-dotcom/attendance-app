package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

@Entity
@Table(name = "sale_items", uniqueConstraints = {
        @UniqueConstraint(name = "ux_sale_items_agent_product_date_shop_type",
                columnNames = {"agent_id", "product_id", "sale_date", "customer_shop_id", "transaction_type"})
})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
public class SaleItem implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_record_id")
    private SalesRecord salesRecord;

    // Denormalized from the parent SalesRecord at creation time (see
    // SalesService) specifically so the database can enforce "one row per
    // agent/product/day" directly — sale_date otherwise only lives on the
    // parent, which can't back a unique constraint on this table. Closes
    // the race where two concurrent submissions could both pass the
    // in-memory duplicate check before either commits (audit Finding 09).
    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    // Denormalized from the parent SalesRecord for the same reason as
    // agentId/saleDate above — the widened V19 unique index needs it on
    // this table (no FK here, same as agentId — real referential integrity
    // is enforced through the sales_record_id -> sales_records ->
    // customer_shop_id chain instead). Defaults to -1 ("no shop") so the
    // legacy /entry and /entry-with-images flow, which never sets this,
    // keeps comparing equal to itself under the widened index exactly as
    // it did under V17's narrower one — NULL would NOT do this (SQL never
    // treats two NULLs as equal in a unique index), which is why this is a
    // real sentinel rather than nullable + a NULL-tolerant index.
    @Column(name = "customer_shop_id", nullable = false)
    private Long customerShopId = -1L;

    // Defaults to SALE so every row created by the untouched legacy flow
    // keeps comparing equal to itself under the widened index, exactly as
    // it did under V17's narrower one.
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType = TransactionType.SALE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Double unitPrice;

    @Column(name = "total_price", nullable = false)
    private Double totalPrice;

    @Column(name = "product_image_url", length = 500)
    private String productImageUrl;

    public SaleItem() {}

    public SaleItem(Product product, Integer quantity, Double unitPrice, Double totalPrice, String productImageUrl) {
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.productImageUrl = productImageUrl;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }

    public Long getCustomerShopId() { return customerShopId; }
    public void setCustomerShopId(Long customerShopId) { this.customerShopId = customerShopId; }

    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public SalesRecord getSalesRecord() { return salesRecord; }
    public void setSalesRecord(SalesRecord salesRecord) { this.salesRecord = salesRecord; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

    public Double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(Double totalPrice) { this.totalPrice = totalPrice; }

    public String getProductImageUrl() { return productImageUrl; }
    public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }
}
