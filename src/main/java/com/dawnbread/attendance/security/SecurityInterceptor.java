package com.dawnbread.attendance.security;

import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.TenantRepository;
import io.jsonwebtoken.Claims;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityInterceptor implements HandlerInterceptor {

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TenantRepository tenantRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip OPTIONS requests (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Skip actuator endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.equals("/actuator/health")) {
            return true;
        }

        // Only login and the agentId-existence check are public. Registration
        // creates accounts (including admin accounts) and must go through the
        // normal token check below, so it can be gated to admins only in
        // AuthController.
        if (path.equals("/api/auth/login") || path.startsWith("/api/auth/exists")) {
            return true;
        }

        // Get token from header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing or invalid Authorization header");
            return false;
        }

        String token = authHeader.substring(7);

        try {
            // Validate token
            if (!tokenProvider.validateToken(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid token");
                return false;
            }

            // Parse token to get claims
            Claims claims = tokenProvider.parseToken(token);
            String username = claims.getSubject();

            // Store username in request for later use
            request.setAttribute("username", username);
            request.setAttribute("role", claims.get("role"));
            Object idClaim = claims.get("id");
            if (idClaim != null) {
                request.setAttribute("id", Long.valueOf(idClaim.toString()));
            }

            // Tenant scoping: every tenant-scoped controller call needs the
            // caller's tenantId to (a) scope the Hibernate filter for every
            // read on this request and (b) let TenantEntityListener stamp it
            // on anything newly created. Super Admin tokens (added in a later
            // step) never carry this claim and must never get the filter
            // enabled — they have no role-check pass on any tenant-data
            // endpoint, so there is nothing here for them to leak into.
            Object tenantIdClaim = claims.get("tenantId");
            Long tenantId = tenantIdClaim != null ? Long.valueOf(tenantIdClaim.toString()) : null;
            if (tenantId == null && !"SUPER_ADMIN".equals(claims.get("role"))) {
                // Bridge: tokens issued before the Company Code login flow
                // shipped carry no tenantId claim at all. There is exactly one
                // tenant in existence during this rollout window, so this is
                // provably correct today and becomes moot as old tokens expire
                // and get reissued with a real claim.
                tenantId = tenantRepository.findFirstByOrderByIdAsc().map(Tenant::getId).orElse(null);
            }
            if (tenantId != null) {
                TenantContext.setTenantId(tenantId);
                Session session = entityManager.unwrap(Session.class);
                session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
            }

            return true;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token validation failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Must clear even though the Hibernate Session itself closes at the
        // end of the request (open-in-view) — Tomcat reuses worker threads
        // across requests, and this ThreadLocal would otherwise leak one
        // tenant's context into the next unrelated request on that thread.
        TenantContext.clear();
    }
}
