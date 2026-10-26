package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.GlobalToggleMode;
import com.dawnbread.attendance.entity.LmtSettings;
import com.dawnbread.attendance.repository.LmtSettingsRepository;
import com.dawnbread.attendance.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class LmtSettingsService {

    private static final double DEFAULT_BUFFER_METERS = 50.0;
    public static final double DEFAULT_RETURN_ALERT_PERCENT = 15.0;

    @Autowired
    private LmtSettingsRepository lmtSettingsRepository;

    /**
     * V18 seeds a row for every tenant that existed at migration time, but a
     * tenant created afterward would have none — fetch-or-create here rather
     * than assuming the row is always present.
     */
    public LmtSettings getOrCreate() {
        Long tenantId = TenantContext.getTenantId();
        return lmtSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    LmtSettings settings = new LmtSettings();
                    settings.setTenantId(tenantId);
                    settings.setGeofenceBufferMeters(DEFAULT_BUFFER_METERS);
                    settings.setCreatedAt(LocalDateTime.now());
                    settings.setUpdatedAt(LocalDateTime.now());
                    return lmtSettingsRepository.save(settings);
                });
    }

    public LmtSettings updateBuffer(Double geofenceBufferMeters) {
        if (geofenceBufferMeters == null || geofenceBufferMeters < 0) {
            throw new RuntimeException("geofenceBufferMeters must be a non-negative number");
        }
        LmtSettings settings = getOrCreate();
        settings.setGeofenceBufferMeters(geofenceBufferMeters);
        settings.setUpdatedAt(LocalDateTime.now());
        return lmtSettingsRepository.save(settings);
    }

    /**
     * QR shop-visit flow (Q1) — the two tenant-wide master overrides. Both
     * optional/independent: a null value leaves that mode unchanged, so an
     * admin can update just one without resending the other.
     */
    public LmtSettings updateModes(String geofenceMode, String qrMode) {
        LmtSettings settings = getOrCreate();
        if (geofenceMode != null) {
            settings.setGeofenceMode(parseMode(geofenceMode, "geofenceMode"));
        }
        if (qrMode != null) {
            settings.setQrMode(parseMode(qrMode, "qrMode"));
        }
        settings.setUpdatedAt(LocalDateTime.now());
        return lmtSettingsRepository.save(settings);
    }

    /**
     * V33 — read-only lookup for the cockpit (which runs in a read-only
     * transaction, so it must never trigger getOrCreate()'s insert).
     * A tenant with no settings row yet gets the default 15%.
     */
    @Transactional(readOnly = true)
    public double getReturnAlertPercent() {
        return lmtSettingsRepository.findByTenantId(TenantContext.getTenantId())
                .map(LmtSettings::getReturnAlertPercent)
                .orElse(DEFAULT_RETURN_ALERT_PERCENT);
    }

    /** V33 — cockpit return-alert %. Null leaves it unchanged (same convention as the modes above). */
    public LmtSettings updateReturnAlertPercent(Double returnAlertPercent) {
        LmtSettings settings = getOrCreate();
        if (returnAlertPercent != null) {
            if (returnAlertPercent < 0 || returnAlertPercent > 100) {
                throw new RuntimeException("returnAlertPercent must be between 0 and 100");
            }
            settings.setReturnAlertPercent(returnAlertPercent);
            settings.setUpdatedAt(LocalDateTime.now());
        }
        return lmtSettingsRepository.save(settings);
    }

    private GlobalToggleMode parseMode(String value, String fieldName) {
        try {
            return GlobalToggleMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(fieldName + " must be one of PER_SHOP, FORCE_ON, FORCE_OFF — got: " + value);
        }
    }
}
