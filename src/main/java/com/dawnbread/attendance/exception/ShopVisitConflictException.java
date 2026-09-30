package com.dawnbread.attendance.exception;

/**
 * A shop-visit submission reused a requestId that's already attached to a
 * DIFFERENT set of items — not a retry of the same visit, a genuine
 * conflict. Mapped to HTTP 409 in SalesController (every other
 * submitShopVisit failure stays a 400).
 */
public class ShopVisitConflictException extends RuntimeException {
    public ShopVisitConflictException(String message) {
        super(message);
    }
}
