package com.dawnbread.attendance.service;

import com.dawnbread.attendance.config.CacheConfig;
import com.dawnbread.attendance.dto.cockpit.CockpitFilter;
import com.dawnbread.attendance.dto.cockpit.SalesCockpitSectionDTOs.*;
import com.dawnbread.attendance.repository.SalesCockpitQueries;
import com.dawnbread.attendance.repository.SalesCockpitSectionQueries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.dawnbread.attendance.repository.SalesCockpitQueries.byDate;
import static com.dawnbread.attendance.repository.SalesCockpitQueries.id;
import static com.dawnbread.attendance.repository.SalesCockpitQueries.lng;
import static com.dawnbread.attendance.repository.SalesCockpitQueries.num;

/**
 * Sales cockpit lower sections (step 2). Counting happens in the database
 * (SalesCockpitSectionQueries); this class only arranges grouped rows:
 * percentages, ABC classes, the heatmap grid and clock-time formatting.
 * Cached 90 s per tenant + filter, identical requests share one computation.
 */
@Service
@Transactional(readOnly = true)
public class SalesCockpitSectionService {

    static final int TOP = 15;
    static final int SLOW = 10;
    static final int HEATMAP_PAGE_SIZE = 20;

    private static final String KEY_PREFIX = "T(com.dawnbread.attendance.security.TenantContext).getTenantId() + ':";

    @Autowired
    private SalesCockpitSectionQueries q;
    @Autowired
    private SalesCockpitQueries base;
    @Autowired
    private LmtSettingsService lmtSettingsService;
    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    // ================================================================ Returns

    @Cacheable(cacheNames = CacheConfig.SALES_COCKPIT, sync = true, key = KEY_PREFIX + "returns:' + #f.cacheKey()")
    public ReturnsSection returns(CockpitFilter f) {
        List<Object[]> daily = base.dailySales(f, f.startDate(), f.endDate());
        double sale = daily.stream().mapToDouble(r -> num(r[1])).sum();
        double ret = daily.stream().mapToDouble(r -> num(r[2])).sum();

        Map<LocalDate, Double> returnedUnits = byDate(daily, 4);
        Map<LocalDate, Double> rbeUnits = byDate(daily, 5);
        Map<LocalDate, Double> returnValue = byDate(daily, 2);
        List<ReturnDay> days = new ArrayList<>();
        for (LocalDate d = f.startDate(); !d.isAfter(f.endDate()); d = d.plusDays(1)) {
            days.add(new ReturnDay(d, returnedUnits.getOrDefault(d, 0.0).longValue(),
                    rbeUnits.getOrDefault(d, 0.0).longValue(), returnValue.getOrDefault(d, 0.0)));
        }

        return new ReturnsSection(LocalDateTime.now(clock), sale, ret, pct(ret, sale),
                lmtSettingsService.getReturnAlertPercent(),
                rates(q.returnsByShop(f, TOP), TOP),
                rates(q.returnsBySalesman(f), TOP),
                rates(q.returnsByProduct(f), TOP),
                days);
    }

    /** Rows (id, name, sale, return, returnedUnits) -> rates with returns, worst first, top n. */
    private static List<ReturnRate> rates(List<Object[]> rows, int n) {
        List<ReturnRate> out = new ArrayList<>();
        for (Object[] r : rows) {
            double s = num(r[2]);
            double rv = num(r[3]);
            if (rv <= 0) continue;
            out.add(new ReturnRate(id(r[0]), (String) r[1], s, rv, s > 0 ? rv * 100.0 / s : 100.0, lng(r[4])));
        }
        out.sort(Comparator.comparingDouble(ReturnRate::returnPercent).reversed()
                .thenComparing(Comparator.comparingDouble(ReturnRate::returnValue).reversed()));
        return out.size() > n ? new ArrayList<>(out.subList(0, n)) : out;
    }

    // ================================================================ Shop coverage

    @Cacheable(cacheNames = CacheConfig.SALES_COCKPIT, sync = true, key = KEY_PREFIX + "coverage:' + #f.cacheKey()")
    public CoverageSection coverage(CockpitFilter f) {
        List<SalesmanCoverage> bySalesman = new ArrayList<>();
        long assignedTotal = 0;
        long visitedTotal = 0;
        for (Object[] r : q.coverageBySalesman(f)) {
            long assigned = lng(r[3]);
            long visited = lng(r[4]);
            assignedTotal += assigned;
            visitedTotal += visited;
            bySalesman.add(new SalesmanCoverage(id(r[0]), (String) r[1], (String) r[2], assigned, visited,
                    pct(visited, assigned)));
        }
        bySalesman.sort(Comparator.comparingDouble(SalesmanCoverage::coveragePercent)
                .thenComparing(SalesmanCoverage::name));

        long assignedNow = base.countAssignedShops(f);
        LocalDate trendEnd = f.endDate().isAfter(today()) ? today() : f.endDate();
        Map<LocalDate, Double> visitedByDay = byDate(base.visitedAssignedShopsByDay(f, f.startDate(), trendEnd), 1);
        List<CoverageDay> trend = new ArrayList<>();
        for (LocalDate d = f.startDate(); !d.isAfter(trendEnd); d = d.plusDays(1)) {
            long v = visitedByDay.getOrDefault(d, 0.0).longValue();
            trend.add(new CoverageDay(d, v, assignedNow, pct(v, assignedNow)));
        }

        List<ReasonCount> reasons = new ArrayList<>();
        q.feedbackReasons(f).forEach(r -> reasons.add(new ReasonCount(String.valueOf(r[0]), lng(r[1]))));

        return new CoverageSection(LocalDateTime.now(clock), assignedTotal, visitedTotal, pct(visitedTotal, assignedTotal),
                bySalesman, trend, reasons);
    }

    /**
     * Shop x day grid for the week (Monday to Sunday) that contains
     * weekOf (default: the filter's end date). Least-visited shops first,
     * paginated so 3,000 shops never load at once.
     */
    @Cacheable(cacheNames = CacheConfig.SALES_COCKPIT, sync = true,
            key = KEY_PREFIX + "heatmap:' + #f.cacheKey() + ':' + #weekOf + ':' + #page")
    public Heatmap heatmap(CockpitFilter f, LocalDate weekOf, int page) {
        LocalDate anchor = weekOf != null ? weekOf : f.endDate();
        LocalDate monday = anchor.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) days.add(monday.plusDays(i));

        List<Object[]> shops = q.heatmapShops(f, monday, sunday, page, HEATMAP_PAGE_SIZE);
        List<Long> ids = shops.stream().map(r -> id(r[0])).toList();
        Map<Long, Map<LocalDate, String>> cells = new HashMap<>();
        for (Object[] c : q.heatmapCells(ids, monday, sunday)) {
            Map<LocalDate, String> m = cells.computeIfAbsent(id(c[0]), k -> new HashMap<>());
            String kind = String.valueOf(c[2]).trim();
            // A real QR scan outranks a feedback-only visit on the same day.
            m.merge((LocalDate) c[1], kind, (a, b) -> "SCAN".equals(a) || "SCAN".equals(b) ? "SCAN" : a);
        }
        LocalDate today = today();
        List<HeatmapRow> rows = new ArrayList<>();
        for (Object[] r : shops) {
            Long shopId = id(r[0]);
            Map<LocalDate, String> m = cells.getOrDefault(shopId, Map.of());
            List<String> states = new ArrayList<>();
            for (LocalDate d : days) {
                states.add(d.isAfter(today) ? "FUTURE" : m.getOrDefault(d, "NONE"));
            }
            rows.add(new HeatmapRow(shopId, (String) r[1], (String) r[2], (int) lng(r[3]), states));
        }
        return new Heatmap(monday, days, rows, page, HEATMAP_PAGE_SIZE, base.countAssignedShops(f));
    }

    // ================================================================ Salesman scorecard

    @Cacheable(cacheNames = CacheConfig.SALES_COCKPIT, sync = true, key = KEY_PREFIX + "salesmen:' + #f.cacheKey()")
    public ScorecardSection scorecard(CockpitFilter f) {
        Map<Long, Object[]> visits = byAgent(q.visitsBySalesman(f));
        Map<Long, Object[]> money = byAgent(q.moneyBySalesman(f));
        Map<Long, Object[]> noScan = byAgent(q.noScanBySalesman(f));
        Map<Long, Object[]> offLoc = byAgent(q.offLocationBySalesman(f));
        Map<Long, Object[]> times = byAgent(q.timesBySalesman(f));

        List<Scorecard> rows = new ArrayList<>();
        for (Object[] r : q.roster(f)) {
            Long agentId = id(r[0]);
            Object[] v = visits.get(agentId);
            Object[] m = money.get(agentId);
            Object[] t = times.get(agentId);
            long visitCount = v == null ? 0 : lng(v[1]);
            double sale = m == null ? 0 : num(m[2]);
            double ret = m == null ? 0 : num(m[3]);
            double net = sale - ret;
            long flagged = (noScan.containsKey(agentId) ? lng(noScan.get(agentId)[1]) : 0)
                    + (offLoc.containsKey(agentId) ? lng(offLoc.get(agentId)[1]) : 0);
            rows.add(new Scorecard(agentId, (String) r[1], (String) r[2], visitCount,
                    v == null ? 0 : lng(v[2]), m == null ? 0 : lng(m[1]), sale, ret, net,
                    visitCount > 0 ? net / visitCount : 0, flagged,
                    t == null ? null : clockTime(num(t[1])), t == null ? null : clockTime(num(t[2])),
                    t == null ? 0 : lng(t[3])));
        }
        rows.sort(Comparator.comparingDouble(Scorecard::netValue).reversed().thenComparing(Scorecard::name));
        return new ScorecardSection(LocalDateTime.now(clock), rows);
    }

    /** One salesman's days in the period, for the detail panel (not cached: on demand). */
    public List<SalesmanDay> salesmanDays(CockpitFilter f, Long agentId) {
        Map<LocalDate, Object[]> visits = byDay(q.dayVisitsForSalesman(f, agentId));
        Map<LocalDate, Object[]> money = byDay(q.dayMoneyForSalesman(f, agentId));
        Map<LocalDate, Object[]> times = byDay(q.dayTimesForSalesman(f, agentId));
        TreeSet<LocalDate> dates = new TreeSet<>(Comparator.reverseOrder());
        dates.addAll(visits.keySet());
        dates.addAll(money.keySet());
        dates.addAll(times.keySet());
        List<SalesmanDay> out = new ArrayList<>();
        for (LocalDate d : dates) {
            Object[] v = visits.get(d);
            Object[] m = money.get(d);
            Object[] t = times.get(d);
            out.add(new SalesmanDay(d, v == null ? 0 : lng(v[1]), m == null ? 0 : lng(m[1]),
                    m == null ? 0 : num(m[2]) - num(m[3]),
                    t == null ? null : clockTime(num(t[1])), t == null ? null : clockTime(num(t[2]))));
        }
        return out;
    }

    // ================================================================ LMT stock

    @Cacheable(cacheNames = CacheConfig.SALES_COCKPIT, sync = true, key = KEY_PREFIX + "lmt:' + #f.cacheKey()")
    public LmtStockSection lmtStock(CockpitFilter f) {
        if ("LOCAL".equals(f.segment())) {
            return new LmtStockSection(LocalDateTime.now(clock), false, List.of(), new LmtHealth(0, 0, 0));
        }
        List<LmtSalesmanStock> rows = new ArrayList<>();
        for (Object[] r : q.lmtStockBySalesman(f)) {
            rows.add(new LmtSalesmanStock(id(r[0]), (String) r[1], lng(r[2]), lng(r[3]), lng(r[4]), lng(r[5]),
                    lng(r[6]), lng(r[7]), lng(r[8])));
        }
        rows.sort(Comparator.comparingLong(LmtSalesmanStock::missing).reversed().thenComparing(LmtSalesmanStock::name));
        Object[] h = q.lmtHealth(f, today());
        return new LmtStockSection(LocalDateTime.now(clock), true, rows, new LmtHealth(lng(h[0]), lng(h[1]), lng(h[2])));
    }

    // ================================================================ Products

    @Cacheable(cacheNames = CacheConfig.SALES_COCKPIT, sync = true, key = KEY_PREFIX + "products:' + #f.cacheKey()")
    public ProductsSection products(CockpitFilter f) {
        List<Object[]> rows = q.productSales(f);
        double total = rows.stream().mapToDouble(r -> num(r[3])).sum();
        List<ProductRow> products = new ArrayList<>();
        double cumulative = 0;
        for (Object[] r : rows) {
            double value = num(r[3]);
            double share = pct(value, total);
            // Class by where the product STARTS in the cumulative curve, so the product that
            // crosses 80% is still an A (standard ABC: A items make up the first 80% of value).
            String abc = cumulative < 80.0 ? "A" : cumulative < 95.0 ? "B" : "C";
            cumulative += share;
            products.add(new ProductRow(id(r[0]), (String) r[1], lng(r[2]), value, share, Math.min(cumulative, 100.0), abc));
        }
        List<SlowProduct> slow = new ArrayList<>();
        for (Object[] r : q.slowProducts(f, SLOW)) {
            slow.add(new SlowProduct(id(r[0]), (String) r[1], lng(r[2]), num(r[3]), (LocalDate) r[4]));
        }
        return new ProductsSection(LocalDateTime.now(clock), total, products, slow);
    }

    // ================================================================ helpers

    private static Map<Long, Object[]> byAgent(List<Object[]> rows) {
        Map<Long, Object[]> m = new HashMap<>();
        rows.forEach(r -> m.put(id(r[0]), r));
        return m;
    }

    private static Map<LocalDate, Object[]> byDay(List<Object[]> rows) {
        Map<LocalDate, Object[]> m = new HashMap<>();
        rows.forEach(r -> m.put((LocalDate) r[0], r));
        return m;
    }

    static double pct(double part, double whole) {
        return whole > 0 ? part * 100.0 / whole : 0.0;
    }

    /** Seconds since midnight -> "HH:mm" (Pakistan local times, as stored). */
    static String clockTime(double seconds) {
        long s = Math.round(seconds);
        return String.format(Locale.ROOT, "%02d:%02d", (s / 3600) % 24, (s % 3600) / 60);
    }
}
