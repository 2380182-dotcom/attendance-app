package com.dawnbread.attendance.dto;

import java.time.LocalDate;

/** GET/POST/PUT /api/holidays body. */
public record HolidayDTO(Long id, LocalDate holidayDate, String name) {
}
