package com.dawnbread.attendance.entity;

/**
 * A truly closed, fixed set — unlike Agent.role (deliberately an open
 * string), this is exactly SALE/RETURN/UNSOLD/RETURN_BEFORE_EXPIRY and
 * nothing else, so a real enum (stored via @Enumerated(EnumType.STRING) on
 * SaleItem) is the right fit here.
 *
 * RETURN_BEFORE_EXPIRY ("salesman updates" feature): the salesman takes
 * back still-unexpired bread from a shop and REPLACES it with equal
 * quantity of fresh bread from his own stock — a swap, not a sale or a
 * loss. Priced like RETURN (no discount) and excluded from totalAmount,
 * same as RETURN/UNSOLD, but its quantity DOES consume van stock exactly
 * like a SALE (the fresh replacement leaves the van) — see
 * LmtStockService.reconcile's rbeQty term.
 */
public enum TransactionType {
    SALE,
    RETURN,
    UNSOLD,
    RETURN_BEFORE_EXPIRY
}
