package com.dawnbread.attendance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * A single injectable Clock, fixed to Asia/Karachi — every "what day/time
 * is it right now" decision in the app (ShopVisitScanService, SalesService)
 * should go through this rather than a bare LocalDate.now()/LocalTime.now()
 * (which silently uses the server's actual zone, UTC on Render). Tests
 * override this bean with a fixed Clock to deterministically prove day-
 * attribution at real boundary times (e.g. 2am Karachi) without depending
 * on when the test happens to run.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Karachi"));
    }
}
