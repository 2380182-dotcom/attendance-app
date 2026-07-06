package com.dawnbread.attendance.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Small shared helper for the role checks controllers need beyond "does a
 * valid token exist" (already enforced by SecurityInterceptor). Every method
 * reads only the role/id attributes SecurityInterceptor derived from the
 * verified JWT — never a client-supplied field — matching the pattern used in
 * AuthController.register() and AgentController's create/update/delete.
 */
public final class AccessControl {

    private AccessControl() {
    }

    public static String callerRole(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        return role != null ? role.toString() : null;
    }

    public static Long callerId(HttpServletRequest request) {
        Object id = request.getAttribute("id");
        if (id == null) {
            return null;
        }
        return id instanceof Long ? (Long) id : Long.valueOf(id.toString());
    }

    public static String callerUsername(HttpServletRequest request) {
        Object username = request.getAttribute("username");
        return username != null ? username.toString() : null;
    }

    /** True if the verified caller's role is one of the given roles. */
    public static boolean hasRole(HttpServletRequest request, String... allowedRoles) {
        String role = callerRole(request);
        if (role == null) {
            return false;
        }
        Set<String> allowed = Arrays.stream(allowedRoles).collect(Collectors.toSet());
        return allowed.contains(role);
    }

    /**
     * True if the verified caller either owns the target agent id (their own
     * token's id claim matches) or holds one of the given elevated roles.
     * Used for endpoints like "read my own attendance/notifications/face
     * status" where the target id also appears as a path variable.
     */
    public static boolean isSelfOrRole(HttpServletRequest request, Long targetAgentId, String... elevatedRoles) {
        Long id = callerId(request);
        if (id != null && targetAgentId != null && id.equals(targetAgentId)) {
            return true;
        }
        return hasRole(request, elevatedRoles);
    }
}
