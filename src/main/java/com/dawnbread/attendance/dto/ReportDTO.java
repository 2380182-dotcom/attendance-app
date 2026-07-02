package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ReportDTO {
    private String title;
    private String dateRange;
    private LocalDateTime generatedAt;
    private String generatedBy;
    private Double totalRevenue;
    private Integer totalUnits;
    private Integer activeAgents;
    private List<AgentReportSummary> agentSummaries;
    private List<ProductPerformanceDetail> productPerformance;
    private List<AgentPerformanceDTO> agentRankings;

    public ReportDTO() {}

    public ReportDTO(String title, String dateRange, LocalDateTime generatedAt, String generatedBy, Double totalRevenue, Integer totalUnits, Integer activeAgents, List<AgentReportSummary> agentSummaries, List<ProductPerformanceDetail> productPerformance, List<AgentPerformanceDTO> agentRankings) {
        this.title = title;
        this.dateRange = dateRange;
        this.generatedAt = generatedAt;
        this.generatedBy = generatedBy;
        this.totalRevenue = totalRevenue;
        this.totalUnits = totalUnits;
        this.activeAgents = activeAgents;
        this.agentSummaries = agentSummaries;
        this.productPerformance = productPerformance;
        this.agentRankings = agentRankings;
    }

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDateRange() { return dateRange; }
    public void setDateRange(String dateRange) { this.dateRange = dateRange; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }

    public Double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; }

    public Integer getTotalUnits() { return totalUnits; }
    public void setTotalUnits(Integer totalUnits) { this.totalUnits = totalUnits; }

    public Integer getActiveAgents() { return activeAgents; }
    public void setActiveAgents(Integer activeAgents) { this.activeAgents = activeAgents; }

    public List<AgentReportSummary> getAgentSummaries() { return agentSummaries; }
    public void setAgentSummaries(List<AgentReportSummary> agentSummaries) { this.agentSummaries = agentSummaries; }

    public List<ProductPerformanceDetail> getProductPerformance() { return productPerformance; }
    public void setProductPerformance(List<ProductPerformanceDetail> productPerformance) { this.productPerformance = productPerformance; }

    public List<AgentPerformanceDTO> getAgentRankings() { return agentRankings; }
    public void setAgentRankings(List<AgentPerformanceDTO> agentRankings) { this.agentRankings = agentRankings; }

    // Inner classes
    public static class AgentReportSummary {
        private String agentName;
        private String employeeId;
        private Double totalRevenue;
        private Integer totalUnits;
        private List<SaleItemDTO> items;

        public AgentReportSummary() {}

        public AgentReportSummary(String agentName, String employeeId, Double totalRevenue, Integer totalUnits, List<SaleItemDTO> items) {
            this.agentName = agentName;
            this.employeeId = employeeId;
            this.totalRevenue = totalRevenue;
            this.totalUnits = totalUnits;
            this.items = items;
        }

        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public Double getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; }

        public Integer getTotalUnits() { return totalUnits; }
        public void setTotalUnits(Integer totalUnits) { this.totalUnits = totalUnits; }

        public List<SaleItemDTO> getItems() { return items; }
        public void setItems(List<SaleItemDTO> items) { this.items = items; }
    }

    public static class ProductPerformanceDetail {
        private String productName;
        private Integer quantitySold;
        private Double totalRevenue;
        private String productImageUrl;

        public ProductPerformanceDetail() {}

        public ProductPerformanceDetail(String productName, Integer quantitySold, Double totalRevenue, String productImageUrl) {
            this.productName = productName;
            this.quantitySold = quantitySold;
            this.totalRevenue = totalRevenue;
            this.productImageUrl = productImageUrl;
        }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public Integer getQuantitySold() { return quantitySold; }
        public void setQuantitySold(Integer quantitySold) { this.quantitySold = quantitySold; }

        public Double getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; }

        public String getProductImageUrl() { return productImageUrl; }
        public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }
    }
}
