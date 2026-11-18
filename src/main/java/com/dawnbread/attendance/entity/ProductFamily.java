package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * A product family (V39): one of the app's filter buttons (Bread, Pitta,
 * Burger ...), in sortOrder. nameUr is the button's Urdu label (null =
 * English only). Admin manages them on the Pricing page.
 */
@Entity
@Table(name = "product_families",
        uniqueConstraints = @UniqueConstraint(name = "ux_product_families_tenant_name", columnNames = { "tenant_id", "name" }))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
public class ProductFamily implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "name_ur", length = 60)
    private String nameUr;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    @Override
    public Long getTenantId() { return tenantId; }
    @Override
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameUr() { return nameUr; }
    public void setNameUr(String nameUr) { this.nameUr = nameUr; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
