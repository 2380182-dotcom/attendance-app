package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.HierarchyPersonCreateDTO;
import com.dawnbread.attendance.entity.HierarchyPerson;
import com.dawnbread.attendance.repository.HierarchyPersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class HierarchyPersonService {

    @Autowired
    private HierarchyPersonRepository hierarchyPersonRepository;

    public HierarchyPerson create(HierarchyPersonCreateDTO dto) {
        HierarchyPerson person = new HierarchyPerson();
        person.setName(dto.getName());
        person.setRoleLabel(dto.getRoleLabel());
        person.setContact(dto.getContact());
        person.setCreatedAt(LocalDateTime.now());
        person.setIsActive(true);
        return hierarchyPersonRepository.save(person);
    }

    public List<HierarchyPerson> getActive() {
        return hierarchyPersonRepository.findByIsActiveTrue();
    }

    public List<HierarchyPerson> getAll() {
        return hierarchyPersonRepository.findAll();
    }

    public Optional<HierarchyPerson> getById(Long id) {
        return hierarchyPersonRepository.findById(id);
    }

    public HierarchyPerson update(Long id, HierarchyPersonCreateDTO dto) {
        HierarchyPerson person = hierarchyPersonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hierarchy person not found with id: " + id));
        if (dto.getName() != null) {
            person.setName(dto.getName());
        }
        if (dto.getRoleLabel() != null) {
            person.setRoleLabel(dto.getRoleLabel());
        }
        if (dto.getContact() != null) {
            person.setContact(dto.getContact());
        }
        return hierarchyPersonRepository.save(person);
    }

    /**
     * Soft-delete only — Area may hold a live FK to this row (tse/srTse/asm),
     * and a hard delete would either fail or silently orphan that reference.
     */
    public void deactivate(Long id) {
        HierarchyPerson person = hierarchyPersonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hierarchy person not found with id: " + id));
        person.setIsActive(false);
        hierarchyPersonRepository.save(person);
    }

    public HierarchyPerson reactivate(Long id) {
        HierarchyPerson person = hierarchyPersonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hierarchy person not found with id: " + id));
        person.setIsActive(true);
        return hierarchyPersonRepository.save(person);
    }
}
