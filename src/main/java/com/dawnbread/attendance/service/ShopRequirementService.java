package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.GlobalToggleMode;
import com.dawnbread.attendance.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The single place that resolves "is geofence/QR enforcement actually
 * required for this shop right now" — folding in the tenant-wide master
 * override (LmtSettings.geofenceMode/qrMode) on top of the shop's own
 * toggle. PER_SHOP (the default for every existing tenant) returns exactly
 * the shop's own setting, so this is a byte-identical drop-in for the raw
 * field read it replaces wherever PER_SHOP is in effect.
 */
@Service
public class ShopRequirementService {

    @Autowired
    private LmtSettingsService lmtSettingsService;

    public boolean isGeofenceRequired(CustomerShop shop) {
        GlobalToggleMode mode = lmtSettingsService.getOrCreate().getGeofenceMode();
        if (mode == GlobalToggleMode.FORCE_ON) {
            return true;
        }
        if (mode == GlobalToggleMode.FORCE_OFF) {
            return false;
        }
        return Boolean.TRUE.equals(shop.getGeoFencingEnabled());
    }

    public boolean isQrRequired(CustomerShop shop) {
        GlobalToggleMode mode = lmtSettingsService.getOrCreate().getQrMode();
        if (mode == GlobalToggleMode.FORCE_ON) {
            return true;
        }
        if (mode == GlobalToggleMode.FORCE_OFF) {
            return false;
        }
        return Boolean.TRUE.equals(shop.getQrRequired());
    }

    /**
     * The buffered hard-gate shared by every "I am physically at this shop"
     * action — SalesService.submitShopVisit (sales/returns) and
     * ShopFeedbackService (feedback, "salesman updates" feature). Throws
     * IllegalArgumentException exactly as before extraction; returns the
     * computed distance (null when the shop has no geofence configured and
     * the gate was skipped) for the caller to persist.
     *
     * A local salesman has no check-in to prove where they are, so the
     * shop's location IS the proof: a shop with no geofence configured
     * can't be verified, and must not be recordable from anywhere. LMT
     * keeps its existing behavior: an unconfigured shop skips the gate.
     */
    public Double checkGeofenceOrThrow(CustomerShop shop, boolean isLocalSalesman, double latitude, double longitude) {
        boolean shopHasGeofence = isGeofenceRequired(shop)
                && shop.getLatitude() != null && shop.getLongitude() != null && shop.getRadius() != null;
        if (isLocalSalesman && !shopHasGeofence) {
            throw new IllegalArgumentException("'" + shop.getShopName()
                    + "' has no location set up yet, so a visit here can't be verified. Ask the admin to set the shop's location.");
        }
        if (!shopHasGeofence) {
            return null;
        }
        double distance = GeoUtils.distanceMeters(latitude, longitude, shop.getLatitude(), shop.getLongitude());
        double buffer = lmtSettingsService.getOrCreate().getGeofenceBufferMeters();
        double allowedRadius = shop.getRadius() + buffer;
        if (distance > allowedRadius) {
            throw new IllegalArgumentException(String.format(
                    "Too far from %s to record this visit: %.0fm away, allowed up to %.0fm (shop radius %.0fm + %.0fm buffer).",
                    shop.getShopName(), distance, allowedRadius, shop.getRadius(), buffer));
        }
        return distance;
    }
}
