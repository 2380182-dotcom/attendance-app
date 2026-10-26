package com.dawnbread.attendance.entity;

/** Why a salesman couldn't make a sale at a shop he actually visited (see ShopFeedback). */
public enum ShopFeedbackReason {
    SHOP_CLOSED,
    STOCK_FULL,
    OWNER_NOT_AVAILABLE,
    /** The note is mandatory for this reason — see ShopFeedbackRequest validation. */
    OTHER
}
