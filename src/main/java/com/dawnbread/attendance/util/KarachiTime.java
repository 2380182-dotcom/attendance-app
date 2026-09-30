package com.dawnbread.attendance.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Shared boundary-conversion helper for every "give me today's window" query
 * that filters a UTC-stored instant column (checkInTime, etc.) — the same
 * pattern first proven in AttendanceService.getDailyReportWithShift
 * (commit e630b17), now applied everywhere the same shape of bug turned up
 * in the Task 3 timezone audit. The server runs on UTC (Render); every
 * agent/salesman/admin is in Pakistan — "today" has to mean Pakistan's
 * calendar day, and the boundary handed to a query against UTC-stored
 * instants has to be converted TO UTC, not left as Karachi wall-clock.
 *
 * Deliberately does NOT touch how instants like checkInTime are written —
 * only the window used to filter them. Changing that field's own storage
 * convention would ripple into every existing reader (see SalesRecord.
 * saleTime's write-site fix in SalesService for why that's a much bigger,
 * riskier change, not repeated here for widely-read fields).
 */
public final class KarachiTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Karachi");

    private KarachiTime() {}

    /** Karachi midnight on this date, expressed as the equivalent UTC instant — the correct lower bound for a UTC-instant-column query. */
    public static LocalDateTime startOfDayUtc(LocalDate karachiDate) {
        return karachiDate.atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Karachi 23:59:59 on this date, expressed as the equivalent UTC instant — the correct upper bound for a UTC-instant-column query. */
    public static LocalDateTime endOfDayUtc(LocalDate karachiDate) {
        return karachiDate.atTime(23, 59, 59).atZone(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
