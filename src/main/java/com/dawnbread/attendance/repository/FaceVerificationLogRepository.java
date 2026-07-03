package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.FaceVerificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FaceVerificationLogRepository extends JpaRepository<FaceVerificationLog, Long> {
    List<FaceVerificationLog> findByAgentIdAndVerificationTimeBetween(Long agentId, LocalDateTime start, LocalDateTime end);
}
