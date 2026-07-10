package com.dawnbread.attendance.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Simple in-memory rate limiter per client IP address.
 *
 * Backed by BoundedKeyedRateLimiter, not a raw map: even after ClientIpResolver
 * closed the spoofable-first-entry bug, a single-hop X-Forwarded-For with no
 * commas is still client-supplied text with no length limit — an attacker
 * could otherwise send an oversized garbage value on every request and grow
 * this map's memory footprint without bound, key by key.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final boolean enabled;
    private final BoundedKeyedRateLimiter limiter;

    public RateLimitInterceptor(
            @Value("${rate-limit.enabled:true}") boolean enabled,
            @Value("${rate-limit.requests-per-minute:300}") int requestsPerMinute,
            @Value("${rate-limit.max-tracked-keys:10000}") int maxEntries) {
        this.enabled = enabled;
        this.limiter = new BoundedKeyedRateLimiter(requestsPerMinute, 60_000L, maxEntries);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled || HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String clientKey = resolveClientKey(request);

        if (!limiter.allow(clientKey)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Rate limit exceeded. Try again later.\"}");
            return false;
        }

        return true;
    }

    private String resolveClientKey(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }

    /** Every 5 minutes — well inside the 1-minute window, so stale entries don't linger. */
    @Scheduled(fixedRate = 5 * 60 * 1000L)
    public void sweepExpiredBuckets() {
        limiter.sweepExpired();
    }
}
