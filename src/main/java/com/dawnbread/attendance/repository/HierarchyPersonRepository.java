package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.HierarchyPerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HierarchyPersonRepository extends JpaRepository<HierarchyPerson, Long> {

    List<HierarchyPerson> findByIsActiveTrue();
}
