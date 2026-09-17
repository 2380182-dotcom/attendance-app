package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

/**
 * A per-shop, per-product discount override (Feature 2) — wins over
 * CustomerShop.discountPercent for that one product at that one shop,
 * never both. One row per (shop, product) pair. Applies only to LMT
 * shop-visit SALE lines (see SalesService.submitShopVisit).
 */
@Entity
@Table(name = "shop_product_discount", uniqueConstraints = {
        @UniqueConstraint(name = "ux_shop_product_discount_shop_product",
                columnNames = {"customer_shop_id", "product_id"})
})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
public class ShopProductDiscount implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_shop_id", nullable = false)
    private CustomerShop customerShop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "discount_percent", nullable = false)
    private Double discountPercent;

    public ShopProductDiscount() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public CustomerShop getCustomerShop() { return customerShop; }
    public void setCustomerShop(CustomerShop customerShop) { this.customerShop = customerShop; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Double discountPercent) { this.discountPercent = discountPercent; }
}
