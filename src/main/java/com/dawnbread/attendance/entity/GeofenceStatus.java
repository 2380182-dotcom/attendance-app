package com.dawnbread.attendance.entity;

/** Whether a scan's GPS reading was inside the shop's radius+buffer — independent of whether geofence enforcement was actually required at the time. */
public enum GeofenceStatus {
    INSIDE,
    OUTSIDE,
    /** The shop has no lat/lon/radius configured, so distance could not be computed. */
    NOT_EVALUATED
}
