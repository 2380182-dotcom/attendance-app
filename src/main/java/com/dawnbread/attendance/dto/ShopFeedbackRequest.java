package com.dawnbread.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** POST /api/lmt/shop-visits/feedback — the SALESMAN_LMT / SALESMAN_LOCAL / ADMIN-only flow ("salesman updates" feature). */
public class ShopFeedbackRequest {

    @NotNull(message = "Agent ID is required")
    private Long agentId;

    @NotBlank(message = "Shop code is required")
    private String shopCode;

    @NotBlank(message = "reason is required (SHOP_CLOSED, STOCK_FULL, OWNER_NOT_AVAILABLE, or OTHER)")
    private String reason;

    /** Required (validated in ShopFeedbackService) when reason = OTHER; optional otherwise — the salesman's own words. */
    private String note;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    /** Idempotency key, same pattern as ShopVisitRequest.requestId — optional so an old app build keeps working. */
    private String requestId;

    public ShopFeedbackRequest() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
