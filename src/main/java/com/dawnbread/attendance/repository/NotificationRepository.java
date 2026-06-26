package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    List<Notification> findByAgentIdOrderByCreatedAtDesc(Long agentId);
    
    List<Notification> findByDepartmentOrderByCreatedAtDesc(String department);
    
    List<Notification> findByIsReadFalse();
}
