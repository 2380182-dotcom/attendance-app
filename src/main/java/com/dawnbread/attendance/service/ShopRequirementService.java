package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.GlobalToggleMode;
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
}
