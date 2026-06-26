package com.dawnbread.attendance.security;

import com.dawnbread.attendance.security.TokenProvider.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
public class SecurityInterceptor implements HandlerInterceptor {

    @Autowired
    private TokenProvider tokenProvider;

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Bypass pre-flight OPTIONS requests for CORS
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        // Allow public auth endpoints (except registration which requires ADMIN)
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/exists")) {
            return true;
        }

        // Extract Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false,\"message\":\"Missing or invalid Authorization header\"}");
            response.setContentType("application/json");
            return false;
        }

        String token = authHeader.substring(7);
        Claims claims = tokenProvider.parseToken(token);
        if (claims == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid or expired token\"}");
            response.setContentType("application/json");
            return false;
        }

        // Save claims in request attributes for controllers to use
        request.setAttribute("userClaims", claims);

        String role = claims.getRole();

        // Enforce role-based access rules:
        
        // 1. ADMIN-Only paths:
        // - Admin endpoints: /api/admin/**
        // - Register new users: /api/auth/register
        // - User mutation endpoints: POST, PUT, DELETE on /api/agents/**
        if (path.startsWith("/api/admin/") || path.startsWith("/api/auth/register")) {
            if (!"ADMIN".equals(role)) {
                return denyAccess(response);
            }
            return true;
        }

        if (path.startsWith("/api/agents")) {
            String method = request.getMethod();
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                if (!"ADMIN".equals(role)) {
                    return denyAccess(response);
                }
                return true;
            }
        }

        // 2. HR & SALES have read-only access to records and reports
        if (path.startsWith("/api/reports/export") && !path.contains("/agent/")) {
            if (!"ADMIN".equals(role) && !"HR".equals(role) && !"SALES".equals(role)) {
                return denyAccess(response);
            }
            return true;
        }

        if (path.startsWith("/api/notifications/hr")) {
            if (!"ADMIN".equals(role) && !"HR".equals(role)) {
                return denyAccess(response);
            }
            return true;
        }

        if (path.startsWith("/api/notifications/sales")) {
            if (!"ADMIN".equals(role) && !"SALES".equals(role)) {
                return denyAccess(response);
            }
            return true;
        }

        // 3. AGENT / Owner-only access checks for paths
        if ("AGENT".equals(role)) {
            // Agents can check in/out themselves, handled in controller/interceptor.
            
            // Profile fetch: /api/agents/agentId/{agentId}
            if (path.startsWith("/api/agents/agentId/")) {
                String targetAgentId = path.substring("/api/agents/agentId/".length());
                if (!claims.getAgentId().equals(targetAgentId)) {
                    return denyAccess(response);
                }
            } 
            
            // Profile fetch by ID: /api/agents/{id}
            else if (path.startsWith("/api/agents/")) {
                Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (pathVariables != null && pathVariables.containsKey("id")) {
                    try {
                        Long targetId = Long.parseLong(pathVariables.get("id"));
                        if (!claims.getId().equals(targetId)) {
                            return denyAccess(response);
                        }
                    } catch (NumberFormatException e) {
                        // ignore or handle
                    }
                }
            }

            // Attendance history access: /api/attendance/agent/{agentId}
            if (path.startsWith("/api/attendance/agent/")) {
                Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (pathVariables != null && pathVariables.containsKey("agentId")) {
                    try {
                        Long targetAgentId = Long.parseLong(pathVariables.get("agentId"));
                        if (!claims.getId().equals(targetAgentId)) {
                            return denyAccess(response);
                        }
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }

            // Geo-fence logs: /api/geo-fence/logs/agent/{agentId}
            if (path.startsWith("/api/geo-fence/logs/agent/")) {
                Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (pathVariables != null && pathVariables.containsKey("agentId")) {
                    try {
                        Long targetAgentId = Long.parseLong(pathVariables.get("agentId"));
                        if (!claims.getId().equals(targetAgentId)) {
                            return denyAccess(response);
                        }
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }

            // Agent notifications: /api/notifications/agent/{agentId}
            if (path.startsWith("/api/notifications/agent/")) {
                Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (pathVariables != null && pathVariables.containsKey("agentId")) {
                    try {
                        Long targetAgentId = Long.parseLong(pathVariables.get("agentId"));
                        if (!claims.getId().equals(targetAgentId)) {
                            return denyAccess(response);
                        }
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }

            // Report export for agent: /api/reports/export/agent/{agentId}
            if (path.startsWith("/api/reports/export/agent/")) {
                Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (pathVariables != null && pathVariables.containsKey("agentId")) {
                    try {
                        Long targetAgentId = Long.parseLong(pathVariables.get("agentId"));
                        if (!claims.getId().equals(targetAgentId)) {
                            return denyAccess(response);
                        }
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }

            // Agents can't fetch all attendance records, open records, logs, or stats
            if (path.equals("/api/attendance") || path.startsWith("/api/attendance/open") || path.startsWith("/api/attendance/status/") || path.startsWith("/api/attendance/report/") || path.startsWith("/api/attendance/statistics") || path.equals("/api/geo-fence/logs")) {
                return denyAccess(response);
            }
        }

        return true;
    }

    private boolean denyAccess(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("{\"success\":false,\"message\":\"Access denied: insufficient permissions\"}");
        response.setContentType("application/json");
        return false;
    }
}
