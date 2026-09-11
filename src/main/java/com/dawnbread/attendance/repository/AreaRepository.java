package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AreaRepository extends JpaRepository<Area, Long> {

    Optional<Area> findByName(String name);
    List<Area> findByIsActiveTrue();
    boolean existsByName(String name);
}
