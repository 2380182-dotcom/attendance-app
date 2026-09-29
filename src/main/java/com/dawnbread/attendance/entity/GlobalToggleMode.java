package com.dawnbread.attendance.entity;

/**
 * A tenant-wide master override for a per-shop yes/no setting (geofence
 * enforcement, QR requirement). PER_SHOP is the default and preserves
 * today's exact behavior — each shop's own toggle decides. FORCE_ON/
 * FORCE_OFF override every shop without touching (or losing) any shop's
 * individual setting underneath; switching back to PER_SHOP instantly
 * restores whatever each shop had.
 */
public enum GlobalToggleMode {
    PER_SHOP,
    FORCE_ON,
    FORCE_OFF
}
