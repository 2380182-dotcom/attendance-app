package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;

/**
 * A TSE / SR TSE / ASM reference record — name + optional contact only, no
 * login. roleLabel is a free-text hint ("TSE", "SR_TSE", "ASM", or anything
 * else) used purely so admin UIs can filter the picker list per Area slot;
 * it does not restrict which Area field (tse/srTse/asm) a person can
 * actually be assigned to. Built as its own table now — rather than plain
 * strings on Area — specifically so a later phase (logins/attribution for
 * these people) doesn't require migrating a live table.
 */
@Entity
@Table(name = "hierarchy_persons")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({TenantEntityListener.class})
public class HierarchyPerson implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    private String name;

    @Column(name = "role_label")
    private String roleLabel;

    private String contact;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public HierarchyPerson() {}

    // Getters
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getRoleLabel() { return roleLabel; }
    public String getContact() { return contact; }
    public Boolean getIsActive() { return isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public void setName(String name) { this.name = name; }
    public void setRoleLabel(String roleLabel) { this.roleLabel = roleLabel; }
    public void setContact(String contact) { this.contact = contact; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
