package com.dawnbread.attendance.exception;

/**
 * Thrown by SalesService.submitShopVisit when a concurrent submission with
 * the same requestId wins the DB-level race (ux_sales_records_request_id).
 * Deliberately lets submitShopVisit's own transaction roll back normally —
 * Hibernate poisons a session after a failed flush, so the recovery lookup
 * cannot happen inside that same transaction/session. The caller (the
 * controller, a different bean, so no Spring AOP self-invocation issue)
 * catches this and resolves it via ShopVisitIdempotencyService's own fresh
 * transaction instead.
 */
public class ShopVisitRequestIdRaceException extends RuntimeException {
    private final String requestId;

    public ShopVisitRequestIdRaceException(String requestId) {
        super("requestId race: " + requestId);
        this.requestId = requestId;
    }

    public String getRequestId() {
        return requestId;
    }
}
