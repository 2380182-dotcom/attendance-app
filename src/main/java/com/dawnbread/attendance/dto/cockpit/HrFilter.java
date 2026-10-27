package com.dawnbread.attendance.dto.cockpit;

import java.time.LocalDate;

/**
 * HR cockpit filters, validated by the controller. department is "" when not
 * filtered (never null, so no untyped null reaches Postgres); martId is null
 * when not filtered. The HR roster is mart agents (role AGENT) only —
 * Local and LMT salesmen no longer check in.
 */
public record HrFilter(LocalDate startDate, LocalDate endDate, String department, Long martId) {

    public String cacheKey() {
        return startDate + "|" + endDate + "|" + department + "|" + martId;
    }
}
