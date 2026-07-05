package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.AdminStatsDTO;
import com.dawnbread.attendance.dto.AgentRegistrationDTO;
import com.dawnbread.attendance.dto.FaceConfigDTO;
import com.dawnbread.attendance.dto.ShiftScheduleDTO;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.MartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminService {

    @Autowired
    private MartRepository martRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AgentService agentService;

    @Autowired
    private FaceVerificationService faceVerificationService;

    @Autowired
    private ShiftValidationService shiftValidationService;

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
        mart.setIsActive(true);
        return martRepository.save(mart);
    }

    public Mart updateMart(Long id, Mart martDetails) {
        Mart mart = martRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mart not found with id: " + id));

        if (martDetails.getName() != null) mart.setName(martDetails.getName());
        if (martDetails.getAddress() != null) mart.setAddress(martDetails.getAddress());
        if (martDetails.getLatitude() != null) mart.setLatitude(martDetails.getLatitude());
        if (martDetails.getLongitude() != null) mart.setLongitude(martDetails.getLongitude());
        if (martDetails.getRadius() != null) mart.setRadius(martDetails.getRadius());
        if (martDetails.getGeoFencingEnabled() != null) mart.setGeoFencingEnabled(martDetails.getGeoFencingEnabled());

        return martRepository.save(mart);
    }

    /**
     * Soft-deletes a mart (isActive = false) instead of removing the row — attendance
     * history holds a NOT NULL FK to mart_id, so historical records must remain intact
     * and readable. The mart just stops being offered for check-in/geofencing.
     */
    public void deleteMart(Long id) {
        Mart mart = martRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mart not found with id: " + id));
        mart.setIsActive(false);
        martRepository.save(mart);
    }

    /** Reverses a soft-delete, making the mart selectable for check-in/geofencing again. */
    public Mart reactivateMart(Long id) {
        Mart mart = martRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mart not found with id: " + id));
        mart.setIsActive(true);
        return martRepository.save(mart);
    }

    public Mart toggleGeoFence(Long id, Boolean enabled) {
        Mart mart = martRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mart not found with id: " + id));
        mart.setGeoFencingEnabled(enabled);
        return martRepository.save(mart);
    }

    public AdminStatsDTO getAdminDashboardStats() {
        LocalDateTime start = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime end = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        // Active field agents only — matches HR dashboard's "Total Agents" definition.
        // (agentRepository.count() would include ADMIN/HR/SALES accounts and deactivated
        // agents too, which is why this used to show a different, larger number than HR's.)
        long totalAgents = agentRepository.countByIsActiveTrueAndRole("AGENT");
        long activeToday = agentRepository.findAgentsWithCheckInToday(start, end).size();
        long checkInsToday = attendanceRepository.findByCheckInTimeBetween(start, end).size();
        long lateArrivalsToday = attendanceRepository.findByDateRangeAndStatus(start, end, "LATE").size();
        long activeGeoFences = martRepository.findAll().stream()
                .filter(m -> Boolean.TRUE.equals(m.getGeoFencingEnabled()))
                .count();
        long totalMarts = martRepository.count();

        return new AdminStatsDTO(totalAgents, activeToday, checkInsToday, lateArrivalsToday, activeGeoFences, totalMarts);
    }

    public Agent createAgent(AgentRegistrationDTO dto) {
        if (agentRepository.existsByAgentId(dto.getAgentId())) {
            throw new RuntimeException("Agent ID already exists: " + dto.getAgentId());
        }

        Agent agent = new Agent();
        agent.setAgentId(dto.getAgentId());
        agent.setName(dto.getName());
        agent.setEmail(dto.getEmail());
        agent.setPhone(dto.getPhone());
        agent.setPassword(dto.getPassword());
        agent.setRole(dto.getRole() != null ? dto.getRole() : "AGENT");
        agent.setDepartment(dto.getDepartment() != null ? dto.getDepartment() : "AGENT");
        agent.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        agent.setFaceVerificationEnabled(dto.getFaceVerificationEnabled() != null ? dto.getFaceVerificationEnabled() : true);
        agent.setFaceVerificationFrequency(dto.getFaceVerificationFrequency() != null ? dto.getFaceVerificationFrequency() : 2);
        if (dto.getFaceVerificationTimes() != null) {
            agent.setFaceVerificationTimes(dto.getFaceVerificationTimes());
        }
        agent.setShiftStartTime(dto.getShiftStartTime() != null ? dto.getShiftStartTime() : LocalTime.of(9, 0));
        agent.setShiftEndTime(dto.getShiftEndTime() != null ? dto.getShiftEndTime() : LocalTime.of(17, 0));
        agent.setGracePeriodMinutes(dto.getGracePeriodMinutes() != null ? dto.getGracePeriodMinutes() : 15);
        if (dto.getWorkingDays() != null) {
            agent.setWorkingDays(dto.getWorkingDays());
        }
        agent.setCreatedAt(LocalDateTime.now());

        return agentService.createAgent(agent);
    }

    public Agent updateFaceConfig(Long id, FaceConfigDTO config) {
        faceVerificationService.updateFaceConfig(id, config.getEnabled(), config.getFrequency(), config.getVerificationTimes());
        return agentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + id));
    }

    public Agent updateShift(Long id, ShiftScheduleDTO shift) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + id));

        if (shift.getShiftStart() != null) agent.setShiftStartTime(shift.getShiftStart());
        if (shift.getShiftEnd() != null) agent.setShiftEndTime(shift.getShiftEnd());
        if (shift.getGracePeriod() != null) agent.setGracePeriodMinutes(shift.getGracePeriod());
        if (shift.getWorkingDays() != null) agent.setWorkingDays(shift.getWorkingDays());
        agent.setUpdatedAt(LocalDateTime.now());

        return agentRepository.save(agent);
    }

    public ShiftScheduleDTO getAgentSchedule(Long id) {
        return shiftValidationService.getShiftSchedule(id, java.time.LocalDate.now());
    }

    public List<Agent> listAgents(String role, String department, Boolean active) {
        return agentRepository.findAll().stream()
                .filter(a -> role == null || role.equalsIgnoreCase(a.getRole()))
                .filter(a -> department == null || department.equalsIgnoreCase(a.getDepartment()))
                .filter(a -> active == null || active.equals(a.getIsActive()))
                .collect(Collectors.toList());
    }
}
