package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.repository.MartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class MartService {

    @Autowired
    private MartRepository martRepository;

    public Mart createMart(Mart mart) {
        if (martRepository.existsByName(mart.getName())) {
            throw new RuntimeException("Mart with name already exists: " + mart.getName());
        }
        mart.setCreatedAt(LocalDateTime.now());
        return martRepository.save(mart);
    }

    public List<Mart> getAllMarts() {
        return martRepository.findAll();
    }

    public Optional<Mart> getMartById(Long id) {
        return martRepository.findById(id);
    }

    public Optional<Mart> getMartByName(String name) {
        return martRepository.findByName(name);
    }

    public List<Mart> searchMartsByName(String name) {
        return martRepository.findByNameContainingIgnoreCase(name);
    }

    public List<Mart> searchMartsByAddress(String address) {
        return martRepository.findByAddressContainingIgnoreCase(address);
    }

    public List<Mart> findMartsWithinRadius(double latitude, double longitude, double radiusKm) {
        return martRepository.findMartsWithinRadius(latitude, longitude, radiusKm);
    }

    public Mart updateMart(Long id, Mart martDetails) {
        Mart mart = martRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mart not found with id: " + id));
        
        if (martDetails.getName() != null) {
            Optional<Mart> existingMart = martRepository.findByName(martDetails.getName());
            if (existingMart.isPresent() && !existingMart.get().getId().equals(id)) {
                throw new RuntimeException("Mart name already taken: " + martDetails.getName());
            }
            mart.setName(martDetails.getName());
        }
        if (martDetails.getAddress() != null) {
            mart.setAddress(martDetails.getAddress());
        }
        if (martDetails.getLatitude() != null) {
            mart.setLatitude(martDetails.getLatitude());
        }
        if (martDetails.getLongitude() != null) {
            mart.setLongitude(martDetails.getLongitude());
        }
        if (martDetails.getRadius() != null) {
            mart.setRadius(martDetails.getRadius());
        }
        
        return martRepository.save(mart);
    }

    public void deleteMart(Long id) {
        if (!martRepository.existsById(id)) {
            throw new RuntimeException("Mart not found with id: " + id);
        }
        martRepository.deleteById(id);
    }

    public void deleteMartByName(String name) {
        Mart mart = martRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Mart not found with name: " + name));
        martRepository.delete(mart);
    }

    public List<Mart> getActiveMarts() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        return martRepository.findMartsWithActiveAgents(startOfDay, endOfDay);
    }

    public List<Object[]> getTopMartsByAttendance(int limit) {
        return martRepository.findTopMartsByAttendance(limit);
    }

    public boolean existsByName(String name) {
        return martRepository.existsByName(name);
    }

    public long countMarts() {
        return martRepository.count();
    }
}
