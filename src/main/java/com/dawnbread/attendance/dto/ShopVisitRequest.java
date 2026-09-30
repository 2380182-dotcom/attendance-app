package com.dawnbread.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** POST /api/sales/shop-visit — the SALESMAN_LMT / ADMIN-only flow. */
public class ShopVisitRequest {

    @NotNull(message = "Agent ID is required")
    private Long agentId;

    @NotBlank(message = "Shop code is required")
    private String shopCode;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    @NotEmpty(message = "Cart cannot be empty")
    @Valid
    private List<ShopVisitItemRequest> items;

    // Idempotency key (Task 2) — optional so old app versions that never
    // send one keep working exactly as before. Generated once per visit on
    // the client and reused on retries (double-tap, network retry).
    private String requestId;

    public ShopVisitRequest() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public List<ShopVisitItemRequest> getItems() { return items; }
    public void setItems(List<ShopVisitItemRequest> items) { this.items = items; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
