package com.dawnbread.attendance.service;

import com.dawnbread.attendance.config.CacheConfig;
import com.dawnbread.attendance.dto.cockpit.CockpitFilter;
import com.dawnbread.attendance.dto.routes.RoutesDTOs.*;
import com.dawnbread.attendance.repository.RoutesQueries;
import com.dawnbread.attendance.repository.SalesCockpitQueries;
import com.dawnbread.attendance.util.ShopDueDays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.dawnbread.attendance.repository.SalesCockpitQueries.id;
import static com.dawnbread.attendance.repository.SalesCockpitQueries.lng;

/**
 * Sales > Routes: loads one company's shop coverage for a date range. The
 * result (about 1 MB for 3,000 shops) is cached for 90 seconds per company
 * + range in its own small cache (CacheConfig.ROUTES), and every view of
 * the page (summary, not-visited list, unassigned list, Excel) is built
 * from it by RoutesViews, so changing a filter or a sort never runs SQL.
 *
 * Due days: each salesman's working days in the range minus company
 * holidays (ShopDueDays, the same rule as the Sales cockpit's "today"
 * cards). Salesmen are grouped by their working-day set (normally one
 * group: MON-SAT) and each group is one query with its own due dates.
 */
@Service
@Transactional(readOnly = true)
public class RoutesService {

    private static final String KEY_PREFIX = "T(com.dawnbread.attendance.security.TenantContext).getTenantId() + ':";

    @Autowired
    private RoutesQueries queries;

    @Autowired
    private SalesCockpitQueries cockpitQueries;

    @Autowired
    private Clock clock;

    @Cacheable(cacheNames = CacheConfig.ROUTES, sync = true, key = KEY_PREFIX + "' + #from + '|' + #to")
    public RoutesData load(LocalDate from, LocalDate to) {
        List<Holiday> holidays = new ArrayList<>();
        Set<LocalDate> holidayDates = new HashSet<>();
        for (Object[] r : queries.holidays(from, to)) {
            holidays.add(new Holiday((LocalDate) r[0], (String) r[1]));
            holidayDates.add((LocalDate) r[0]);
        }
        List<LocalDate> nonHolidays = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (!holidayDates.contains(d)) nonHolidays.add(d);
        }

        // Group the salesmen by working-day set; each group gets its own due dates.
        List<Object[]> salesmen = queries.salesmenWithShops();
        Map<Long, List<String>> workingDays = cockpitQueries.workingDays(salesmen.stream().map(r -> id(r[0])).toList());
        Map<Set<String>, List<Long>> groups = new LinkedHashMap<>();
        for (Object[] r : salesmen) {
            List<String> days = workingDays.get(id(r[0]));
            // No working days saved = every day is a working day (same as the cockpit's idle-salesman check).
            Set<String> key = days == null || days.isEmpty() ? Set.of() : Set.copyOf(days);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(id(r[0]));
        }

        List<ShopRow> shops = new ArrayList<>();
        for (Map.Entry<Set<String>, List<Long>> g : groups.entrySet()) {
            List<LocalDate> due = nonHolidays.stream()
                    .filter(d -> ShopDueDays.worksOn(List.copyOf(g.getKey()), d))
                    .toList();
            for (Object[] r : queries.assignedShops(g.getValue(), due, to)) {
                shops.add(new ShopRow(id(r[0]), (String) r[1], (String) r[2], (String) r[3], id(r[4]), (String) r[5],
                        type((String) r[6]), latest((LocalDate) r[7], (LocalDate) r[8]), due.size(), (int) lng(r[9])));
            }
        }

        List<UnassignedRow> unassigned = new ArrayList<>();
        for (Object[] r : queries.unassignedShops(nonHolidays, to)) {
            String assignedName = (String) r[4];
            String reason = assignedName == null ? "NO_SALESMAN"
                    : !Boolean.TRUE.equals(r[5]) ? "SALESMAN_INACTIVE" : "NOT_A_SALESMAN";
            unassigned.add(new UnassignedRow(id(r[0]), (String) r[1], (String) r[2], (String) r[3], reason, assignedName,
                    latest((LocalDate) r[7], (LocalDate) r[8]), nonHolidays.size(), (int) lng(r[9])));
        }

        int rangeDays = (int) ChronoUnit.DAYS.between(from, to) + 1;
        return new RoutesData(from, to, rangeDays, List.copyOf(holidays), List.copyOf(shops), List.copyOf(unassigned),
                LocalDateTime.now(clock).withNano(0));
    }

    static String type(String role) {
        return CockpitFilter.ROLE_LMT.equals(role) ? "LMT" : "LOCAL";
    }

    private static LocalDate latest(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
