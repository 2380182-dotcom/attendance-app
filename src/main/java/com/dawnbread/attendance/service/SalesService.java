package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.*;
import com.dawnbread.attendance.entity.*;
import com.dawnbread.attendance.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SalesService {

    private static final Logger logger = LoggerFactory.getLogger(SalesService.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    @Autowired
    private SaleItemRepository saleItemRepository;

    @Autowired
    private SalesSyncLogRepository salesSyncLogRepository;

    @Autowired
    private AgentService agentService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Value("${sales.max-quantity-limit:500}")
    private int maxQuantityLimit;

    /**
     * Enhanced sales entry with validations and real-time sync
     */
    public SalesRecord addSalesWithImages(SalesRequest request) {
        // 1. Validate agent
        Agent agent = agentService.getAgentById(request.getAgentId())
                .orElseThrow(() -> new IllegalArgumentException("Agent not found with ID: " + request.getAgentId()));

        LocalDate today = LocalDate.now();

        // 2. Fetch today's existing sales for duplicate check
        List<SalesRecord> todaySales = salesRecordRepository.findByAgentIdAndSaleDate(agent.getId(), today);
        Set<Long> existingProductIds = todaySales.stream()
                .flatMap(sr -> sr.getItems().stream())
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());

        // 3. Process items and validate rules
        double totalAmount = 0.0;
        List<SaleItem> itemsToSave = new ArrayList<>();

        for (SaleItemRequest itemReq : request.getItems()) {
            // Negative check
            if (itemReq.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero.");
            }

            // Max quantity check
            if (itemReq.getQuantity() > maxQuantityLimit) {
                throw new IllegalArgumentException("Quantity for product ID " + itemReq.getProductId() + 
                        " exceeds maximum allowed limit of " + maxQuantityLimit);
            }

            // Duplicate product check per day
            if (existingProductIds.contains(itemReq.getProductId())) {
                Product p = productRepository.findById(itemReq.getProductId()).orElse(null);
                String pName = p != null ? p.getName() : String.valueOf(itemReq.getProductId());
                throw new IllegalArgumentException("Duplicate entry: Sales for product '" + pName + "' have already been recorded today.");
            }

            // Fetch product
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + itemReq.getProductId()));

            double itemTotal = product.getPrice() * itemReq.getQuantity();
            totalAmount += itemTotal;

            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(product.getPrice());
            item.setTotalPrice(itemTotal);
            item.setProductImageUrl(product.getImageUrl());
            itemsToSave.add(item);
        }

        // 4. Save Sales Header
        SalesRecord record = new SalesRecord();
        record.setAgent(agent);
        record.setTotalAmount(totalAmount);
        record.setSaleDate(today);
        record.setSaleTime(LocalTime.now());
        record.setLocation(request.getLocation() != null ? request.getLocation() : "North Outlet");
        record.setCreatedAt(LocalDateTime.now());

        for (SaleItem item : itemsToSave) {
            record.addItem(item);
        }

        SalesRecord saved = salesRecordRepository.save(record);

        // 5. Trigger Real-time synchronization
        syncToSalesDepartment(saved);
        syncToHRDepartment(saved);

        // 6. WebSocket alert to Sales/HR dashboards
        broadcastSaleUpdate(saved);

        // 7. System notification logging
        createSystemNotification(saved);

        return saved;
    }

    /**
     * Real-time sync to Sales Department
     */
    public void syncToSalesDepartment(SalesRecord record) {
        SalesSyncLog log = new SalesSyncLog();
        log.setSaleRecordId(record.getId());
        log.setSyncedTo("SALES_DEPARTMENT");
        log.setSyncedAt(LocalDateTime.now());
        log.setSyncStatus("SUCCESS");
        log.setSyncMessage("Sales synced in real-time. Agent: " + record.getAgent().getName() + ", Amount: PKR " + record.getTotalAmount());
        salesSyncLogRepository.save(log);
        logger.info("[SYNC-SALES] Saved sync log for sale ID: {}", record.getId());
    }

    /**
     * Real-time sync to HR Department
     */
    public void syncToHRDepartment(SalesRecord record) {
        SalesSyncLog log = new SalesSyncLog();
        log.setSaleRecordId(record.getId());
        log.setSyncedTo("HR_DEPARTMENT");
        log.setSyncedAt(LocalDateTime.now());
        log.setSyncStatus("SUCCESS");
        log.setSyncMessage("Sales sync to HR completed for attendance correlation.");
        salesSyncLogRepository.save(log);
        logger.info("[SYNC-HR] Saved sync log for sale ID: {}", record.getId());
    }

    /**
     * Broadcast WebSocket updates
     */
    private void broadcastSaleUpdate(SalesRecord record) {
        try {
            SalesDTO dto = convertToDTO(record);
            messagingTemplate.convertAndSend("/topic/sales", dto);
            logger.info("[WS-BROADCAST] Broadcasted sale ID {} to /topic/sales", record.getId());
        } catch (Exception e) {
            logger.error("Failed to broadcast WebSocket sales update: ", e);
        }
    }

    /**
     * Create real-time dashboard notifications
     */
    private void createSystemNotification(SalesRecord record) {
        String agentName = record.getAgent().getName();
        String timeStr = record.getSaleTime().format(DateTimeFormatter.ofPattern("hh:mm a"));
        
        // Notification for Sales Department alerts feed
        Notification salesNotif = new Notification();
        salesNotif.setAgent(record.getAgent());
        salesNotif.setAgentName(agentName);
        
        // Format first item as visual summary
        String itemSummary = record.getItems().isEmpty() ? "" : 
            record.getItems().get(0).getProduct().getName() + " (" + record.getItems().get(0).getQuantity() + " units)";
        
        salesNotif.setMessage("🛍️ " + agentName + " sold " + itemSummary + " - Total PKR " + record.getTotalAmount());
        salesNotif.setType("SALE");
        salesNotif.setDepartment("SALES");
        notificationService.saveNotification(salesNotif);
    }

    /**
     * Get agent sales
     */
    public List<SalesDTO> getSalesWithImages(Long agentId) {
        List<SalesRecord> records = salesRecordRepository.findByAgentIdOrderBySaleDateDescSaleTimeDesc(agentId);
        return records.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * Generate daily report DTO
     */
    public ReportDTO generateDailyReport(LocalDate date) {
        List<SalesRecord> records = salesRecordRepository.findBySaleDate(date);
        double totalRevenue = records.stream().mapToDouble(SalesRecord::getTotalAmount).sum();
        int totalUnits = records.stream()
                .flatMap(r -> r.getItems().stream())
                .mapToInt(SaleItem::getQuantity).sum();
        
        Set<Long> uniqueAgents = records.stream().map(r -> r.getAgent().getId()).collect(Collectors.toSet());

        List<ReportDTO.AgentReportSummary> agentSummaries = records.stream()
                .collect(Collectors.groupingBy(SalesRecord::getAgent))
                .entrySet().stream()
                .map(entry -> {
                    Agent agent = entry.getKey();
                    List<SalesRecord> agentRecords = entry.getValue();
                    double agentRevenue = agentRecords.stream().mapToDouble(SalesRecord::getTotalAmount).sum();
                    int agentUnits = agentRecords.stream()
                            .flatMap(r -> r.getItems().stream())
                            .mapToInt(SaleItem::getQuantity).sum();
                    
                    // Flatten items
                    List<SaleItemDTO> itemDTOs = agentRecords.stream()
                            .flatMap(r -> r.getItems().stream())
                            .map(this::convertToItemDTO)
                            .collect(Collectors.toList());

                    return new ReportDTO.AgentReportSummary(agent.getName(), agent.getAgentId(), agentRevenue, agentUnits, itemDTOs);
                }).collect(Collectors.toList());

        // Top products performance list
        List<ReportDTO.ProductPerformanceDetail> productPerformance = getProductPerformanceForRecords(records);

        return new ReportDTO(
                "DAWN BREAD - DAILY SALES REPORT",
                date.toString(),
                LocalDateTime.now(),
                "System Admin",
                totalRevenue,
                totalUnits,
                uniqueAgents.size(),
                agentSummaries,
                productPerformance,
                new ArrayList<>()
        );
    }

    /**
     * Generate Weekly Report (Last 7 Days)
     */
    public ReportDTO generateWeeklyReport(LocalDate date) {
        LocalDate start = date.minusDays(6);
        List<SalesRecord> records = salesRecordRepository.findBySaleDateBetween(start, date);
        double totalRevenue = records.stream().mapToDouble(SalesRecord::getTotalAmount).sum();
        int totalUnits = records.stream().flatMap(r -> r.getItems().stream()).mapToInt(SaleItem::getQuantity).sum();
        Set<Long> uniqueAgents = records.stream().map(r -> r.getAgent().getId()).collect(Collectors.toSet());

        List<ReportDTO.ProductPerformanceDetail> productPerformance = getProductPerformanceForRecords(records);

        return new ReportDTO(
                "DAWN BREAD - WEEKLY SALES SUMMARY",
                start.toString() + " to " + date.toString(),
                LocalDateTime.now(),
                "System Admin",
                totalRevenue,
                totalUnits,
                uniqueAgents.size(),
                new ArrayList<>(),
                productPerformance,
                new ArrayList<>()
        );
    }

    /**
     * Generate Monthly Report
     */
    public ReportDTO generateMonthlyReport(LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = date.withDayOfMonth(date.lengthOfMonth());
        List<SalesRecord> records = salesRecordRepository.findBySaleDateBetween(start, end);
        double totalRevenue = records.stream().mapToDouble(SalesRecord::getTotalAmount).sum();
        int totalUnits = records.stream().flatMap(r -> r.getItems().stream()).mapToInt(SaleItem::getQuantity).sum();
        Set<Long> uniqueAgents = records.stream().map(r -> r.getAgent().getId()).collect(Collectors.toSet());

        List<ReportDTO.ProductPerformanceDetail> productPerformance = getProductPerformanceForRecords(records);

        return new ReportDTO(
                "DAWN BREAD - MONTHLY SALES REPORT",
                start.toString() + " to " + end.toString(),
                LocalDateTime.now(),
                "System Admin",
                totalRevenue,
                totalUnits,
                uniqueAgents.size(),
                new ArrayList<>(),
                productPerformance,
                new ArrayList<>()
        );
    }

    /**
     * Admin override of sales records
     */
    public SalesRecord overrideSalesEntry(Long saleId, SalesRequest request, String reason, String username) {
        SalesRecord record = salesRecordRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sales record not found with ID: " + saleId));

        // Lock check: normal modification locked after 24 hours, but admin overrides with reason bypasses
        if (record.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24)) && (reason == null || reason.trim().isEmpty())) {
            throw new IllegalStateException("Sales entry is locked (older than 24 hours). Admin must provide a reason to override.");
        }

        // Clean out existing items
        record.getItems().clear();
        salesRecordRepository.saveAndFlush(record);

        double totalAmount = 0.0;
        for (SaleItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + itemReq.getProductId()));
            
            double itemTotal = product.getPrice() * itemReq.getQuantity();
            totalAmount += itemTotal;

            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(product.getPrice());
            item.setTotalPrice(itemTotal);
            item.setProductImageUrl(product.getImageUrl());
            record.addItem(item);
        }

        record.setTotalAmount(totalAmount);
        record.setModifiedAt(LocalDateTime.now());
        record.setModifiedBy(username);
        record.setOverrideReason(reason);

        SalesRecord saved = salesRecordRepository.save(record);

        // Sync again
        syncToSalesDepartment(saved);
        syncToHRDepartment(saved);
        broadcastSaleUpdate(saved);

        return saved;
    }

    // Helper: Map list of records to product performance details
    private List<ReportDTO.ProductPerformanceDetail> getProductPerformanceForRecords(List<SalesRecord> records) {
        Map<Product, Integer> qtyMap = new HashMap<>();
        Map<Product, Double> revMap = new HashMap<>();

        for (SalesRecord r : records) {
            for (SaleItem item : r.getItems()) {
                qtyMap.put(item.getProduct(), qtyMap.getOrDefault(item.getProduct(), 0) + item.getQuantity());
                revMap.put(item.getProduct(), revMap.getOrDefault(item.getProduct(), 0.0) + item.getTotalPrice());
            }
        }

        return qtyMap.entrySet().stream()
                .map(entry -> {
                    Product p = entry.getKey();
                    int qty = entry.getValue();
                    double rev = revMap.get(p);
                    return new ReportDTO.ProductPerformanceDetail(p.getName(), qty, rev, p.getImageUrl());
                })
                .sorted((a, b) -> b.getQuantitySold().compareTo(a.getQuantitySold()))
                .collect(Collectors.toList());
    }

    // Mapper DTOs
    public SalesDTO convertToDTO(SalesRecord record) {
        List<SaleItemDTO> itemDTOs = record.getItems().stream()
                .map(this::convertToItemDTO)
                .collect(Collectors.toList());

        return new SalesDTO(
                record.getId(),
                record.getAgent().getId(),
                record.getAgent().getName(),
                record.getAgent().getAgentId(),
                record.getTotalAmount(),
                record.getSaleDate(),
                record.getSaleTime(),
                record.getLocation(),
                itemDTOs,
                record.getModifiedAt(),
                record.getModifiedBy(),
                record.getOverrideReason()
        );
    }

    private SaleItemDTO convertToItemDTO(SaleItem item) {
        return new SaleItemDTO(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getProductImageUrl()
        );
    }
}
