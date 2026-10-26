package com.dawnbread.attendance.dto;

import java.time.LocalDate;

/** One saved absence (mart agent, Pakistan date) for HR screens. */
public record AbsenceDTO(Long id, Long agentId, String agentName, String department, LocalDate absenceDate) {
}
