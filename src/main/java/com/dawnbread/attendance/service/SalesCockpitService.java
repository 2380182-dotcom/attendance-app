package com.dawnbread.attendance.service;

import com.dawnbread.attendance.config.CacheConfig;
import com.dawnbread.attendance.dto.cockpit.CockpitFilter;
import com.dawnbread.attendance.dto.cockpit.SalesCockpitDTOs.*;
import com.dawnbread.attendance.dto.PageResponse;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.repository.SalesCockpitQueries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.dawnbread.attendance.repository.SalesCockpitQueries.byDate;
import static com.dawnbread.attendance.repository.SalesCockpitQueries.lng;
import static com.dawnbread.attendance.repository.SalesCockpitQueries.num;

/**
 * Sales cockpit — one method per page section. All counting happens in
 * SalesCockpitQueries (database aggregates). This class only arranges
 * already-aggregated rows: fills missing days with 0, works out "vs
 * previous period" percentages and sorts alerts.
 *
 * Every public method is cached for 90 seconds per tenant + filter (see
 * CacheConfig); the tenantId is the first part of every key so one
 * company can never be served another's cached numbers.
 *
 * "Today" is always the Pakistan date from the injected Asia/Karachi Clock.
 */
@Service
@Transactional(readOnly = true)
public class SalesCockpitService {

    static final int SPARK_DAYS = 14;
    static final LocalTime ZERO_VISIT_CHECK_TIME = LocalTime.NOON;
    static final int NOT_VISITED_DAYS = 3;
    static final int ALERT_ITEMS = 10;

    private static final String KEY_PREFIX = "T(com.dawnbread.attendance.security.TenantContext).getTenantId() + ':";

    @Autowired
    private SalesCockpitQueries queries;

    @Autowired
    private LmtSettingsService lmtSettingsService;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    // ==================================================================
    // 1. Highlights
    // ==================================================================

    @Cacheable(cacheNames = CacheConfig.SALES_COCKPIT, key = KEY_PREFIX + "highlights:' + #f.cacheKey()")
    public Highlights highlights(CockpitFilter f) {
        LocalDate today = today();
        LocalDate prevStart = f.startDate().minusDays(f.days());
        LocalDate prevEnd = f.startDate().minusDays(1);
        LocalDate sparkStart = f.endDate().minusDays(SPARK_DAYS - 1);
        LocalDate from = min(prevStart, sparkStart);

        List<HighlightCard> cards = new ArrayList<>();

        // --- money (one daily query covers the period, the previous period and the sparkline)
        List<Object[]> sales = queries.dailySales(f, from, f.endDate());
        Map<LocalDate, Double> saleByDay = byDate(sales, 1);
        Map<LocalDate, Double> returnByDay = byDate(sales, 2);
        Map<LocalDate, Double> rbeByDay = byDate(sales, 5);
        Map<LocalDate, Double> netByDay = new java.util.HashMap<>();
        saleByDay.forEach((d, v) -> netByDay.merge(d, v, Double::sum));
        returnByDay.forEach((d, v) -> netByDay.merge(d, -v, Double::sum));

        double net = sum(netByDay, f.startDate(), f.endDate());
        double netPrev = sum(netByDay, prevStart, prevEnd);
        cards.add(new HighlightCard("net_sales", "Net sales", net, netPrev,
                "Sales minus returns · previous " + f.days() + " days Rs " + money(netPrev),
                spark(netByDay, sparkStart, f.endDate()), pct(net, netPrev), "vs previous period",
                true, "sales", "sales"));

        double sale = sum(saleByDay, f.startDate(), f.endDate());
        double ret = sum(returnByDay, f.startDate(), f.endDate());
        double retPrev = sum(returnByDay, prevStart, prevEnd);
        double returnPct = sale > 0 ? ret * 100.0 / sale : 0.0;
        cards.add(new HighlightCard("sale_return", "Sales & returns", sale, ret,
                "Returns Rs " + money(ret) + " · " + String.format(Locale.ROOT, "%.1f", returnPct) + "% of sales",
                spark(returnByDay, sparkStart, f.endDate()), pct(ret, retPrev), "returns vs previous period",
                false, "returns", "returns"));

        double rbe = sum(rbeByDay, f.startDate(), f.endDate());
        double rbePrev = sum(rbeByDay, prevStart, prevEnd);
        cards.add(new HighlightCard("rbe", "Return before expiry", rbe, null,
                "Units replaced with fresh stock · does not change totals",
                spark(rbeByDay, sparkStart, f.endDate()), pct(rbe, rbePrev), "vs previous period",
                false, "rbe", "returns"));

        // --- today's coverage (same "visited" rule as QR Scanned Shops)
        LocalDate todaySparkStart = today.minusDays(SPARK_DAYS - 1);
        long assigned = queries.countAssignedShops(f);
        List<DayValue> visitedSpark = new ArrayList<>();
        List<DayValue> notVisitedSpark = new ArrayList<>();
        for (LocalDate d = todaySparkStart; !d.isAfter(today); d = d.plusDays(1)) {
            long v = queries.countVisitedAssignedShops(f, d);
            visitedSpark.add(new DayValue(d, v));
            notVisitedSpark.add(new DayValue(d, Math.max(assigned - v, 0)));
        }
        double visitedToday = last(visitedSpark);
        double visitedLastWeek = weekAgo(visitedSpark);
        double coverage = assigned > 0 ? visitedToday * 100.0 / assigned : 0.0;
        cards.add(new HighlightCard("visits_today", "Shop visits today", visitedToday, (double) assigned,
                "of " + assigned + " assigned shops · " + String.format(Locale.ROOT, "%.0f", coverage) + "% coverage",
                visitedSpark, pct(visitedToday, visitedLastWeek), "vs same day last week",
                true, "ok", "coverage"));
        double notVisitedToday = last(notVisitedSpark);
        cards.add(new HighlightCard("not_visited_today", "Not visited today", notVisitedToday, null,
                assigned == 0 ? "No shops are assigned to salesmen yet" : "Assigned shops with no scan or feedback yet",
                notVisitedSpark, pct(notVisitedToday, weekAgo(notVisitedSpark)), "vs same day last week",
                false, "alert", "coverage"));

        // --- location / scan flags (period)
        Map<LocalDate, Double> offLoc = byDate(queries.dailyOffLocationAttempts(f, from, f.endDate()), 1);
        Map<LocalDate, Double> notChecked = byDate(queries.dailyLocationNotChecked(f, from, f.endDate()), 1);
        double off = sum(offLoc, f.startDate(), f.endDate());
        double nc = sum(notChecked, f.startDate(), f.endDate());
        cards.add(new HighlightCard("off_location", "Off-location attempts", off, nc,
                "Failed scans outside the shop area · " + (long) nc + " vouchers location not checked",
                spark(offLoc, sparkStart, f.endDate()), pct(off, sum(offLoc, prevStart, prevEnd)),
                "vs previous period", false, "alert", "coverage"));

        Map<LocalDate, Double> noScan = byDate(queries.dailyVouchersWithoutScan(f, from, f.endDate()), 1);
        double ns = sum(noScan, f.startDate(), f.endDate());
        cards.add(new HighlightCard("without_scan", "Vouchers without QR scan", ns, null,
                "Voucher saved but no successful scan at that shop that day",
                spark(noScan, sparkStart, f.endDate()), pct(ns, sum(noScan, prevStart, prevEnd)),
                "vs previous period", false, "alert", "coverage"));

        // --- feedback today
        Map<LocalDate, Double> feedback = byDate(queries.dailyFeedback(f, todaySparkStart, today), 1);
        List<DayValue> feedbackSpark = spark(feedback, todaySparkStart, today);
        double feedbackToday = last(feedbackSpark);
        cards.add(new HighlightCard("feedback_today", "Shop feedback today", feedbackToday, null,
                "Visits where no sale was made (shop closed, stock full …)",
                feedbackSpark, pct(feedbackToday, weekAgo(feedbackSpark)), "vs same day last week",
                null, "neutral", "coverage"));

        // --- LMT missing stock (period; LMT only, so 0 under the Local segment)
        boolean lmtVisible = !"LOCAL".equals(f.segment());
        Map<LocalDate, Double> missing = lmtVisible
                ? byDate(queries.dailyLmtMissing(f, from, f.endDate()), 1) : Map.of();
        double miss = sum(missing, f.startDate(), f.endDate());
        long openDays = lmtVisible ? queries.unreconciledByAgent(f, f.startDate(), min(today, f.endDate().plusDays(1)))
                .stream().mapToLong(r -> lng(r[1])).sum() : 0;
        cards.add(new HighlightCard("lmt_missing", "LMT missing stock", miss, (double) openDays,
                lmtVisible ? "Units with no recorded outcome · " + openDays + " stock days not reconciled"
                        : "Not applicable to Local salesmen",
                spark(missing, sparkStart, f.endDate()), pct(miss, sum(missing, prevStart, prevEnd)),
                "vs previous period", false, "alert", "lmt"));

        // --- active salesmen today
        long totalSalesmen = queries.countSalesmen(f);
        List<DayValue> activeSpark = new ArrayList<>();
        for (LocalDate d = todaySparkStart; !d.isAfter(today); d = d.plusDays(1)) {
            activeSpark.add(new DayValue(d, queries.countActiveSalesmen(f, d)));
        }
        double activeToday = last(activeSpark);
        cards.add(new HighlightCard("active_salesmen", "Active salesmen today", activeToday, (double) totalSalesmen,
                "with at least 1 visit · of " + totalSalesmen + " salesmen",
                activeSpark, pct(activeToday, weekAgo(activeSpark)), "vs same day last week",
                true, "ok", "salesmen"));

        return new Highlights(LocalDateTime.now(clock), today, cards);
    }

    // ==================================================================
    // 2. Needs attention
    // ==================================================================

    @Cacheable(cacheNames = CacheConfig.SALES_COCKPIT, key = KEY_PREFIX + "attention:' + #f.cacheKey()")
    public Attention attention(CockpitFilter f) {
        LocalDate today = today();
        LocalDateTime now = LocalDateTime.now(clock);
        double threshold = lmtSettingsService.getReturnAlertPercent();
        List<Alert> alerts = new ArrayList<>();

        // Salesmen with 0 visits by 12:00 PM (working days only; checked from 12:00 Pakistan time)
        if (!now.toLocalTime().isBefore(ZERO_VISIT_CHECK_TIME)) {
            String dow = today.getDayOfWeek().name().substring(0, 3);
            List<AlertItem> idle = new ArrayList<>();
            for (Agent a : queries.salesmenWithoutVisit(f, today)) {
                List<String> days = a.getWorkingDays();
                if (days == null || days.contains(dow)) {
                    idle.add(new AlertItem(a.getName(), roleLabel(a.getRole())));
                }
            }
            if (!idle.isEmpty()) {
                alerts.add(new Alert("zero_visits", "CRITICAL", count(idle.size(), "salesman", "salesmen") + " with no visit by 12:00 PM",
                        "No QR scan, feedback or voucher yet today", idle.size(), "salesmen", head(idle)));
            }
        }

        // Shops not visited for 3+ days
        LocalDate cutoff = today.minusDays(NOT_VISITED_DAYS);
        long stale = queries.countShopsNotVisitedSince(f, cutoff);
        if (stale > 0) {
            List<AlertItem> items = new ArrayList<>();
            for (Object[] r : queries.shopsNotVisitedSince(f, cutoff, ALERT_ITEMS)) {
                LocalDate last = latest((LocalDate) r[2], (LocalDate) r[3]);
                items.add(new AlertItem(r[0] + " · " + r[1],
                        last == null ? "never visited" : "last visit " + last));
            }
            alerts.add(new Alert("not_visited_3d", "WARNING", count(stale, "shop", "shops") + " not visited for " + NOT_VISITED_DAYS
                    + "+ days", "Assigned shops with no scan or feedback by their salesman since " + cutoff,
                    (int) stale, "coverage", items));
        }

        // Off-location attempts today
        List<AlertItem> off = agentCounts(queries.offLocationAttemptsByAgent(f, today, today), "attempts");
        if (!off.isEmpty()) {
            int total = off.stream().mapToInt(i -> Integer.parseInt(i.value().split(" ")[0])).sum();
            alerts.add(new Alert("off_location_today", "WARNING", count(total, "off-location scan attempt", "off-location scan attempts") + " today",
                    "QR scans refused because the salesman was outside the shop area", total, "coverage", head(off)));
        }

        // Vouchers without scan today
        List<AlertItem> noScan = agentCounts(queries.vouchersWithoutScanByAgent(f, today, today), "vouchers");
        if (!noScan.isEmpty()) {
            int total = noScan.stream().mapToInt(i -> Integer.parseInt(i.value().split(" ")[0])).sum();
            alerts.add(new Alert("without_scan_today", "WARNING", count(total, "voucher", "vouchers") + " without QR scan today",
                    "Voucher saved but no successful scan at that shop today", total, "coverage", head(noScan)));
        }

        // LMT missing stock / not reconciled (in the selected period)
        if (!"LOCAL".equals(f.segment())) {
            List<Object[]> missing = queries.lmtMissingByAgent(f, f.startDate(), f.endDate());
            if (!missing.isEmpty()) {
                long units = missing.stream().mapToLong(r -> lng(r[1])).sum();
                List<AlertItem> items = new ArrayList<>();
                missing.forEach(r -> items.add(new AlertItem((String) r[0], count(lng(r[1]), "unit", "units"))));
                alerts.add(new Alert("lmt_missing", "CRITICAL", count(units, "LMT unit", "LMT units") + " missing",
                        "Reconciled stock with units that have no recorded outcome", (int) units, "lmt", head(items)));
            }
            List<Object[]> open = queries.unreconciledByAgent(f, f.startDate(), min(today, f.endDate().plusDays(1)));
            if (!open.isEmpty()) {
                long days = open.stream().mapToLong(r -> lng(r[1])).sum();
                List<AlertItem> items = new ArrayList<>();
                open.forEach(r -> items.add(new AlertItem((String) r[0], count(lng(r[1]), "day", "days"))));
                alerts.add(new Alert("lmt_not_reconciled", "WARNING", count(days, "LMT stock day", "LMT stock days") + " not reconciled",
                        "Morning stock was entered but the day was never closed", (int) days, "lmt", head(items)));
            }
        }

        // Return % above the setting (shops and products, in the selected period)
        List<AlertItem> highReturn = new ArrayList<>();
        for (Object[] r : queries.shopsAboveReturnPercent(f, threshold)) {
            highReturn.add(new AlertItem("Shop · " + r[1], returnPct(num(r[2]), num(r[3]))));
        }
        for (Object[] r : queries.productsAboveReturnPercent(f, threshold)) {
            highReturn.add(new AlertItem("Product · " + r[1], returnPct(num(r[2]), num(r[3]))));
        }
        if (!highReturn.isEmpty()) {
            highReturn.sort(Comparator.comparingDouble((AlertItem i) -> -Double.parseDouble(i.value().replace("%", ""))));
            alerts.add(new Alert("high_return", "WARNING", count(highReturn.size(), "shop/product", "shops/products") + " with returns above "
                    + String.format(Locale.ROOT, "%.0f", threshold) + "%",
                    "Return value as a % of sale value in the selected period", highReturn.size(), "returns",
                    head(highReturn)));
        }

        alerts.sort(Comparator.comparingInt((Alert a) -> severityRank(a.severity()))
                .thenComparing(Comparator.comparingInt(Alert::count).reversed()));
        return new Attention(now, threshold, alerts);
    }

    // ==================================================================
    // 3. Sales section
    // ==================================================================

    @Cacheable(cacheNames = CacheConfig.SALES_COCKPIT, key = KEY_PREFIX + "sales:' + #f.cacheKey()")
    public SalesSection salesSection(CockpitFilter f) {
        // Six extra days before the range so the first days' 7-day average is real, not partial.
        List<Object[]> rows = queries.dailySales(f, f.startDate().minusDays(6), f.endDate());
        Map<LocalDate, Double> saleByDay = byDate(rows, 1);
        Map<LocalDate, Double> returnByDay = byDate(rows, 2);
        Map<LocalDate, Double> unitsByDay = byDate(rows, 3);
        List<DailySales> daily = new ArrayList<>();
        for (LocalDate d = f.startDate(); !d.isAfter(f.endDate()); d = d.plusDays(1)) {
            double s = saleByDay.getOrDefault(d, 0.0);
            double r = returnByDay.getOrDefault(d, 0.0);
            double avg = sum(saleByDay, d.minusDays(6), d) / 7.0;
            daily.add(new DailySales(d, s, r, s - r, unitsByDay.getOrDefault(d, 0.0).longValue(), avg));
        }

        List<SegmentSales> bySegment = new ArrayList<>();
        for (Object[] r : queries.salesBySegment(f)) {
            bySegment.add(new SegmentSales(segmentOf((String) r[0]), num(r[1]), num(r[2]), num(r[1]) - num(r[2]), lng(r[3])));
        }
        bySegment.sort(Comparator.comparingDouble(SegmentSales::netValue).reversed());

        List<AgentSales> byAgent = new ArrayList<>();
        for (Object[] r : queries.salesByAgent(f)) {
            byAgent.add(new AgentSales((Long) r[0], (String) r[1], (String) r[2], num(r[3]), num(r[4]),
                    num(r[3]) - num(r[4]), lng(r[5]), lng(r[6])));
        }
        byAgent.sort(Comparator.comparingDouble(AgentSales::netValue).reversed());

        List<AreaSales> byArea = new ArrayList<>();
        for (Object[] r : queries.salesByArea(f)) {
            byArea.add(new AreaSales((Long) r[0], r[1] == null ? "No area" : (String) r[1], (String) r[2],
                    (String) r[3], (String) r[4], num(r[5]), num(r[6]), num(r[5]) - num(r[6]), lng(r[7])));
        }
        byArea.sort(Comparator.comparingDouble(AreaSales::netValue).reversed());

        List<ProductSales> top = new ArrayList<>();
        for (Object[] r : queries.topProducts(f, 15)) {
            top.add(new ProductSales((Long) r[0], (String) r[1], lng(r[2]), num(r[3]), num(r[4])));
        }

        return new SalesSection(LocalDateTime.now(clock), daily, bySegment, byAgent, byArea, top);
    }

    // ==================================================================
    // Drill-down + filter options (not cached: drill-down is on demand)
    // ==================================================================

    public PageResponse<VoucherRow> vouchers(CockpitFilter f, Long productId, boolean noArea, int page, int size) {
        List<VoucherRow> rows = new ArrayList<>();
        for (Object[] r : queries.vouchers(f, productId, noArea, page, size)) {
            rows.add(new VoucherRow((Long) r[0], (LocalDate) r[1], (LocalTime) r[2], (String) r[3], (String) r[4],
                    (String) r[5], (String) r[6], num(r[7]), num(r[8]), num(r[7]) - num(r[8]), lng(r[9]), lng(r[10])));
        }
        long total = queries.countVouchers(f, productId, noArea);
        int totalPages = (int) Math.ceil(total / (double) size);
        return new PageResponse<>(rows, page, size, total, totalPages, page + 1 < totalPages);
    }

    @Cacheable(cacheNames = CacheConfig.SALES_COCKPIT, key = KEY_PREFIX + "options'")
    public FilterOptions filterOptions() {
        List<Option> areas = new ArrayList<>();
        queries.activeAreas().forEach(r -> areas.add(new Option((Long) r[0], (String) r[1], null)));
        List<Option> salesmen = new ArrayList<>();
        queries.activeSalesmen().forEach(r -> salesmen.add(new Option((Long) r[0], (String) r[1], segmentOf((String) r[2]))));
        return new FilterOptions(areas, salesmen);
    }

    // ==================================================================
    // helpers
    // ==================================================================

    static String segmentOf(String role) {
        if (CockpitFilter.ROLE_LOCAL.equals(role)) return "LOCAL";
        if (CockpitFilter.ROLE_LMT.equals(role)) return "LMT";
        return "OTHER";
    }

    /** "1 shop" / "3 shops". */
    static String count(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    private static String roleLabel(String role) {
        return CockpitFilter.ROLE_LOCAL.equals(role) ? "Local" : CockpitFilter.ROLE_LMT.equals(role) ? "LMT" : role;
    }

    private static int severityRank(String s) {
        return "CRITICAL".equals(s) ? 0 : "WARNING".equals(s) ? 1 : 2;
    }

    private static List<AlertItem> agentCounts(List<Object[]> rows, String unit) {
        List<AlertItem> items = new ArrayList<>();
        rows.forEach(r -> items.add(new AlertItem((String) r[0], count(lng(r[1]), unit.substring(0, unit.length() - 1), unit))));
        return items;
    }

    private static List<AlertItem> head(List<AlertItem> items) {
        return items.size() > ALERT_ITEMS ? new ArrayList<>(items.subList(0, ALERT_ITEMS)) : items;
    }

    private static String returnPct(double sale, double ret) {
        return String.format(Locale.ROOT, "%.1f%%", sale > 0 ? ret * 100.0 / sale : 0.0);
    }

    private static LocalDate latest(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    static double sum(Map<LocalDate, Double> m, LocalDate from, LocalDate to) {
        double total = 0;
        for (Map.Entry<LocalDate, Double> e : m.entrySet()) {
            if (!e.getKey().isBefore(from) && !e.getKey().isAfter(to)) total += e.getValue();
        }
        return total;
    }

    static List<DayValue> spark(Map<LocalDate, Double> m, LocalDate from, LocalDate to) {
        List<DayValue> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            out.add(new DayValue(d, m.getOrDefault(d, 0.0)));
        }
        return out;
    }

    private static double last(List<DayValue> s) {
        return s.isEmpty() ? 0 : s.get(s.size() - 1).value();
    }

    private static double weekAgo(List<DayValue> s) {
        return s.size() >= 8 ? s.get(s.size() - 8).value() : 0;
    }

    /** Percentage change; null when there is nothing to compare with. */
    static Double pct(double current, double previous) {
        if (previous == 0) return null;
        return (current - previous) * 100.0 / Math.abs(previous);
    }

    private static String money(double v) {
        return String.format(Locale.ROOT, "%,.0f", v);
    }
}
