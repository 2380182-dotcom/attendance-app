package com.dawnbread.attendance.dto.cockpit;

import java.time.LocalDate;

/**
 * The cockpit's global filters, already validated and defaulted by the
 * controller. segment is ALL / LOCAL / LMT. areaId and agentId are null when
 * not filtered. cacheKey() is part of every cache key (after the tenantId),
 * so two different filter combinations never share a cached answer.
 */
public record CockpitFilter(LocalDate startDate, LocalDate endDate, String segment, Long areaId, Long agentId) {

    public static final String ROLE_LOCAL = "SALESMAN_LOCAL";
    public static final String ROLE_LMT = "SALESMAN_LMT";

    /** The role a segment maps to; null for ALL (no role clause at all). */
    public String role() {
        if ("LOCAL".equals(segment)) return ROLE_LOCAL;
        if ("LMT".equals(segment)) return ROLE_LMT;
        return null;
    }

    public long days() {
        return endDate.toEpochDay() - startDate.toEpochDay() + 1;
    }

    /** Same filters, different date range — used for "previous period" and sparkline windows. */
    public CockpitFilter withRange(LocalDate start, LocalDate end) {
        return new CockpitFilter(start, end, segment, areaId, agentId);
    }

    public String cacheKey() {
        return startDate + "|" + endDate + "|" + segment + "|" + areaId + "|" + agentId;
    }
}
