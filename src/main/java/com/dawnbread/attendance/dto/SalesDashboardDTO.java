package com.dawnbread.attendance.dto;

import java.util.List;

public class SalesDashboardDTO {
    private Double todayTotalRevenue;
    private Integer todayTotalUnits;
    private Integer activeAgentsCount;
    private List<ProductSalesDetail> topSellingProducts;
    private List<AgentSalesSummary> salesByAgent;
    private List<String> recentAlerts;
    private List<DailyTrend> salesTrend;

    public SalesDashboardDTO() {}

    public SalesDashboardDTO(Double todayTotalRevenue, Integer todayTotalUnits, Integer activeAgentsCount, List<ProductSalesDetail> topSellingProducts, List<AgentSalesSummary> salesByAgent, List<String> recentAlerts, List<DailyTrend> salesTrend) {
        this.todayTotalRevenue = todayTotalRevenue;
        this.todayTotalUnits = todayTotalUnits;
        this.activeAgentsCount = activeAgentsCount;
        this.topSellingProducts = topSellingProducts;
        this.salesByAgent = salesByAgent;
        this.recentAlerts = recentAlerts;
        this.salesTrend = salesTrend;
    }

    // Getters and Setters
    public Double getTodayTotalRevenue() { return todayTotalRevenue; }
    public void setTodayTotalRevenue(Double todayTotalRevenue) { this.todayTotalRevenue = todayTotalRevenue; }

    public Integer getTodayTotalUnits() { return todayTotalUnits; }
    public void setTodayTotalUnits(Integer todayTotalUnits) { this.todayTotalUnits = todayTotalUnits; }

    public Integer getActiveAgentsCount() { return activeAgentsCount; }
    public void setActiveAgentsCount(Integer activeAgentsCount) { this.activeAgentsCount = activeAgentsCount; }

    public List<ProductSalesDetail> getTopSellingProducts() { return topSellingProducts; }
    public void setTopSellingProducts(List<ProductSalesDetail> topSellingProducts) { this.topSellingProducts = topSellingProducts; }

    public List<AgentSalesSummary> getSalesByAgent() { return salesByAgent; }
    public void setSalesByAgent(List<AgentSalesSummary> salesByAgent) { this.salesByAgent = salesByAgent; }

    public List<String> getRecentAlerts() { return recentAlerts; }
    public void setRecentAlerts(List<String> recentAlerts) { this.recentAlerts = recentAlerts; }

    public List<DailyTrend> getSalesTrend() { return salesTrend; }
    public void setSalesTrend(List<DailyTrend> salesTrend) { this.salesTrend = salesTrend; }

    // Inner Classes
    public static class ProductSalesDetail {
        private String productName;
        private Integer unitsSold;
        private Double revenue;
        private String trend;

        public ProductSalesDetail() {}

        public ProductSalesDetail(String productName, Integer unitsSold, Double revenue, String trend) {
            this.productName = productName;
            this.unitsSold = unitsSold;
            this.revenue = revenue;
            this.trend = trend;
        }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public Integer getUnitsSold() { return unitsSold; }
        public void setUnitsSold(Integer unitsSold) { this.unitsSold = unitsSold; }

        public Double getRevenue() { return revenue; }
        public void setRevenue(Double revenue) { this.revenue = revenue; }

        public String getTrend() { return trend; }
        public void setTrend(String trend) { this.trend = trend; }
    }

    public static class AgentSalesSummary {
        private String agentName;
        private Integer unitsSold;
        private Double revenue;
        private String status;

        public AgentSalesSummary() {}

        public AgentSalesSummary(String agentName, Integer unitsSold, Double revenue, String status) {
            this.agentName = agentName;
            this.unitsSold = unitsSold;
            this.revenue = revenue;
            this.status = status;
        }

        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }

        public Integer getUnitsSold() { return unitsSold; }
        public void setUnitsSold(Integer unitsSold) { this.unitsSold = unitsSold; }

        public Double getRevenue() { return revenue; }
        public void setRevenue(Double revenue) { this.revenue = revenue; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class DailyTrend {
        private String date;
        private Double revenue;

        public DailyTrend() {}

        public DailyTrend(String date, Double revenue) {
            this.date = date;
            this.revenue = revenue;
        }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public Double getRevenue() { return revenue; }
        public void setRevenue(Double revenue) { this.revenue = revenue; }
    }
}
