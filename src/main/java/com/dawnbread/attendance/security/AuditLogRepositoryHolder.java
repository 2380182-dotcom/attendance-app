package com.dawnbread.attendance.security;

import com.dawnbread.attendance.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA instantiates AuditEntityListener itself (via a no-arg constructor), not
 * Spring — so it can't be @Autowired directly. This bean grabs the one
 * Spring-managed dependency the listener needs at startup and exposes it
 * through a static accessor, the same bridge pattern @PostConstruct-style
 * holders use throughout the JPA-listener-needs-a-Spring-bean problem space.
 */
@Component
public class AuditLogRepositoryHolder {

    private static AuditLogRepository repository;

    @Autowired
    public AuditLogRepositoryHolder(AuditLogRepository repository) {
        AuditLogRepositoryHolder.repository = repository;
    }

    static AuditLogRepository get() {
        return repository;
    }
}
