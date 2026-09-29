package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;

/** GET/PUT /api/lmt/settings body. geofenceMode/qrMode are "PER_SHOP" | "FORCE_ON" | "FORCE_OFF" — the QR shop-visit flow's tenant-wide master overrides. */
public class LmtSettingsDTO {
    private Double geofenceBufferMeters;
    private String geofenceMode;
    private String qrMode;
    private LocalDateTime updatedAt;

    public LmtSettingsDTO() {}

    public LmtSettingsDTO(Double geofenceBufferMeters, String geofenceMode, String qrMode, LocalDateTime updatedAt) {
        this.geofenceBufferMeters = geofenceBufferMeters;
        this.geofenceMode = geofenceMode;
        this.qrMode = qrMode;
        this.updatedAt = updatedAt;
    }

    public Double getGeofenceBufferMeters() { return geofenceBufferMeters; }
    public void setGeofenceBufferMeters(Double geofenceBufferMeters) { this.geofenceBufferMeters = geofenceBufferMeters; }

    public String getGeofenceMode() { return geofenceMode; }
    public void setGeofenceMode(String geofenceMode) { this.geofenceMode = geofenceMode; }

    public String getQrMode() { return qrMode; }
    public void setQrMode(String qrMode) { this.qrMode = qrMode; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
