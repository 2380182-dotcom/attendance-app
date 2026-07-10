package com.dawnbread.attendance.controller;

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

/**
 * Separate class from AuthRateLimiterTest specifically to override
 * max-tracked-keys down to a tiny number (own isolated Spring context, same
 * reasoning as that class) — proves the fix for the self-review finding:
 * AuthRateLimiter.accountBuckets used to grow by one entry per request
 * forever, since the account key is built from unvalidated request-body
 * content an unauthenticated caller fully controls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "auth.rate-limit.account.max-attempts=100",
        "auth.rate-limit.account.window-minutes=15",
        "auth.rate-limit.account.max-tracked-keys=3",
        "auth.rate-limit.ip.max-attempts=1000",
        "auth.rate-limit.ip.max-tracked-keys=1000"
})
class AuthRateLimiterBoundedMapTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> login(String agentId, String ip) {
        Map<String, String> body = new HashMap<>();
        body.put("companyCode", "RLTEST");
        body.put("agentId", agentId);
        body.put("password", "wrong-password");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", ip);
        return restTemplate.exchange(url("/api/auth/login"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void aFloodOfUniqueAccountNamesCannotGrowTheMapPastItsBound() {
        // account.max-attempts=100 means none of these would individually
        // trip the per-account limit — a distinct IP per request also rules
        // out the per-IP bucket as the cause of anything observed below.
        // Fill the account-key map to its 3-entry cap.
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> response = login("FLOOD_ACCT_" + i, "203.0.113." + i);
            assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                    "Filling the map to capacity should hit normal auth failure, not rate limiting: " + response.getBody());
        }

        // The map is now full. A never-before-seen account name, from a
        // never-before-seen IP, on its very first attempt — with an
        // unbounded map this would sail through (fresh key, count 1 of 100
        // allowed). With the fix, a full map fails closed instead of
        // growing further, so this brand-new key is rejected outright.
        ResponseEntity<String> overflow = login("NEVER_SEEN_BEFORE", "203.0.113.250");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, overflow.getStatusCode(),
                "A brand-new account key must be rejected once the tracked-key map is full, proving it cannot grow past its bound: "
                        + overflow.getBody());
    }
}
