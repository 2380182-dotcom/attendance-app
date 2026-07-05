package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.SaleItem;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.SalesRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates the Sales per-agent sales CSV report.
 *
 * Data-boundary note: this service is deliberately wired to ONLY SalesRecordRepository
 * and AgentRepository. It has no dependency on AttendanceRepository/GeoFenceLogRepository
 * anywhere in this class, so there is no code path — regardless of how the /reports/sales
 * endpoint is called — through which attendance/geofence data could end up in this report.
 * That is the actual enforcement mechanism, not a UI-level filter.
 */
@Service
public class SalesAgentCsvService {

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    @Autowired
    private AgentRepository agentRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public byte[] exportAgentSalesCsv(Long agentId, LocalDate startDate, LocalDate endDate) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));

        List<SalesRecord> records = salesRecordRepository.findByAgentIdAndSaleDateBetween(agentId, startDate, endDate);

        StringBuilder csv = new StringBuilder();
        csv.append(CsvUtil.row(
                "Date", "Time", "Agent ID", "Agent Name", "Mart / Store", "Product", "Quantity",
                "Unit Price (PKR)", "Line Total (PKR)", "Sale Total (PKR)", "Status",
                "Override Reason", "Override By", "Override At"
        ));

        for (SalesRecord record : records) {
            String store = record.getStoreName() != null ? record.getStoreName() : record.getLocation();
            String dateStr = record.getSaleDate() != null ? record.getSaleDate().format(DATE_FORMATTER) : "";
            String timeStr = record.getSaleTime() != null ? record.getSaleTime().toString() : "";
            String overrideReason = record.getOverrideReason() != null ? record.getOverrideReason() : "";
            String overrideBy = record.getModifiedBy() != null ? record.getModifiedBy() : "";
            String overrideAt = record.getModifiedAt() != null ? record.getModifiedAt().toString() : "";

            List<SaleItem> items = record.getItems();
            if (items == null || items.isEmpty()) {
                csv.append(CsvUtil.row(
                        dateStr, timeStr, agent.getAgentId(), agent.getName(), store,
                        "", "", "", "", record.getTotalAmount(), record.getStatus(),
                        overrideReason, overrideBy, overrideAt
                ));
                continue;
            }

            for (SaleItem item : items) {
                csv.append(CsvUtil.row(
                        dateStr,
                        timeStr,
                        agent.getAgentId(),
                        agent.getName(),
                        store,
                        item.getProduct() != null ? item.getProduct().getName() : "",
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getTotalPrice(),
                        record.getTotalAmount(),
                        record.getStatus(),
                        overrideReason,
                        overrideBy,
                        overrideAt
                ));
            }
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
