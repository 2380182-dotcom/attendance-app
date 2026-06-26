package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.AdminStatsDTO;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.MartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AdminService {

    @Autowired
    private MartRepository martRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    // Mart CRUD
    public List<Mart> getAllMarts() {
        return martRepository.findAll();
    }

    public Optional<Mart> getMartById(Long id) {
        return martRepository.findById(id);
    }

    public Mart createMart(Mart mart) {
        if (martRepository.existsByName(mart.getName())) {
            throw new RuntimeException("Mart with name already exists: " + mart.getName());
        }
        mart.setCreatedAt(LocalDateTime.now());
        if (mart.getRadius() == null) {
            mart.setRadius(100.0);
        }
        if (mart.getGeoFencingEnabled() == null) {
            mart.setGeoFencingEnabled(true);
        }
        return martRepository.save(mart);
    }

    public Mart updateMart(Long id, Mart martDetails) {
        Mart mart = martRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mart not found with id: " + id));

        if (martDetails.getName() != null) {
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
        if (martDetails.getGeoFencingEnabled() != null) {
            mart.setGeoFencingEnabled(martDetails.getGeoFencingEnabled());
        }

        return martRepository.save(mart);
    }

    public void deleteMart(Long id) {
        if (!martRepository.existsById(id)) {
            throw new RuntimeException("Mart not found with id: " + id);
        }
        martRepository.deleteById(id);
    }

    public Mart toggleGeoFence(Long id, Boolean enabled) {
        Mart mart = martRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mart not found with id: " + id));
        mart.setGeoFencingEnabled(enabled);
        return martRepository.save(mart);
    }

    // Dashboard Statistics
    public AdminStatsDTO getAdminDashboardStats() {
        LocalDateTime start = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime end = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        long totalAgents = agentRepository.count();
        long activeToday = agentRepository.findAgentsWithCheckInToday(start, end).size();
        long checkInsToday = attendanceRepository.findByCheckInTimeBetween(start, end).size();
        long lateArrivalsToday = attendanceRepository.findByDateRangeAndStatus(start, end, "LATE").size();
        
        long activeGeoFences = martRepository.findAll().stream()
                .filter(m -> Boolean.TRUE.equals(m.getGeoFencingEnabled()))
                .count();
                
        long totalMarts = martRepository.count();

        return new AdminStatsDTO(totalAgents, activeToday, checkInsToday, lateArrivalsToday, activeGeoFences, totalMarts);
    }
}
