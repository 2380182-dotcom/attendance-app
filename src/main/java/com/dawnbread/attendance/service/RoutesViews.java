package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.routes.RoutesDTOs.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Sales > Routes views, built from one cached RoutesData (no SQL here):
 * the per-salesman summary, the filtered + sorted not-visited list, the
 * unassigned list and the Excel file of the current not-visited list.
 */
public final class RoutesViews {

    private RoutesViews() {
    }

    public static final String NO_AREA = "No area";
    public static final Set<String> SORTS = Set.of("shopName", "shopCode", "area", "salesman", "type", "lastVisit",
            "daysSinceLastVisit", "visitedDays", "missedDays");

    /** The not-visited list's filters. Any of them may be null (= no filter). */
    public record Filter(Long agentId, String type, String area, Integer minDaysSince, String sort, boolean desc) {
    }

    // ------------------------------------------------------------------ summary

    public static Summary summary(RoutesData data) {
        Map<Long, List<ShopRow>> bySalesman = new LinkedHashMap<>();
        for (ShopRow s : data.shops()) bySalesman.computeIfAbsent(s.agentId(), k -> new ArrayList<>()).add(s);

        List<SalesmanCoverage> salesmen = new ArrayList<>();
        for (List<ShopRow> rows : bySalesman.values()) {
            ShopRow first = rows.get(0);
            salesmen.add(coverage(first.agentId(), first.salesman(), first.type(), rows));
        }
        salesmen.sort(Comparator.comparing(SalesmanCoverage::salesman, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SalesmanCoverage::agentId));
        SalesmanCoverage total = coverage(null, "All salesmen", null, data.shops());

        int unassignedNotVisited = 0, noSalesman = 0, inactive = 0, notSalesman = 0;
        for (UnassignedRow u : data.unassigned()) {
            if (u.dueDays() > 0 && u.visitedDays() == 0) unassignedNotVisited++;
            switch (u.reason()) {
                case "NO_SALESMAN" -> noSalesman++;
                case "SALESMAN_INACTIVE" -> inactive++;
                default -> notSalesman++;
            }
        }

        Set<String> areas = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ShopRow s : data.shops()) areas.add(areaName(s.area()));
        List<Option> options = salesmen.stream().map(c -> new Option(c.agentId(), c.salesman(), c.type())).toList();

        return new Summary(data.from(), data.to(), data.singleDay(), data.rangeDays(), data.holidays(), data.updatedAt(),
                total, salesmen, new UnassignedSummary(data.unassigned().size(), unassignedNotVisited, noSalesman,
                inactive, notSalesman), List.copyOf(areas), options);
    }

    private static SalesmanCoverage coverage(Long agentId, String name, String type, List<ShopRow> rows) {
        int due = 0, visited = 0, shopsNotVisited = 0;
        for (ShopRow s : rows) {
            due += s.dueDays();
            visited += s.visitedDays();
            if (s.dueDays() > 0 && s.visitedDays() == 0) shopsNotVisited++;
        }
        Double pct = due == 0 ? null : Math.round(visited * 1000.0 / due) / 10.0;
        return new SalesmanCoverage(agentId, name, type, rows.size(), due, visited, due - visited, pct,
                shopsNotVisited, due == 0);
    }

    // ------------------------------------------------------------------ not-visited list

    /** Shops with at least one due day not visited, filtered and sorted (all of them, for the page and the Excel). */
    public static List<NotVisitedRow> notVisited(RoutesData data, Filter f) {
        Predicate<ShopRow> keep = s -> s.missedDays() > 0;
        if (f.agentId() != null) keep = keep.and(s -> f.agentId().equals(s.agentId()));
        if (f.type() != null) keep = keep.and(s -> f.type().equals(s.type()));
        if (f.area() != null) keep = keep.and(s -> f.area().equalsIgnoreCase(areaName(s.area())));
        List<NotVisitedRow> out = new ArrayList<>();
        for (ShopRow s : data.shops()) {
            if (!keep.test(s)) continue;
            Integer since = daysSince(s.lastVisit(), data.to());
            if (f.minDaysSince() != null && since != null && since < f.minDaysSince()) continue;
            out.add(new NotVisitedRow(s.shopId(), s.shopName(), s.shopCode(), areaName(s.area()), s.agentId(),
                    s.salesman(), s.type(), s.lastVisit(), since, s.dueDays(), s.visitedDays(), s.missedDays()));
        }
        out.sort(comparator(f.sort(), f.desc(), data.singleDay()));
        return out;
    }

    public static NotVisitedPage page(RoutesData data, Filter f, int page, int size) {
        List<NotVisitedRow> all = notVisited(data, f);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new NotVisitedPage(List.copyOf(all.subList(from, to)), all.size(), page, size, data.from(), data.to(),
                data.singleDay(), data.updatedAt());
    }

    /** "Never" sorts as the longest time since a visit. */
    private static final Comparator<NotVisitedRow> SINCE_ASC = Comparator.comparing(
            (NotVisitedRow r) -> r.daysSinceLastVisit() == null ? Integer.MAX_VALUE : r.daysSinceLastVisit());
    private static final Comparator<NotVisitedRow> TIE_BREAK = Comparator
            .comparing(NotVisitedRow::shopName, String.CASE_INSENSITIVE_ORDER).thenComparing(NotVisitedRow::shopId);

    static Comparator<NotVisitedRow> comparator(String sort, boolean desc, boolean singleDay) {
        if (sort == null) {
            // Default: longest unvisited first (one day); most missed days first (a range).
            Comparator<NotVisitedRow> c = singleDay ? SINCE_ASC.reversed()
                    : Comparator.comparingInt(NotVisitedRow::missedDays).reversed().thenComparing(SINCE_ASC.reversed());
            return c.thenComparing(TIE_BREAK);
        }
        Comparator<NotVisitedRow> c = switch (sort) {
            case "shopCode" -> Comparator.comparing(NotVisitedRow::shopCode, String.CASE_INSENSITIVE_ORDER);
            case "area" -> Comparator.comparing(NotVisitedRow::area, String.CASE_INSENSITIVE_ORDER);
            case "salesman" -> Comparator.comparing(NotVisitedRow::salesman, String.CASE_INSENSITIVE_ORDER);
            case "type" -> Comparator.comparing(NotVisitedRow::type);
            // Oldest last visit = longest time since a visit, so lastVisit ascending = daysSince descending.
            case "lastVisit" -> SINCE_ASC.reversed();
            case "daysSinceLastVisit" -> SINCE_ASC;
            case "visitedDays" -> Comparator.comparingInt(NotVisitedRow::visitedDays);
            case "missedDays" -> Comparator.comparingInt(NotVisitedRow::missedDays);
            default -> Comparator.comparing(NotVisitedRow::shopName, String.CASE_INSENSITIVE_ORDER);
        };
        return (desc ? c.reversed() : c).thenComparing(TIE_BREAK);
    }

    // ------------------------------------------------------------------ unassigned

    /** Not-visited first, then longest unvisited, then name. */
    public static UnassignedList unassigned(RoutesData data) {
        List<UnassignedListRow> out = new ArrayList<>();
        for (UnassignedRow u : data.unassigned()) {
            out.add(new UnassignedListRow(u.shopId(), u.shopName(), u.shopCode(), areaName(u.area()), u.reason(),
                    u.assignedName(), u.lastVisit(), daysSince(u.lastVisit(), data.to()), u.dueDays(), u.visitedDays(),
                    u.missedDays()));
        }
        out.sort(Comparator.comparing((UnassignedListRow r) -> r.dueDays() > 0 && r.visitedDays() == 0 ? 0 : 1)
                .thenComparing((UnassignedListRow r) -> r.daysSinceLastVisit() == null ? Integer.MAX_VALUE
                        : r.daysSinceLastVisit(), Comparator.reverseOrder())
                .thenComparing(UnassignedListRow::shopName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(UnassignedListRow::shopId));
        return new UnassignedList(out, data.from(), data.to(), data.singleDay(), data.updatedAt());
    }

    // ------------------------------------------------------------------ Excel

    /** The current filtered + sorted not-visited list as .xlsx (streaming workbook: ~100 rows in memory at a time). */
    public static byte[] excel(RoutesData data, Filter f, String filterText) {
        List<NotVisitedRow> rows = notVisited(data, f);
        boolean range = !data.singleDay();
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Not visited");
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);

            int r = 0;
            cell(sheet.createRow(r++), 0, "Routes: shops not visited", bold);
            cell(sheet.createRow(r++), 0, "Dates: " + (range ? data.from() + " to " + data.to() : data.from().toString()), null);
            if (!data.holidays().isEmpty()) {
                cell(sheet.createRow(r++), 0, "Company holidays skipped: " + String.join(", ",
                        data.holidays().stream().map(h -> h.date() + " " + h.name()).toList()), null);
            }
            cell(sheet.createRow(r++), 0, "Filters: " + filterText, null);
            cell(sheet.createRow(r++), 0, "Updated at " + data.updatedAt().toLocalTime() + " (Pakistan time) · "
                    + rows.size() + " shops", null);
            r++;

            List<String> head = new ArrayList<>(List.of("Shop", "Code", "Area", "Salesman", "Type", "Last visit",
                    "Days since last visit"));
            if (range) head.addAll(List.of("Due days", "Visited days", "Not visited days"));
            Row h = sheet.createRow(r++);
            for (int i = 0; i < head.size(); i++) cell(h, i, head.get(i), bold);

            for (NotVisitedRow n : rows) {
                Row row = sheet.createRow(r++);
                cell(row, 0, n.shopName(), null);
                cell(row, 1, n.shopCode(), null);
                cell(row, 2, n.area(), null);
                cell(row, 3, n.salesman(), null);
                cell(row, 4, "LMT".equals(n.type()) ? "LMT" : "Local", null);
                cell(row, 5, n.lastVisit() == null ? "Never" : n.lastVisit().toString(), null);
                if (n.daysSinceLastVisit() == null) cell(row, 6, "Never", null);
                else row.createCell(6).setCellValue(n.daysSinceLastVisit());
                if (range) {
                    row.createCell(7).setCellValue(n.dueDays());
                    row.createCell(8).setCellValue(n.visitedDays());
                    row.createCell(9).setCellValue(n.missedDays());
                }
            }
            int[] widths = { 36, 14, 22, 24, 8, 12, 12, 10, 12, 16 };
            for (int i = 0; i < head.size(); i++) sheet.setColumnWidth(i, widths[i] * 256);
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the Excel file", e);
        }
    }

    public static String fileName(RoutesData data) {
        return "routes-not-visited-" + data.from() + (data.singleDay() ? "" : "_to_" + data.to()) + ".xlsx";
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        if (style != null) c.setCellStyle(style);
    }

    // ------------------------------------------------------------------ helpers

    static String areaName(String area) {
        return area == null || area.isBlank() ? NO_AREA : area;
    }

    static Integer daysSince(LocalDate lastVisit, LocalDate to) {
        return lastVisit == null ? null : (int) ChronoUnit.DAYS.between(lastVisit, to);
    }
}
