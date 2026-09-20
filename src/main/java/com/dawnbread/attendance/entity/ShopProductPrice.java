package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

/**
 * An explicit price for one product at one shop. Used only by salesman
 * (LMT/local) shop-visit sales as the base price, falling back to
 * Product.salesmanPrice when no row exists — see
 * SalesService.submitShopVisit. Agents never read this.
 */
@Entity
@Table(name = "shop_product_price", uniqueConstraints = {
        @UniqueConstraint(name = "ux_shop_product_price_shop_product",
                columnNames = {"customer_shop_id", "product_id"})
})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
public class ShopProductPrice implements TenantAware {

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

    @Column(name = "price", nullable = false)
    private Double price;

    public ShopProductPrice() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public CustomerShop getCustomerShop() { return customerShop; }
    public void setCustomerShop(CustomerShop customerShop) { this.customerShop = customerShop; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
}
