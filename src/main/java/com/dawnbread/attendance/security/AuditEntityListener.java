package com.dawnbread.attendance.security;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.AuditLog;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.entity.TenantAware;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreRemove;

import java.time.LocalDateTime;

/**
 * Automatic audit trail for entity mutations — fires from Hibernate's own
 * lifecycle callbacks, so it runs no matter which code path saved or deleted
 * the entity. Unlike AuditLogService.logAction() (still used for non-entity
 * events like login attempts, since there's no entity mutation for a
 * listener to hook), this cannot be skipped by a service method that forgets
 * to call it — there is no save/delete path that bypasses Hibernate.
 *
 * Deliberately allowlists which fields go into `details` per entity type,
 * rather than reflecting over all fields — Agent carries password/
 * faceTemplate/faceEmbedding, and an audit log that captured those would
 * just reintroduce the exact problem Findings 01/02 closed, in a new table.
 *
 * Scoped to Agent and Mart: the two low-volume, admin-managed entities where
 * losing history actually hurts — this is a direct response to a real
 * incident where an Agent row disappeared with no trace of who removed it or
 * when. High-volume transactional entities (Attendance, SalesRecord) already
 * carry their own timestamps and submitter, and are out of scope here —
 * auditing every check-in/sale as a second row would multiply this table's
 * size for data that's already timestamped at the source.
 */
public class AuditEntityListener {

    @PostPersist
    public void onCreate(Object entity) {
        record(entity, "CREATED");
    }

    @PostUpdate
    public void onUpdate(Object entity) {
        record(entity, "UPDATED");
    }

    @PreRemove
    public void onRemove(Object entity) {
        record(entity, "DELETED");
    }

    private void record(Object entity, String verb) {
        String details = describe(entity);
        if (details == null) {
            return; // not an audited entity type
        }

        AuditLog log = new AuditLog();
        log.setAction(entity.getClass().getSimpleName().toUpperCase() + "_" + verb);
        log.setUsername(AuditContext.getUsername() != null ? AuditContext.getUsername() : "SYSTEM");
        log.setDetails(details);
        log.setIpAddress(AuditContext.getIpAddress());
        log.setTimestamp(LocalDateTime.now());
        log.setStatus("SUCCESS");
        log.setTenantId(resolveTenantId(entity));

        AuditLogRepositoryHolder.get().save(log);
    }

    private String describe(Object entity) {
        if (entity instanceof Agent agent) {
            return String.format("id=%s agentId=%s name=%s role=%s department=%s isActive=%s",
                    agent.getId(), agent.getAgentId(), agent.getName(), agent.getRole(),
                    agent.getDepartment(), agent.getIsActive());
        }
        if (entity instanceof Mart mart) {
            return String.format("id=%s name=%s address=%s isActive=%s geoFencingEnabled=%s",
                    mart.getId(), mart.getName(), mart.getAddress(), mart.getIsActive(), mart.getGeoFencingEnabled());
        }
        return null;
    }

    /**
     * Resolves from the audited entity itself (it's TenantAware and already
     * has its own tenantId set by the time these callbacks fire) rather than
     * TenantContext, which is only populated during an HTTP request — a
     * future background job that mutates Agent/Mart outside a request would
     * otherwise silently lose its audit row.
     */
    private Long resolveTenantId(Object entity) {
        if (entity instanceof TenantAware tenantAware && tenantAware.getTenantId() != null) {
            return tenantAware.getTenantId();
        }
        return TenantContext.getTenantId();
    }
}
