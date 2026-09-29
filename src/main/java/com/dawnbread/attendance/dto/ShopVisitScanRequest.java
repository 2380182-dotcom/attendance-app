package com.dawnbread.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** POST /api/lmt/shop-visits/scan — the SALESMAN_LMT / SALESMAN_LOCAL / ADMIN-only flow. */
public class ShopVisitScanRequest {

    @NotNull(message = "Agent ID is required")
    private Long agentId;

    @NotBlank(message = "Scanned code is required")
    private String scannedCode;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    public ShopVisitScanRequest() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getScannedCode() { return scannedCode; }
    public void setScannedCode(String scannedCode) { this.scannedCode = scannedCode; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
