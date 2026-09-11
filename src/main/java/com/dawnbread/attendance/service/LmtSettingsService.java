package com.dawnbread.attendance.service;

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
}
