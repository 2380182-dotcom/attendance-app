package com.dawnbread.attendance.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter per client IP address.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final boolean enabled;
    private final int requestsPerMinute;
    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitInterceptor(
            @Value("${rate-limit.enabled:true}") boolean enabled,
            @Value("${rate-limit.requests-per-minute:300}") int requestsPerMinute) {
        this.enabled = enabled;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled || HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String clientKey = resolveClientKey(request);
        RateLimitBucket bucket = buckets.computeIfAbsent(clientKey, k -> new RateLimitBucket(requestsPerMinute));

        if (!bucket.tryConsume()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Rate limit exceeded. Try again later.\"}");
            return false;
        }

        return true;
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class RateLimitBucket {
        private final int limit;
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStartMs = System.currentTimeMillis();

        RateLimitBucket(int limit) {
            this.limit = limit;
        }

        synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStartMs >= 60_000L) {
                windowStartMs = now;
                count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }
    }
}
