package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.AttendanceWithShiftDTO;
import com.dawnbread.attendance.dto.ShiftScheduleDTO;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.repository.AgentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ShiftValidationService {

    private static final Map<DayOfWeek, String> DAY_MAP = new HashMap<>();

    static {
        DAY_MAP.put(DayOfWeek.MONDAY, "MON");
        DAY_MAP.put(DayOfWeek.TUESDAY, "TUE");
        DAY_MAP.put(DayOfWeek.WEDNESDAY, "WED");
        DAY_MAP.put(DayOfWeek.THURSDAY, "THU");
        DAY_MAP.put(DayOfWeek.FRIDAY, "FRI");
        DAY_MAP.put(DayOfWeek.SATURDAY, "SAT");
        DAY_MAP.put(DayOfWeek.SUNDAY, "SUN");
    }

    @Autowired
    private AgentRepository agentRepository;

    public boolean validateShift(Long agentId) {
        Agent agent = getAgent(agentId);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        return isWorkingDay(agent, today) && isWithinShift(agent, now);
    }

    public boolean isWorkingDay(Long agentId, LocalDate date) {
        return isWorkingDay(getAgent(agentId), date);
    }

    public boolean isWorkingDay(Agent agent, LocalDate date) {
        List<String> workingDays = agent.getWorkingDays();
        if (workingDays == null || workingDays.isEmpty()) {
            return true;
        }
        String dayCode = DAY_MAP.get(date.getDayOfWeek());
        return workingDays.contains(dayCode);
    }

    public boolean isWithinShift(Long agentId, LocalTime time) {
        return isWithinShift(getAgent(agentId), time);
    }

    public boolean isWithinShift(Agent agent, LocalTime time) {
        LocalTime start = agent.getShiftStartTime() != null ? agent.getShiftStartTime() : LocalTime.of(9, 0);
        LocalTime end = agent.getShiftEndTime() != null ? agent.getShiftEndTime() : LocalTime.of(17, 0);
        if (end.isAfter(start) || end.equals(start)) {
            return !time.isBefore(start) && !time.isAfter(end);
        }
        return !time.isBefore(start) || !time.isAfter(end);
    }

    public boolean isLateArrival(Long agentId, LocalDateTime checkInTime) {
        return isLateArrival(getAgent(agentId), checkInTime);
    }

    public boolean isLateArrival(Agent agent, LocalDateTime checkInTime) {
        return calculateLateMinutes(agent, checkInTime) > 0;
    }

    public int calculateLateMinutes(Long agentId, LocalDateTime checkInTime) {
        return calculateLateMinutes(getAgent(agentId), checkInTime);
    }

    public int calculateLateMinutes(Agent agent, LocalDateTime checkInTime) {
        LocalTime shiftStart = agent.getShiftStartTime() != null ? agent.getShiftStartTime() : LocalTime.of(9, 0);
        int grace = agent.getGracePeriodMinutes() != null ? agent.getGracePeriodMinutes() : 15;
        LocalTime allowedUntil = shiftStart.plusMinutes(grace);
        LocalTime actual = checkInTime.toLocalTime();
        if (!actual.isAfter(allowedUntil)) {
            return 0;
        }
        return (int) Duration.between(allowedUntil, actual).toMinutes();
    }

    public ShiftScheduleDTO getShiftSchedule(Long agentId, LocalDate date) {
        Agent agent = getAgent(agentId);
        return new ShiftScheduleDTO(
                agent.getShiftStartTime(),
                agent.getShiftEndTime(),
                agent.getGracePeriodMinutes(),
                agent.getWorkingDays()
        );
    }

    public AttendanceWithShiftDTO validateAttendanceWithShift(Long agentId, LocalDateTime checkInTime) {
        Agent agent = getAgent(agentId);
        LocalDate date = checkInTime.toLocalDate();
        LocalTime time = checkInTime.toLocalTime();

        boolean workingDay = isWorkingDay(agent, date);
        boolean withinShift = isWithinShift(agent, time);
        int lateMinutes = calculateLateMinutes(agent, checkInTime);
        boolean lateArrival = lateMinutes > 0;

        AttendanceWithShiftDTO dto = new AttendanceWithShiftDTO();
        dto.setShiftSchedule(getShiftSchedule(agentId, date));
        dto.setWorkingDay(workingDay);
        dto.setWithinShift(withinShift);
        dto.setLateArrival(lateArrival);
        dto.setLateMinutes(lateMinutes);

        if (!workingDay) {
            dto.setShiftCompliance("NON_WORKING_DAY");
        } else if (lateArrival) {
            dto.setShiftCompliance("LATE");
        } else if (withinShift) {
            dto.setShiftCompliance("ON_TIME");
        } else {
            dto.setShiftCompliance("OUTSIDE_SHIFT");
        }

        return dto;
    }

    private Agent getAgent(Long agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));
    }
}
