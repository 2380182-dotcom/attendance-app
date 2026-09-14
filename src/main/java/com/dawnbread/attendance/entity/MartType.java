package com.dawnbread.attendance.entity;

/**
 * COMPANY marks the depot LMTs must check in at each morning before
 * selling (see SalesService.submitShopVisit's check-in gate) — REGULAR is
 * every ordinary shop-visit/agent check-in location, and is the default so
 * every existing Mart and the 25 live Agents' check-in behavior is
 * completely unchanged by this field's introduction.
 */
public enum MartType {
    REGULAR,
    COMPANY
}
