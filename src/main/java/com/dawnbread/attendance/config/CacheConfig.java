package com.dawnbread.attendance.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cockpit dashboards only. Each section endpoint is cached for 90 seconds
 * per tenant + filter combination (the cache key always starts with the
 * caller's tenantId — see CockpitCacheKeys), so a page left on 5-minute
 * auto-refresh in several browsers costs one set of queries per 90s, not
 * one per browser. maximumSize keeps memory bounded on the 512 MB server
 * no matter how many filter combinations people try.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SALES_COCKPIT = "salesCockpit";
    public static final String HR_COCKPIT = "hrCockpit";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(SALES_COCKPIT, HR_COCKPIT);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(90))
                .maximumSize(300));
        return manager;
    }
}
