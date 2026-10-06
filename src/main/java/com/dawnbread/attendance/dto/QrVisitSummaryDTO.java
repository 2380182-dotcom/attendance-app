package com.dawnbread.attendance.dto;

/** Task 3 summary counts for one date + role: Total Shops / Visited / Not Visited. */
public class QrVisitSummaryDTO {
    private int totalShops;
    private int visitedShops;
    private int notVisitedShops;
    private int voucherWithoutScanCount;
    /** "Salesman updates" feature: how many of visitedShops got there via feedback (no sale, no QR scan) rather than a real scan/voucher. */
    private int feedbackCount;

    public QrVisitSummaryDTO() {}

    public QrVisitSummaryDTO(int totalShops, int visitedShops, int notVisitedShops, int voucherWithoutScanCount, int feedbackCount) {
        this.totalShops = totalShops;
        this.visitedShops = visitedShops;
        this.notVisitedShops = notVisitedShops;
        this.voucherWithoutScanCount = voucherWithoutScanCount;
        this.feedbackCount = feedbackCount;
    }

    public int getTotalShops() { return totalShops; }
    public void setTotalShops(int totalShops) { this.totalShops = totalShops; }

    public int getVisitedShops() { return visitedShops; }
    public void setVisitedShops(int visitedShops) { this.visitedShops = visitedShops; }

    public int getNotVisitedShops() { return notVisitedShops; }
    public void setNotVisitedShops(int notVisitedShops) { this.notVisitedShops = notVisitedShops; }

    public int getVoucherWithoutScanCount() { return voucherWithoutScanCount; }
    public void setVoucherWithoutScanCount(int voucherWithoutScanCount) { this.voucherWithoutScanCount = voucherWithoutScanCount; }

    public int getFeedbackCount() { return feedbackCount; }
    public void setFeedbackCount(int feedbackCount) { this.feedbackCount = feedbackCount; }
}
