package com.dawnbread.attendance.security;

/**
 * Per-request holder for "who is making this change", set by SecurityInterceptor
 * from the verified JWT — mirrors TenantContext exactly, and exists for the same
 * reason: AuditEntityListener runs inside Hibernate's lifecycle callbacks, which
 * have no access to the HttpServletRequest, so the acting user's identity has to
 * be threaded through a ThreadLocal instead.
 *
 * Must be cleared at the end of every request — see SecurityInterceptor#afterCompletion
 * — since Tomcat reuses worker threads across requests and a leaked value here
 * would attribute one caller's writes to a completely different one.
 */
public final class AuditContext {

    private static final ThreadLocal<String> CURRENT_USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_IP = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void setActor(String username, String ipAddress) {
        CURRENT_USERNAME.set(username);
        CURRENT_IP.set(ipAddress);
    }

    public static String getUsername() {
        return CURRENT_USERNAME.get();
    }

    public static String getIpAddress() {
        return CURRENT_IP.get();
    }

    public static void clear() {
        CURRENT_USERNAME.remove();
        CURRENT_IP.remove();
    }
}
