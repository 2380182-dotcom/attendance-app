package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;

/**
 * Sales territory. Hierarchy (TSE/SR TSE/ASM) lives here, not on
 * CustomerShop, so re-assigning e.g. a TSE for a whole area is a single
 * row update rather than a bulk update across every shop in it.
 */
@Entity
@Table(name = "areas")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({TenantEntityListener.class})
public class Area implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    private String name;

    @ManyToOne
    @JoinColumn(name = "tse_id")
    private HierarchyPerson tse;

    @ManyToOne
    @JoinColumn(name = "sr_tse_id")
    private HierarchyPerson srTse;

    @ManyToOne
    @JoinColumn(name = "asm_id")
    private HierarchyPerson asm;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Area() {}

    // Getters
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getName() { return name; }
    public HierarchyPerson getTse() { return tse; }
    public HierarchyPerson getSrTse() { return srTse; }
    public HierarchyPerson getAsm() { return asm; }
    public Boolean getIsActive() { return isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public void setName(String name) { this.name = name; }
    public void setTse(HierarchyPerson tse) { this.tse = tse; }
    public void setSrTse(HierarchyPerson srTse) { this.srTse = srTse; }
    public void setAsm(HierarchyPerson asm) { this.asm = asm; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
