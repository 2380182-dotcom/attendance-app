package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

@Entity
@Table(name = "sale_items", uniqueConstraints = {
        @UniqueConstraint(name = "ux_sale_items_agent_product_date", columnNames = {"agent_id", "product_id", "sale_date"})
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
