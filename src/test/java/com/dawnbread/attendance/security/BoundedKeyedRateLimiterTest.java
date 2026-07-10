package com.dawnbread.attendance.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the fix behind Round 3's self-review finding: the backing
 * map must never grow past its configured bound, no matter how many distinct
 * attacker-controlled keys arrive, and a long key can't inflate memory on
 * its own either.
 */
class BoundedKeyedRateLimiterTest {

    @Test
    void allowsRequestsUnderTheLimit() {
        BoundedKeyedRateLimiter limiter = new BoundedKeyedRateLimiter(3, 60_000L, 100);
        assertTrue(limiter.allow("k"));
        assertTrue(limiter.allow("k"));
        assertTrue(limiter.allow("k"));
    }

    @Test
    void deniesOnceTheLimitIsExceededForThatKey() {
        BoundedKeyedRateLimiter limiter = new BoundedKeyedRateLimiter(3, 60_000L, 100);
        limiter.allow("k");
        limiter.allow("k");
        limiter.allow("k");
        assertFalse(limiter.allow("k"), "4th request for the same key within the window must be denied");
    }

    @Test
    void backingMapNeverGrowsPastMaxEntriesUnderAFloodOfUniqueKeys() {
        int cap = 50;
        BoundedKeyedRateLimiter limiter = new BoundedKeyedRateLimiter(5, 60_000L, cap);

        // Simulates an attacker sending a new fake account/IP-like key on
        // every single request — exactly the scenario that grew
        // AuthRateLimiter.accountBuckets unboundedly before this fix.
        for (int i = 0; i < cap * 20; i++) {
            limiter.allow("attacker-key-" + i);
        }

        assertEquals(cap, limiter.size(),
                "The map must stop growing at the configured cap even after 20x cap distinct keys arrive");
    }

    @Test
    void newKeysAreDeniedRatherThanEvictingAnExistingEntryOnceFull() {
        int cap = 5;
        BoundedKeyedRateLimiter limiter = new BoundedKeyedRateLimiter(5, 60_000L, cap);

        for (int i = 0; i < cap; i++) {
            assertTrue(limiter.allow("victim-" + i), "Filling the map to capacity must succeed for each new key");
        }
        assertEquals(cap, limiter.size());

        // The map is now full. A flood of brand-new keys must be rejected —
        // not silently evict "victim-0" to make room, which would let an
        // attacker reset a specific target's bucket by flooding around it.
        for (int i = 0; i < 100; i++) {
            assertFalse(limiter.allow("newcomer-" + i),
                    "A new key must be denied once the map is at capacity, not accepted by evicting an existing one");
        }
        assertEquals(cap, limiter.size(), "The map size must stay exactly at the cap, not grow past it");

        // And the original victim keys are completely unaffected — still
        // tracked, still consuming from their own real counters.
        assertTrue(limiter.allow("victim-0"), "An existing tracked key must be unaffected by the flood around it");
    }

    @Test
    void oversizedKeysAreCappedRatherThanStoredInFull() {
        BoundedKeyedRateLimiter limiter = new BoundedKeyedRateLimiter(3, 60_000L, 100);
        String base = "x".repeat(200);

        limiter.allow(base + "-first-tail");
        limiter.allow(base + "-second-tail-totally-different");

        // Both keys share the same first 200 characters, so after capping
        // they collide into one bucket — proving the cap actually applies,
        // not just that long keys happen to still work individually.
        assertEquals(1, limiter.size(),
                "Two keys identical in their first 200 characters must be treated as the same bucket after capping");
    }

    @Test
    void sweepExpiredRemovesBucketsIdlePastTheirWindow() throws InterruptedException {
        long shortWindow = 50L;
        BoundedKeyedRateLimiter limiter = new BoundedKeyedRateLimiter(5, shortWindow, 100);

        limiter.allow("stale-key");
        assertEquals(1, limiter.size());

        Thread.sleep(shortWindow + 20);
        limiter.sweepExpired();

        assertEquals(0, limiter.size(), "A bucket idle past its own window must be removed by the sweep");
    }
}
