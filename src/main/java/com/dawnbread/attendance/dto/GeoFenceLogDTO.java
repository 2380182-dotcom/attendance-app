package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;

/**
 * GeoFenceLog previously serialized its full nested Agent (email, phone,
 * shift config, and — before Agent gained field-level @JsonIgnore — the
 * password hash and raw face embedding) to any ADMIN/HR caller of
 * GET /api/geo-fence/logs. This DTO carries only what a geofence activity
 * log actually needs to display.
 */
public class GeoFenceLogDTO {
    private Long id;
    private Long agentId;
    private String agentDisplayId;
    private String agentName;
    private Long martId;
    private String martName;
    private String action;
    private Double latitude;
    private Double longitude;
    private LocalDateTime createdAt;

    public GeoFenceLogDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getAgentDisplayId() { return agentDisplayId; }
    public void setAgentDisplayId(String agentDisplayId) { this.agentDisplayId = agentDisplayId; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public Long getMartId() { return martId; }
    public void setMartId(Long martId) { this.martId = martId; }

    public String getMartName() { return martName; }
    public void setMartName(String martName) { this.martName = martName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
