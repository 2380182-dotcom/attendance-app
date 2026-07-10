package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByAgentIdOrderByCreatedAtDesc(Long agentId, Pageable pageable);

    Page<Notification> findByDepartmentOrderByCreatedAtDesc(String department, Pageable pageable);

    List<Notification> findByIsReadFalse();
}
