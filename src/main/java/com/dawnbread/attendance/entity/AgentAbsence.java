package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A mart agent (role AGENT) who had no check-in on one of their working
 * days, as recorded by the 10 PM job (V34). Never created for salesmen,
 * non-working days or company holidays. A later check-in on the same
 * Pakistan date removes it; adding a holiday for that date removes it too.
 */
@Entity
@Table(name = "agent_absence",
        uniqueConstraints = @UniqueConstraint(name = "ux_agent_absence_agent_date",
                columnNames = { "tenant_id", "agent_id", "absence_date" }))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
public class AgentAbsence implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    /** Pakistan calendar date. */
    @Column(name = "absence_date", nullable = false)
    private LocalDate absenceDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    @Override
    public Long getTenantId() { return tenantId; }
    @Override
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public LocalDate getAbsenceDate() { return absenceDate; }
    public void setAbsenceDate(LocalDate absenceDate) { this.absenceDate = absenceDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
