package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {
    
    // ===== CUSTOM FINDER METHODS =====
    
    Optional<Agent> findByAgentId(String agentId);
    List<Agent> findByNameContainingIgnoreCase(String name);
    Optional<Agent> findByEmail(String email);
    List<Agent> findByPhone(String phone);
    boolean existsByAgentId(String agentId);
    boolean existsByEmail(String email);
    List<Agent> findByCreatedAtAfter(LocalDateTime date);
    List<Agent> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    List<Agent> findByName(String name);
    long countByIsActiveTrueAndRole(String role);

    // ===== JPQL QUERIES =====
    
    @Query("SELECT DISTINCT a FROM Agent a JOIN a.attendances at WHERE at.checkInTime BETWEEN :start AND :end")
    List<Agent> findAgentsWithCheckInToday(@Param("start") LocalDateTime todayStart, 
                                           @Param("end") LocalDateTime todayEnd);
    
    @Query("SELECT DISTINCT a FROM Agent a JOIN a.attendances at WHERE at.checkInTime > :date")
    List<Agent> findActiveAgents(@Param("date") LocalDateTime date);
    
    @Query("SELECT a.id, a.name, COUNT(at) as attendanceCount FROM Agent a LEFT JOIN a.attendances at GROUP BY a.id ORDER BY attendanceCount DESC")
    List<Object[]> findAgentsWithAttendanceCount();
}
