package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.AuditLog;
import com.dawnbread.attendance.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    public AuditLog logAction(String action, String username, String details, String ipAddress, String status) {
        AuditLog log = new AuditLog(action, username, details, ipAddress, LocalDateTime.now(), status);
        return auditLogRepository.save(log);
    }
}
