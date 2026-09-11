package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.AreaCreateDTO;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.HierarchyPerson;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.HierarchyPersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AreaService {

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private HierarchyPersonRepository hierarchyPersonRepository;

    private HierarchyPerson resolvePerson(Long id) {
        if (id == null) {
            return null;
        }
        return hierarchyPersonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hierarchy person not found with id: " + id));
    }

    public Area create(AreaCreateDTO dto) {
        if (areaRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Area with name already exists: " + dto.getName());
        }
        Area area = new Area();
        area.setName(dto.getName());
        area.setTse(resolvePerson(dto.getTseId()));
        area.setSrTse(resolvePerson(dto.getSrTseId()));
        area.setAsm(resolvePerson(dto.getAsmId()));
        area.setCreatedAt(LocalDateTime.now());
        area.setIsActive(true);
        return areaRepository.save(area);
    }

    public List<Area> getActive() {
        return areaRepository.findByIsActiveTrue();
    }

    public List<Area> getAll() {
        return areaRepository.findAll();
    }

    public Optional<Area> getById(Long id) {
        return areaRepository.findById(id);
    }

    public Area update(Long id, AreaCreateDTO dto) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Area not found with id: " + id));

        if (dto.getName() != null) {
            Optional<Area> existing = areaRepository.findByName(dto.getName());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw new RuntimeException("Area name already taken: " + dto.getName());
            }
            area.setName(dto.getName());
        }
        if (dto.getTseId() != null) {
            area.setTse(resolvePerson(dto.getTseId()));
        }
        if (dto.getSrTseId() != null) {
            area.setSrTse(resolvePerson(dto.getSrTseId()));
        }
        if (dto.getAsmId() != null) {
            area.setAsm(resolvePerson(dto.getAsmId()));
        }
        return areaRepository.save(area);
    }

    /**
     * Soft-delete only — CustomerShop.area is a required FK, so a hard
     * delete would fail (or orphan shops) the moment any shop references it.
     */
    public void deactivate(Long id) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Area not found with id: " + id));
        area.setIsActive(false);
        areaRepository.save(area);
    }

    public Area reactivate(Long id) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Area not found with id: " + id));
        area.setIsActive(true);
        return areaRepository.save(area);
    }
}
