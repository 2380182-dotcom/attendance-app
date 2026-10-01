package com.dawnbread.attendance.dto;

/**
 * Task 3/4 "Not Visited" tab, one row per salesman-shop assignment — Local
 * and LMT work identically now (assignment is the one mechanism for both).
 * Also reused, with assignedAgentId always null, for the separate
 * "Unassigned — not scanned" list (role = "UNASSIGNED").
 */
public class NotVisitedShopDTO {
    private Long shopId;
    private String shopCode;
    private String shopName;
    private String areaName;
    private String city;
    /** Null only for a row on the "Unassigned — not scanned" list. */
    private Long assignedAgentId;
    private String assignedAgentName;
    /** "LMT", "LOCAL", or "UNASSIGNED" — which report/bucket this row belongs to. */
    private String role;

    public NotVisitedShopDTO() {}

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Long getAssignedAgentId() { return assignedAgentId; }
    public void setAssignedAgentId(Long assignedAgentId) { this.assignedAgentId = assignedAgentId; }

    public String getAssignedAgentName() { return assignedAgentName; }
    public void setAssignedAgentName(String assignedAgentName) { this.assignedAgentName = assignedAgentName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
