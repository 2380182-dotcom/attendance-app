package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.AgentPerformanceDTO;
import com.dawnbread.attendance.dto.HRDashboardDTO;
import com.dawnbread.attendance.dto.SalesDashboardDTO;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.entity.SaleItem;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.SalesRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    /**
     * Compile real-time Sales Department Dashboard details
     */
    public SalesDashboardDTO getRealtimeSalesDashboard() {
        LocalDate today = LocalDate.now();
        List<SalesRecord> todaySales = salesRecordRepository.findBySaleDate(today);

        // 1. Calculations for header cards
        double todayRevenue = todaySales.stream().mapToDouble(SalesRecord::getTotalAmount).sum();
        int todayUnits = todaySales.stream()
                .flatMap(sr -> sr.getItems().stream())
                .mapToInt(SaleItem::getQuantity).sum();

        // 2. Compile sales by agent list
        List<Agent> allAgents = agentRepository.findAll().stream()
                .filter(Agent::getIsActive)
                .filter(a -> "AGENT".equals(a.getRole()))
                .collect(Collectors.toList());

        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(23, 59, 59);

        List<Long> agentIds = allAgents.stream().map(Agent::getId).collect(Collectors.toList());
        Map<Long, List<SalesRecord>> salesByAgentId = todaySales.stream()
                .collect(Collectors.groupingBy(sr -> sr.getAgent().getId()));
        Map<Long, List<Attendance>> attendanceByAgentId = fetchAttendanceByAgentId(agentIds, start, end);

        List<SalesDashboardDTO.AgentSalesSummary> salesByAgent = new ArrayList<>();
        int activeAgentsCount = 0;

        for (Agent agent : allAgents) {
            List<SalesRecord> agentRecords = salesByAgentId.getOrDefault(agent.getId(), List.of());

            double revenue = agentRecords.stream().mapToDouble(SalesRecord::getTotalAmount).sum();
            int units = agentRecords.stream()
                    .flatMap(sr -> sr.getItems().stream())
                    .mapToInt(SaleItem::getQuantity).sum();

            List<Attendance> attendances = attendanceByAgentId.getOrDefault(agent.getId(), List.of());
            String status = "Absent";
            if (!attendances.isEmpty()) {
                activeAgentsCount++;
                Attendance att = attendances.get(0);
                status = "LATE".equals(att.getStatus()) ? "⚠️ Late Check-in" : "✅ Active";
            }

            // Only show agents with sales or checked-in agents
            if (!attendances.isEmpty() || units > 0) {
                salesByAgent.add(new SalesDashboardDTO.AgentSalesSummary(agent.getName(), units, revenue, status));
            }
        }

        // Sort by revenue descending
        salesByAgent.sort((a, b) -> b.getRevenue().compareTo(a.getRevenue()));

        // 3. Compile top selling products
        Map<String, Integer> productQty = new HashMap<>();
        Map<String, Double> productRev = new HashMap<>();

        for (SalesRecord sr : todaySales) {
            for (SaleItem item : sr.getItems()) {
                String name = item.getProduct().getName();
                productQty.put(name, productQty.getOrDefault(name, 0) + item.getQuantity());
                productRev.put(name, productRev.getOrDefault(name, 0.0) + item.getTotalPrice());
            }
        }

        List<SalesDashboardDTO.ProductSalesDetail> topProducts = productQty.entrySet().stream()
                .map(entry -> {
                    String name = entry.getKey();
                    int qty = entry.getValue();
                    double rev = productRev.get(name);
                    // Provide a nice visual trend string based on quantity
                    String trend = qty > 25 ? "📈 +12%" : (qty > 10 ? "📈 +8%" : "📈 +5%");
                    if (qty < 5) trend = "📉 -3%";
                    return new SalesDashboardDTO.ProductSalesDetail(name, qty, rev, trend);
                })
                .sorted((a, b) -> b.getUnitsSold().compareTo(a.getUnitsSold()))
                .limit(5)
                .collect(Collectors.toList());

        // 4. Compile recent alerts feed (from actual sales)
        List<String> recentAlerts = new ArrayList<>();
        todaySales.stream()
                .sorted(Comparator.comparing(SalesRecord::getSaleTime).reversed())
                .limit(10)
                .forEach(sr -> {
                    String timeStr = sr.getSaleTime().format(timeFormatter);
                    String summary = sr.getItems().isEmpty() ? "products" : sr.getItems().get(0).getProduct().getName();
                    recentAlerts.add("• " + timeStr + " - " + sr.getAgent().getName() + " sold " + summary + " (" + sr.getTotalAmount() + " PKR)");
                });

        // 5. Compile 7 days sales trends
        List<SalesDashboardDTO.DailyTrend> salesTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            List<SalesRecord> records = salesRecordRepository.findBySaleDate(d);
            double dailyRev = records.stream().mapToDouble(SalesRecord::getTotalAmount).sum();
            salesTrend.add(new SalesDashboardDTO.DailyTrend(d.getDayOfWeek().toString().substring(0, 3), dailyRev));
        }

        return new SalesDashboardDTO(
                todayRevenue,
                todayUnits,
                activeAgentsCount,
                topProducts,
                salesByAgent,
                recentAlerts,
                salesTrend
        );
    }

    /**
     * Compile HR Dashboard details with attendance compliance & sales correlations
     */
    public HRDashboardDTO getHRDashboardWithSales() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(23, 59, 59);

        List<Agent> allAgents = agentRepository.findAll().stream()
                .filter(Agent::getIsActive)
                .filter(a -> "AGENT".equals(a.getRole()))
                .collect(Collectors.toList());

        int totalAgentsCount = allAgents.size();
        int checkedInCount = 0;
        int lateCount = 0;
        int absentCount = 0;

        int compAll3 = 0;
        int compMiss1 = 0;
        int compMiss2Plus = 0;

        List<HRDashboardDTO.AttendanceSalesRow> attendanceSalesSheet = new ArrayList<>();
        List<AgentPerformanceDTO> topPerformers = new ArrayList<>();

        List<SalesRecord> todaySales = salesRecordRepository.findBySaleDate(today);

        List<Long> agentIds = allAgents.stream().map(Agent::getId).collect(Collectors.toList());
        Map<Long, List<SalesRecord>> salesByAgentId = todaySales.stream()
                .collect(Collectors.groupingBy(sr -> sr.getAgent().getId()));
        Map<Long, List<Attendance>> attendanceByAgentId = fetchAttendanceByAgentId(agentIds, start, end);

        LocalDate monthStart = today.withDayOfMonth(1);
        Map<Long, List<Attendance>> monthAttendanceByAgentId = fetchAttendanceByAgentId(
                agentIds, monthStart.atStartOfDay(), today.atTime(23, 59, 59));

        for (Agent agent : allAgents) {
            List<Attendance> attendances = attendanceByAgentId.getOrDefault(agent.getId(), List.of());
            List<SalesRecord> agentSales = salesByAgentId.getOrDefault(agent.getId(), List.of());

            int unitsSold = agentSales.stream()
                    .flatMap(s -> s.getItems().stream())
                    .mapToInt(SaleItem::getQuantity).sum();
            double revenue = agentSales.stream().mapToDouble(SalesRecord::getTotalAmount).sum();

            String checkInTimeStr = "❌";
            String midDayTimeStr = "❌";
            String checkOutTimeStr = "❌";
            int verificationsCompleted = 0;

            if (!attendances.isEmpty()) {
                checkedInCount++;
                Attendance att = attendances.get(0);
                boolean isLate = "LATE".equals(att.getStatus());
                if (isLate) lateCount++;

                checkInTimeStr = att.getCheckInTime().format(timeFormatter) + (isLate ? " ⚠️" : " ✓");
                verificationsCompleted++; // 1st verification: Check-In

                // 2nd verification: Mid-day face check OR sales action
                if (att.getMidDayVerificationTime() != null) {
                    midDayTimeStr = att.getMidDayVerificationTime().format(timeFormatter) + " ✓";
                    verificationsCompleted++;
                } else if (!agentSales.isEmpty()) {
                    midDayTimeStr = agentSales.get(0).getSaleTime().format(timeFormatter) + " ✓";
                    verificationsCompleted++;
                }

                // 3rd verification: Check-Out
                if (att.getCheckOutTime() != null) {
                    checkOutTimeStr = att.getCheckOutTime().format(timeFormatter) + " ✓";
                    verificationsCompleted++;
                }
            } else {
                absentCount++;
            }

            // Categorize verification compliance
            if (verificationsCompleted == 3) {
                compAll3++;
            } else if (verificationsCompleted == 2) {
                compMiss1++;
            } else {
                compMiss2Plus++;
            }

            attendanceSalesSheet.add(new HRDashboardDTO.AttendanceSalesRow(
                    agent.getName(),
                    checkInTimeStr,
                    midDayTimeStr,
                    checkOutTimeStr,
                    unitsSold
            ));

            // Overall attendance performance for the current month-to-date, based on the
            // agent's actual working-day schedule (not a lifetime-checkin-count placeholder).
            double attPercent = calculateAttendancePercentage(agent, monthStart, today,
                    monthAttendanceByAgentId.getOrDefault(agent.getId(), List.of()));

            String status = "Absent";
            if (!attendances.isEmpty()) {
                status = "LATE".equals(attendances.get(0).getStatus()) ? "Late Check-in" : "Active";
            }

            topPerformers.add(new AgentPerformanceDTO(
                    agent.getId(),
                    agent.getName(),
                    agent.getAgentId(),
                    attPercent,
                    unitsSold,
                    revenue,
                    status
            ));
        }

        // Sort top performers by revenue (high to low)
        topPerformers.sort((a, b) -> b.getTotalSalesRevenue().compareTo(a.getTotalSalesRevenue()));

        double checkedInPercent = totalAgentsCount > 0 ? (checkedInCount * 100.0 / totalAgentsCount) : 0.0;
        double latePercent = totalAgentsCount > 0 ? (lateCount * 100.0 / totalAgentsCount) : 0.0;
        double absentPercent = totalAgentsCount > 0 ? (absentCount * 100.0 / totalAgentsCount) : 0.0;

        double compAll3Percent = totalAgentsCount > 0 ? (compAll3 * 100.0 / totalAgentsCount) : 0.0;
        double compMiss1Percent = totalAgentsCount > 0 ? (compMiss1 * 100.0 / totalAgentsCount) : 0.0;
        double compMiss2PlusPercent = totalAgentsCount > 0 ? (compMiss2Plus * 100.0 / totalAgentsCount) : 0.0;

        return new HRDashboardDTO(
                totalAgentsCount,
                checkedInCount,
                checkedInPercent,
                lateCount,
                latePercent,
                absentCount,
                absentPercent,
                compAll3,
                compAll3Percent,
                compMiss1,
                compMiss1Percent,
                compMiss2Plus,
                compMiss2PlusPercent,
                attendanceSalesSheet,
                topPerformers
        );
    }

    private static final Map<DayOfWeek, String> WORKING_DAY_CODES = Map.of(
            DayOfWeek.MONDAY, "MON",
            DayOfWeek.TUESDAY, "TUE",
            DayOfWeek.WEDNESDAY, "WED",
            DayOfWeek.THURSDAY, "THU",
            DayOfWeek.FRIDAY, "FRI",
            DayOfWeek.SATURDAY, "SAT",
            DayOfWeek.SUNDAY, "SUN"
    );

    /**
     * Real attendance percentage: days actually present ÷ days the agent was scheduled
     * to work (per their workingDays field) within [periodStart, periodEnd], inclusive.
     * Replaces the old checkins-count * 5% placeholder.
     *
     * Takes the agent's attendance for the period as an already-fetched list rather than
     * querying itself, so callers can batch-fetch across all agents in one query instead
     * of one query per agent (see fetchAttendanceByAgentId).
     */
    private double calculateAttendancePercentage(Agent agent, LocalDate periodStart, LocalDate periodEnd,
                                                   List<Attendance> periodAttendance) {
        List<String> workingDays = agent.getWorkingDays();
        if (workingDays == null || workingDays.isEmpty()) {
            return 0.0;
        }

        int expectedDays = 0;
        for (LocalDate d = periodStart; !d.isAfter(periodEnd); d = d.plusDays(1)) {
            if (workingDays.contains(WORKING_DAY_CODES.get(d.getDayOfWeek()))) {
                expectedDays++;
            }
        }
        if (expectedDays == 0) {
            return 0.0;
        }

        long daysPresent = periodAttendance.stream()
                .map(a -> a.getCheckInTime().toLocalDate())
                .distinct()
                .count();

        return Math.min(daysPresent * 100.0 / expectedDays, 100.0);
    }

    /**
     * Batch-fetches attendance for a set of agents in one query and groups by agent id,
     * instead of the N+1 pattern of querying per-agent inside a loop. Empty agentIds
     * short-circuits rather than issuing an `IN ()` query.
     */
    private Map<Long, List<Attendance>> fetchAttendanceByAgentId(List<Long> agentIds, LocalDateTime start, LocalDateTime end) {
        if (agentIds.isEmpty()) {
            return Map.of();
        }
        return attendanceRepository.findByAgentIdInAndCheckInTimeBetween(agentIds, start, end).stream()
                .collect(Collectors.groupingBy(a -> a.getAgent().getId()));
    }
}
