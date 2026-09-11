package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;

/** GET/PUT /api/lmt/settings body — the only field that matters is the buffer. */
public class LmtSettingsDTO {
    private Double geofenceBufferMeters;
    private LocalDateTime updatedAt;

    public LmtSettingsDTO() {}

    public LmtSettingsDTO(Double geofenceBufferMeters, LocalDateTime updatedAt) {
        this.geofenceBufferMeters = geofenceBufferMeters;
        this.updatedAt = updatedAt;
    }

    public Double getGeofenceBufferMeters() { return geofenceBufferMeters; }
    public void setGeofenceBufferMeters(Double geofenceBufferMeters) { this.geofenceBufferMeters = geofenceBufferMeters; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
