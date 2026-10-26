package com.dawnbread.attendance.util;

import java.util.TimeZone;

/**
 * Pins the JVM default timezone to UTC.
 *
 * WHY: several places save timestamps with a bare LocalDateTime.now() —
 * most importantly AttendanceService.checkIn / checkOut — which uses the
 * JVM default zone. Every "did this agent already check in today?" lookup
 * (GeoFencingService, the 10 PM absence job, reports) compares those
 * timestamps against KarachiTime.startOfDayUtc/endOfDayUtc, i.e. it assumes
 * they are UTC. Production only worked because Render's servers happen to
 * run in UTC; on a machine set to Pakistan time a real evening check-in
 * looked like "not today" (that is why GeoFenceAttendanceModelIntegrationTest
 * failed locally after ~7 PM). Pinning UTC here makes that assumption
 * explicit and guaranteed, with no change to production behaviour.
 *
 * Applied in AttendanceApplication.main (production), TimeConfig's static
 * block (every Spring context, incl. tests run from an IDE) and the Maven
 * Surefire argLine (-Duser.timezone=UTC, before the test JVM starts).
 *
 * FOLLOW-UP (option b, not done yet): save UTC explicitly in check-in /
 * check-out (LocalDateTime.now(Clock.systemUTC()) or an injected UTC clock)
 * so correctness no longer depends on the JVM default zone at all.
 */
public final class ServerTimeZone {

    private ServerTimeZone() {
    }

    public static void pinToUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
