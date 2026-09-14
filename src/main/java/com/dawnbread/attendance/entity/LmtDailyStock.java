package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lmt_daily_stock", uniqueConstraints = {
        @UniqueConstraint(name = "ux_lmt_daily_stock_agent_date",
                columnNames = {"agent_id", "stock_date"})
})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
public class LmtDailyStock implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "stock_date", nullable = false)
    private LocalDate stockDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LmtStockStatus status = LmtStockStatus.OPEN;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    public LmtDailyStock() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public LocalDate getStockDate() { return stockDate; }
    public void setStockDate(LocalDate stockDate) { this.stockDate = stockDate; }

    public LmtStockStatus getStatus() { return status; }
    public void setStatus(LmtStockStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(LocalDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
}
