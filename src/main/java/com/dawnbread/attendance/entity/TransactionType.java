package com.dawnbread.attendance.entity;

/**
 * A truly closed, fixed set — unlike Agent.role (deliberately an open
 * string), this is exactly SALE/RETURN/UNSOLD and nothing else, so a real
 * enum (stored via @Enumerated(EnumType.STRING) on SaleItem) is the right
 * fit here.
 */
public enum TransactionType {
    SALE,
    RETURN,
    UNSOLD
}
