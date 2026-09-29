package com.dawnbread.attendance.dto;

import java.time.LocalDate;
import java.util.List;

/** GET /api/lmt/shop-visits/summary — one salesman's visit history for one day, plus the four counts from the spec's example screen. */
public class ShopVisitDaySummaryDTO {
    private Long agentId;
    private LocalDate date;
    private int totalVisits;
    private int successfulVisits;
    private int failedVisits;
    private int uniqueShopsVisited;
    private List<ShopVisitScanRecordDTO> visits;

    public ShopVisitDaySummaryDTO() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public int getTotalVisits() { return totalVisits; }
    public void setTotalVisits(int totalVisits) { this.totalVisits = totalVisits; }

    public int getSuccessfulVisits() { return successfulVisits; }
    public void setSuccessfulVisits(int successfulVisits) { this.successfulVisits = successfulVisits; }

    public int getFailedVisits() { return failedVisits; }
    public void setFailedVisits(int failedVisits) { this.failedVisits = failedVisits; }

    public int getUniqueShopsVisited() { return uniqueShopsVisited; }
    public void setUniqueShopsVisited(int uniqueShopsVisited) { this.uniqueShopsVisited = uniqueShopsVisited; }

    public List<ShopVisitScanRecordDTO> getVisits() { return visits; }
    public void setVisits(List<ShopVisitScanRecordDTO> visits) { this.visits = visits; }
}
