package com.dawnbread.attendance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Production-only marker configuration.
 * Security behavior is enforced via SecurityHeadersFilter, RateLimitInterceptor,
 * and application-prod.properties settings when SPRING_PROFILES_ACTIVE=prod.
 */
@Configuration
@Profile("prod")
public class ProductionSecurityConfig {
}
