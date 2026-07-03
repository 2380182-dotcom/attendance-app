package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.FaceTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FaceTemplateRepository extends JpaRepository<FaceTemplate, Long> {

    Optional<FaceTemplate> findByAgent_Id(Long agentId);

    void deleteByAgent_Id(Long agentId);

    boolean existsByAgent_Id(Long agentId);
}
