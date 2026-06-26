package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.GeoFenceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GeoFenceLogRepository extends JpaRepository<GeoFenceLog, Long> {
    
    List<GeoFenceLog> findByAgentIdOrderByCreatedAtDesc(Long agentId);
    
    List<GeoFenceLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
