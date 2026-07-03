package com.dawnbread.attendance.dto;

import java.time.LocalTime;
import java.util.List;

public class ShiftScheduleDTO {
    private LocalTime shiftStart;
    private LocalTime shiftEnd;
    private Integer gracePeriod;
    private List<String> workingDays;

    public ShiftScheduleDTO() {}

    public ShiftScheduleDTO(LocalTime shiftStart, LocalTime shiftEnd, Integer gracePeriod, List<String> workingDays) {
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
        this.gracePeriod = gracePeriod;
        this.workingDays = workingDays;
    }

    public LocalTime getShiftStart() { return shiftStart; }
    public void setShiftStart(LocalTime shiftStart) { this.shiftStart = shiftStart; }

    public LocalTime getShiftEnd() { return shiftEnd; }
    public void setShiftEnd(LocalTime shiftEnd) { this.shiftEnd = shiftEnd; }

    public Integer getGracePeriod() { return gracePeriod; }
    public void setGracePeriod(Integer gracePeriod) { this.gracePeriod = gracePeriod; }

    public List<String> getWorkingDays() { return workingDays; }
    public void setWorkingDays(List<String> workingDays) { this.workingDays = workingDays; }
}
