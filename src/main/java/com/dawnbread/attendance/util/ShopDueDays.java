package com.dawnbread.attendance.util;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * The ONE rule for "which days is a salesman's shop due", shared by Sales >
 * Routes and the Sales cockpit's "Shop visits today" / "Not visited today",
 * so the two always show the same numbers: every active shop of an active
 * salesman is due on each of the salesman's working days, except company
 * holidays. A visit on a day that is not due does not count as a visited
 * due day (it still counts as the shop's last visit).
 */
public final class ShopDueDays {

    private ShopDueDays() {
    }

    /** No working days saved = every day is a working day (same as the cockpit's idle-salesman check). */
    public static boolean worksOn(List<String> workingDays, LocalDate day) {
        return workingDays == null || workingDays.isEmpty()
                || workingDays.contains(day.getDayOfWeek().name().substring(0, 3));
    }

    public static boolean isDue(List<String> workingDays, Set<LocalDate> holidays, LocalDate day) {
        return !holidays.contains(day) && worksOn(workingDays, day);
    }
}
