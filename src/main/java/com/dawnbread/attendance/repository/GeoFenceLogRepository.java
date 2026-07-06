package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.GeoFenceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GeoFenceLogRepository extends JpaRepository<GeoFenceLog, Long> {

    List<GeoFenceLog> findByAgentIdOrderByCreatedAtDesc(Long agentId);

    List<GeoFenceLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /** Most recent log for an agent — used to detect a genuine boundary
     * transition (entered/exited) vs. a repeat ping while stationary,
     * without loading the agent's whole geofence history every ~10s. */
    Optional<GeoFenceLog> findFirstByAgentIdOrderByCreatedAtDesc(Long agentId);
}
