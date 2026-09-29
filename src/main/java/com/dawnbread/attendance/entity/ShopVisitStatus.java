package com.dawnbread.attendance.entity;

/** The outcome of one QR-scan attempt — recorded on every scan, pass or fail (see ShopVisitScan). */
public enum ShopVisitStatus {
    SUCCESS,
    /** The scanned code doesn't match any registered shop. */
    INVALID_CODE,
    /** The code resolved to a real shop, but it's deactivated. */
    SHOP_INACTIVE,
    /** The shop requires geofence verification and the salesman was too far away. */
    OUTSIDE_GEOFENCE
}
