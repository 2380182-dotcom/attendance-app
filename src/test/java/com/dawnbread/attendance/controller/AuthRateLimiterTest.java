package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.security.TokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Runs with much stricter limits than production (3 attempts, not 20/5) so a
 * handful of requests can deterministically trigger 429 — overriding these
 * properties gives this class its own isolated Spring context (Spring Test's
 * context cache keys on property overrides), so it cannot leak rate-limit
 * bucket state into, or inherit it from, any other test class.
 *
 * The rate-limit gate runs before any credential lookup, so these tests never
 * need to seed a real Agent/Tenant — a syntactically-valid-but-wrong body is
 * enough to distinguish "rate limited" (429) from "normal auth flow" (401/400).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "auth.rate-limit.ip.max-attempts=3",
        "auth.rate-limit.ip.window-minutes=15",
        "auth.rate-limit.account.max-attempts=3",
        "auth.rate-limit.account.window-minutes=15"
})
class AuthRateLimiterTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> login(String companyCode, String agentId, String password, String xForwardedFor) {
        Map<String, String> body = new HashMap<>();
        body.put("companyCode", companyCode);
        body.put("agentId", agentId);
        body.put("password", password);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (xForwardedFor != null) {
            headers.set("X-Forwarded-For", xForwardedFor);
        }
        return restTemplate.exchange(url("/api/auth/login"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void perIpLimitTriggersAfterMaxAttemptsRegardlessOfAccount() {
        String ip = "203.0.113.10";
        // Three attempts against three DIFFERENT accounts, same IP — isolates
        // the IP dimension: this must trip on IP alone, not the account bucket.
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> response = login("RLTEST", "IP_ACCT_" + i, "wrong-password", ip);
            assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode(),
                    "Attempt " + i + " is within the limit and must not be rate limited: " + response.getBody());
        }

        ResponseEntity<String> fourth = login("RLTEST", "IP_ACCT_FOURTH", "wrong-password", ip);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, fourth.getStatusCode(),
                "The 4th attempt from the same IP must be rate limited even though it targets a new account: " + fourth.getBody());
    }

    @Test
    void perAccountLimitTriggersEvenWhenIpChangesEachAttempt() {
        // Same company+agentId every time, a different IP every time — proves
        // the account bucket alone is sufficient to stop brute force, so an
        // attacker rotating IPs can't dodge it by evading the IP bucket.
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> response = login("RLTEST", "ACCT_TARGET", "wrong-password", "198.51.100." + i);
            assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode(),
                    "Attempt " + i + " is within the limit and must not be rate limited: " + response.getBody());
        }

        ResponseEntity<String> fourth = login("RLTEST", "ACCT_TARGET", "wrong-password", "198.51.100.99");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, fourth.getStatusCode(),
                "The 4th attempt against the same account must be rate limited even from a brand-new IP: " + fourth.getBody());
    }

    /**
     * Proves the ClientIpResolver fix: the FIRST X-Forwarded-For entry is
     * client-controlled and rotated on every request here (as an attacker
     * would to dodge a naive "first entry" parser) — the LAST entry (what a
     * trusted single-hop proxy like Render's edge would itself append) stays
     * constant, and the limiter must key off that, not the spoofable prefix.
     */
    @Test
    void spoofedLeadingXForwardedForEntryDoesNotBypassTheIpLimit() {
        String trustedIp = "192.0.2.55";
        for (int i = 0; i < 3; i++) {
            String spoofedHeader = "10.0.0." + (i * 37 % 250) + ", " + trustedIp;
            ResponseEntity<String> response = login("RLTEST", "SPOOF_ACCT_" + i, "wrong-password", spoofedHeader);
            assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode(),
                    "Attempt " + i + " is within the limit: " + response.getBody());
        }

        // A 4th request with yet another fabricated leading entry, but the
        // same trusted last entry — if the resolver still trusted the first
        // (attacker-controlled) entry, this would look like a "new" IP and
        // sail through. It must not.
        String finalSpoofedHeader = "10.0.0.250, " + trustedIp;
        ResponseEntity<String> fourth = login("RLTEST", "SPOOF_ACCT_FINAL", "wrong-password", finalSpoofedHeader);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, fourth.getStatusCode(),
                "Rotating the spoofable leading X-Forwarded-For entry must not reset the IP bucket: " + fourth.getBody());
    }

    @Test
    void registerEndpointSharesTheIpRateLimitBucket() {
        String ip = "203.0.113.77";
        // Exhaust the IP bucket via login first...
        for (int i = 0; i < 3; i++) {
            login("RLTEST", "SHARED_BUCKET_" + i, "wrong-password", ip);
        }

        // ...then confirm register from the SAME IP is also blocked, proving
        // both endpoints are protected by one shared per-IP throttle. register()
        // requires a Bearer token to reach the controller at all (unlike the
        // deliberately-public login()) — the rate-limit check runs before the
        // role check inside the method body, so any syntactically valid token
        // is enough to prove the 429 fires first, regardless of its role.
        String anyToken = tokenProvider.generateToken(999L, "RATE_LIMIT_TEST_CALLER", "AGENT");
        Map<String, Object> body = new HashMap<>();
        body.put("agentId", "NEW_SPAM_TARGET");
        body.put("name", "Spam Target");
        body.put("email", "spam@example.com");
        body.put("phone", "03001234567");
        body.put("password", "Password123");
        body.put("role", "AGENT");
        body.put("department", "SALES");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(anyToken);
        headers.set("X-Forwarded-For", ip);
        ResponseEntity<String> registerAttempt = restTemplate.exchange(
                url("/api/auth/register"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, registerAttempt.getStatusCode(),
                "register() must be rate limited by the same IP bucket login() exhausted: " + registerAttempt.getBody());
    }
}
