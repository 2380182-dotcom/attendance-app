package com.dawnbread.attendance.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** POST /api/lmt/shop-visits/feedback response — confirms what was saved, no voucher is ever created. */
public class ShopFeedbackResponseDTO {

    private Long id;
    private String shopCode;
    private String shopName;
    private String reason;
    private String note;
    private Double distanceFromShopMeters;
    private LocalDate visitDate;
    private LocalTime visitTime;

    public ShopFeedbackResponseDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Double getDistanceFromShopMeters() { return distanceFromShopMeters; }
    public void setDistanceFromShopMeters(Double distanceFromShopMeters) { this.distanceFromShopMeters = distanceFromShopMeters; }

    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }

    public LocalTime getVisitTime() { return visitTime; }
    public void setVisitTime(LocalTime visitTime) { this.visitTime = visitTime; }
}
