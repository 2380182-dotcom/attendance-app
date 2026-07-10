package com.dawnbread.attendance.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dedicated brute-force throttle for /auth/login and /auth/register —
 * deliberately separate from RateLimitInterceptor's general 300 req/min
 * anti-abuse throttle, which is far too loose to meaningfully slow down
 * credential stuffing or account-creation spam on its own.
 *
 * Two independent dimensions, both checked before any credential/DB work:
 *  - per-IP: protects against a single source hammering either endpoint,
 *    regardless of which account it's targeting.
 *  - per-account: protects a specific credential pair (login) or a specific
 *    target agentId (register) from being brute-forced/spammed even by an
 *    attacker who rotates IPs to dodge the per-IP limit.
 *
 * A simple fixed-window counter, matching RateLimitInterceptor's existing
 * pattern — not adaptive/exponential backoff. Every attempt counts toward
 * the window regardless of outcome (no "reset on success" nuance); that's a
 * reasonable v1 and a possible future refinement, not a gap that leaves this
 * unprotected today.
 *
 * Backed by BoundedKeyedRateLimiter, not a raw map: the account key is built
 * from unvalidated request-body content (companyCode/agentId), so without a
 * bound an unauthenticated caller could grow accountBuckets by one entry per
 * request forever just by sending a new fake agentId each time — a
 * memory-exhaustion vector the rate limiter would otherwise introduce while
 * closing the brute-force one.
 */
@Component
public class AuthRateLimiter {

    private final BoundedKeyedRateLimiter ipLimiter;
    private final BoundedKeyedRateLimiter accountLimiter;

    public AuthRateLimiter(
            @Value("${auth.rate-limit.ip.max-attempts:20}") int ipMaxAttempts,
            @Value("${auth.rate-limit.ip.window-minutes:15}") long ipWindowMinutes,
            @Value("${auth.rate-limit.account.max-attempts:5}") int accountMaxAttempts,
            @Value("${auth.rate-limit.account.window-minutes:15}") long accountWindowMinutes,
            @Value("${auth.rate-limit.ip.max-tracked-keys:10000}") int ipMaxEntries,
            @Value("${auth.rate-limit.account.max-tracked-keys:10000}") int accountMaxEntries) {
        this.ipLimiter = new BoundedKeyedRateLimiter(ipMaxAttempts, ipWindowMinutes * 60_000L, ipMaxEntries);
        this.accountLimiter = new BoundedKeyedRateLimiter(accountMaxAttempts, accountWindowMinutes * 60_000L, accountMaxEntries);
    }

    public boolean allowByIp(String ip) {
        return ipLimiter.allow(ip);
    }

    public boolean allowByAccount(String accountKey) {
        return accountLimiter.allow(accountKey);
    }

    /**
     * Every 5 minutes, well inside both windows' 15-minute default, so a
     * bucket never survives much past the point it stops mattering.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000L)
    public void sweepExpiredBuckets() {
        ipLimiter.sweepExpired();
        accountLimiter.sweepExpired();
    }
}
