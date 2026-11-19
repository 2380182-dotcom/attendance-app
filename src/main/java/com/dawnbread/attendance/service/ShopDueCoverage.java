package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.cockpit.CockpitFilter;
import com.dawnbread.attendance.repository.SalesCockpitQueries;
import com.dawnbread.attendance.util.ShopDueDays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.dawnbread.attendance.repository.SalesCockpitQueries.id;
import static com.dawnbread.attendance.repository.SalesCockpitQueries.lng;

/**
 * Per-day shop coverage with the shared due-day rule (ShopDueDays), for the
 * Sales cockpit's "today" cards and its "Coverage % per day" chart, so they
 * show the same numbers as Sales > Routes: a shop is due on its active
 * salesman's working days except company holidays, and only visits on due
 * days count. Small queries: one row per salesman for the shops, one per
 * (day, salesman) for the visits, plus the holidays; the rest is counted here.
 */
@Component
public class ShopDueCoverage {

    @Autowired
    private SalesCockpitQueries queries;

    /** assigned = all active shops of the filter's active salesmen; due/visited per day; holidays by date. */
    public record Coverage(long assigned, Map<LocalDate, Long> due, Map<LocalDate, Long> visited,
                           Map<LocalDate, String> holidays) {

        public long dueOn(LocalDate d) {
            return due.getOrDefault(d, 0L);
        }

        public long visitedOn(LocalDate d) {
            return visited.getOrDefault(d, 0L);
        }

        /** Why nothing was due that day ("Company holiday: name" or "Day off"), null when shops were due. */
        public String nothingDueReason(LocalDate d) {
            if (dueOn(d) > 0) return null;
            String holiday = holidays.get(d);
            return holiday != null ? "Company holiday: " + holiday : "Day off";
        }
    }

    public Coverage forRange(CockpitFilter f, LocalDate from, LocalDate to) {
        Map<Long, Long> shops = new HashMap<>();
        for (Object[] r : queries.assignedShopsBySalesman(f)) shops.put(id(r[0]), lng(r[1]));
        Map<Long, List<String>> workingDays = queries.workingDays(new ArrayList<>(shops.keySet()));
        Map<LocalDate, String> holidays = new HashMap<>();
        for (Object[] r : queries.holidays(from, to)) holidays.put((LocalDate) r[0], (String) r[1]);
        Set<LocalDate> holidayDates = holidays.keySet();

        Map<LocalDate, Long> due = new HashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            long n = 0;
            for (Map.Entry<Long, Long> e : shops.entrySet()) {
                if (ShopDueDays.isDue(workingDays.get(e.getKey()), holidayDates, d)) n += e.getValue();
            }
            due.put(d, n);
        }
        Map<LocalDate, Long> visited = new HashMap<>();
        for (Object[] r : queries.visitedAssignedShopsByDayAndSalesman(f, from, to)) {
            LocalDate d = (LocalDate) r[0];
            if (ShopDueDays.isDue(workingDays.get(id(r[1])), holidayDates, d)) {
                visited.merge(d, lng(r[2]), Long::sum);
            }
        }
        long assigned = shops.values().stream().mapToLong(Long::longValue).sum();
        return new Coverage(assigned, due, visited, holidays);
    }
}
