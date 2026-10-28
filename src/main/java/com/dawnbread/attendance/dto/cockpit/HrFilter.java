package com.dawnbread.attendance.dto.cockpit;

import java.time.LocalDate;

/**
 * HR cockpit filters, validated by the controller. department is "" when not
 * filtered (never null, so no untyped null reaches Postgres); martId is null
 * when not filtered. The HR roster is mart agents (role AGENT) only —
 * Local and LMT salesmen no longer check in.
 */
public record HrFilter(LocalDate startDate, LocalDate endDate, String department, Long martId) {

    /**
     * The same filter without the mart. Roster-based numbers (present,
     * absent) use it: an agent who usually works at the filtered mart but
     * checked in somewhere else that day was present, not absent.
     */
    public HrFilter withoutMart() {
        return martId == null ? this : new HrFilter(startDate, endDate, department, null);
    }

    public String cacheKey() {
        return startDate + "|" + endDate + "|" + department + "|" + martId;
    }
}
