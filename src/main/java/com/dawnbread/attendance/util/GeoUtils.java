package com.dawnbread.attendance.util;

/**
 * Shared haversine distance calculation. Previously duplicated across
 * SalesService (server), and CheckinScreen.js/RecordVisitScreen.js
 * (mobile) — the mobile copies stay as-is (client-side pre-checks only,
 * a separate codebase), but the two server-side call sites now share
 * this one implementation rather than each keeping a private copy.
 */
public final class GeoUtils {

    private static final int EARTH_RADIUS_METERS = 6371000;

    private GeoUtils() {}

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
