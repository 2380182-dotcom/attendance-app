package com.dawnbread.attendance.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window keyed rate limiter with bounded memory. Backs both
 * RateLimitInterceptor (per-IP) and AuthRateLimiter (per-IP and per-account)
 * — extracted as one shared implementation so a fix here can't drift between
 * the two call sites.
 *
 * Two independent defenses against unbounded growth, since the key is
 * partly or fully attacker-controlled (a resolved IP, or — for the
 * per-account bucket — literal unvalidated request-body content):
 *
 *  - Keys are length-capped before use, so one oversized key can't bloat
 *    memory on its own regardless of how many distinct keys exist.
 *  - A background sweep (see sweepExpired, called from a @Scheduled method
 *    on the owning component) removes buckets that have been idle past
 *    their own window, so the map doesn't grow forever from one-off keys
 *    that are never seen again.
 *
 * Deliberately NOT an LRU cache. LRU eviction here would let an attacker
 * evict a SPECIFIC victim's bucket by flooding with throwaway keys, silently
 * resetting that victim's rate-limit window — exactly the outcome this class
 * exists to prevent. A hard size cap is enforced instead, and it fails
 * closed (deny) for brand-new keys once full, rather than evicting an
 * unrelated existing entry to make room — so one key's traffic volume can
 * never influence whether another key's protection gets reset early.
 */
public class BoundedKeyedRateLimiter {

    private static final int MAX_KEY_LENGTH = 200;

    private final int limit;
    private final long windowMs;
    private final int maxEntries;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public BoundedKeyedRateLimiter(int limit, long windowMs, int maxEntries) {
        this.limit = limit;
        this.windowMs = windowMs;
        this.maxEntries = maxEntries;
    }

    public boolean allow(String rawKey) {
        String key = capLength(rawKey);
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maxEntries) {
                // Fail closed: reject rather than evict an unrelated entry.
                return false;
            }
            bucket = buckets.computeIfAbsent(key, k -> new Bucket());
        }
        return bucket.tryConsume(limit, windowMs);
    }

    private static String capLength(String rawKey) {
        if (rawKey == null) {
            return "";
        }
        return rawKey.length() > MAX_KEY_LENGTH ? rawKey.substring(0, MAX_KEY_LENGTH) : rawKey;
    }

    /** Removes buckets idle past their own window. Call periodically, not per-request. */
    public void sweepExpired() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(e -> (now - e.getValue().windowStartMs) >= windowMs);
    }

    /** Current backing-map size — package-visible for tests proving the bound holds. */
    int size() {
        return buckets.size();
    }

    private static class Bucket {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStartMs = System.currentTimeMillis();

        synchronized boolean tryConsume(int limit, long windowMs) {
            long now = System.currentTimeMillis();
            if (now - windowStartMs >= windowMs) {
                windowStartMs = now;
                count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }
    }
}
