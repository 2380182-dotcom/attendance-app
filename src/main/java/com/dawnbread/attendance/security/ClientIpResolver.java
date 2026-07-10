package com.dawnbread.attendance.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP behind Render's single-hop reverse proxy.
 *
 * Audit finding (Low): the previous rate limiter took the FIRST entry of
 * X-Forwarded-For, which is exactly the value a client controls — a caller
 * can set `X-Forwarded-For: 1.2.3.4` on every request and get a fresh rate
 * limit bucket each time, trivially bypassing the limiter.
 *
 * Render's edge is the only hop between the client and this app (see
 * render.yaml — a single `web` service, no CDN/WAF in front of it), and PaaS
 * edges in that topology APPEND the real client IP as the last entry when
 * forwarding — so the LAST entry is the one our own trusted infrastructure
 * added, while any earlier entries could have been forged by the client.
 * This assumption breaks if a second, untrusted proxy hop is ever introduced
 * in front of Render (e.g. a CDN) without updating this resolver.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
