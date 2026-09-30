package com.dawnbread.attendance.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task 3 audit finding: SchedulerConfig's three cron jobs had no `zone`, so
 * Spring ran them on the server's UTC clock — "midnight" was actually firing
 * at 5am Pakistan time, "11:59 PM" and "10 PM" similarly ~5 and ~3 hours
 * into the next Pakistan day. Can't unit-test WHEN Spring's scheduler
 * actually fires a cron job (that's framework infrastructure, not app
 * code), but this proves the fix that controls it: every cron job here
 * must declare zone = "Asia/Karachi".
 */
class SchedulerConfigTimezoneTest {

    @Test
    void everyCronScheduledMethodDeclaresKarachiZone() throws NoSuchMethodException {
        assertCronZoneIsKarachi("resetDailyFaceVerificationCounts");
        assertCronZoneIsKarachi("autoCheckoutAllAgents");
        assertCronZoneIsKarachi("markAbsentAgents");
    }

    private void assertCronZoneIsKarachi(String methodName) throws NoSuchMethodException {
        Method method = SchedulerConfig.class.getDeclaredMethod(methodName);
        Scheduled annotation = method.getAnnotation(Scheduled.class);
        assertEquals("Asia/Karachi", annotation.zone(),
                methodName + " must run on Asia/Karachi — without an explicit zone, Spring uses the server's UTC clock");
    }
}
