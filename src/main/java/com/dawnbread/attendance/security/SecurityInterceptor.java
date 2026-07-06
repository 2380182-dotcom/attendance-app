package com.dawnbread.attendance.security;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityInterceptor implements HandlerInterceptor {

    @Autowired
    private TokenProvider tokenProvider;

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

            return true;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token validation failed: " + e.getMessage());
            return false;
        }
    }
}
