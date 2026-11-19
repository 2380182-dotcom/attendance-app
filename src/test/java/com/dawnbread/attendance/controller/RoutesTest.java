package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.*;
import com.dawnbread.attendance.repository.*;
import com.dawnbread.attendance.security.TokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.io.ByteArrayInputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sales > Routes. Every test seeds its OWN company and calls the API with
 * that company's token, so counts are exact and other tests' data can
 * never leak in (which also proves the endpoints are company-scoped).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoutesTest {

    private static final List<String> ALL_DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    private static final List<String> ENDPOINTS = List.of("summary", "not-visited", "unassigned", "not-visited/export");

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private TokenProvider tokenProvider;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private AgentRepository agentRepository;
    @Autowired
    private AreaRepository areaRepository;
    @Autowired
    private CustomerShopRepository customerShopRepository;
    @Autowired
    private ShopVisitScanRepository shopVisitScanRepository;
    @Autowired
    private ShopFeedbackRepository shopFeedbackRepository;
    @Autowired
    private CompanyHolidayRepository companyHolidayRepository;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Test classes with their own context (e.g. AuthRateLimiterTest) recreate the shared in-memory database,
     * which restarts the ids; a company seeded here can then get the id of an earlier test's company whose
     * cockpit / Routes answer is still in the 90 s cache. Start every test with empty caches.
     */
    @org.junit.jupiter.api.BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(n -> java.util.Objects.requireNonNull(cacheManager.getCache(n)).clear());
    }
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Karachi"));

    // ------------------------------------------------------------------ seeding

    private Tenant tenant(String label) {
        Tenant t = new Tenant();
        t.setCompanyCode("RT" + label + (System.nanoTime() % 1_000_000));
        t.setName("Routes " + label);
        t.setIsActive(true);
        t.setCreatedAt(LocalDateTime.now());
        t.setCreatedBy("TEST");
        return tenantRepository.save(t);
    }

    private Agent salesman(Tenant t, String role, String name, List<String> workingDays, boolean active) {
        Agent a = new Agent();
        a.setTenantId(t.getId());
        String id = "RT" + System.nanoTime();
        a.setAgentId(id);
        a.setName(name);
        a.setEmail(id.toLowerCase() + "@example.com");
        a.setRole(role);
        a.setWorkingDays(workingDays);
        a.setCreatedAt(LocalDateTime.now());
        a.setIsActive(active);
        return agentRepository.save(a);
    }

    private Area area(Tenant t, String name) {
        Area area = new Area();
        area.setTenantId(t.getId());
        area.setName(name);
        area.setIsActive(true);
        area.setCreatedAt(LocalDateTime.now());
        return areaRepository.save(area);
    }

    private CustomerShop shop(Tenant t, Area area, Agent assigned, String name, boolean active) {
        CustomerShop s = new CustomerShop();
        s.setTenantId(t.getId());
        s.setShopCode("RT-" + System.nanoTime());
        s.setShopName(name);
        s.setArea(area);
        s.setLatitude(0.0);
        s.setLongitude(0.0);
        s.setRadius(100.0);
        s.setIsActive(active);
        s.setCreatedAt(LocalDateTime.now());
        s.setAssignedAgent(assigned);
        return customerShopRepository.save(s);
    }

    private void scan(Tenant t, Agent agent, CustomerShop shop, LocalDate date, ShopVisitStatus status) {
        ShopVisitScan s = new ShopVisitScan();
        s.setTenantId(t.getId());
        s.setAgentId(agent.getId());
        s.setCustomerShop(shop);
        s.setScannedCode(shop.getShopCode());
        s.setScanDate(date);
        s.setScanTime(LocalTime.of(9, 55));
        s.setGpsLatitude(0.0);
        s.setGpsLongitude(0.0);
        s.setGeofenceStatus(status == ShopVisitStatus.SUCCESS ? GeofenceStatus.INSIDE : GeofenceStatus.OUTSIDE);
        s.setVisitStatus(status);
        s.setCreatedAt(LocalDateTime.now());
        shopVisitScanRepository.save(s);
    }

    private void feedback(Tenant t, Agent agent, CustomerShop shop, LocalDate date) {
        ShopFeedback fb = new ShopFeedback();
        fb.setTenantId(t.getId());
        fb.setAgentId(agent.getId());
        fb.setCustomerShop(shop);
        fb.setReason(ShopFeedbackReason.SHOP_CLOSED);
        fb.setGpsLatitude(0.0);
        fb.setGpsLongitude(0.0);
        fb.setGeofenceStatus(GeofenceStatus.INSIDE);
        fb.setVisitDate(date);
        fb.setVisitTime(LocalTime.of(11, 0));
        fb.setCreatedAt(LocalDateTime.now());
        shopFeedbackRepository.save(fb);
    }

    private void holiday(Tenant t, LocalDate date, String name) {
        CompanyHoliday h = new CompanyHoliday();
        h.setTenantId(t.getId());
        h.setHolidayDate(date);
        h.setName(name);
        h.setCreatedAt(LocalDateTime.now());
        companyHolidayRepository.save(h);
    }

    // ------------------------------------------------------------------ http

    private String token(Tenant t, String role) {
        return tokenProvider.generateToken(999L, "RT_" + role, role, t.getId());
    }

    private <T> ResponseEntity<T> get(String token, String path, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("http://localhost:" + port + "/api/" + path, HttpMethod.GET,
                new HttpEntity<>(headers), type);
    }

    private JsonNode data(String token, String path) throws Exception {
        ResponseEntity<String> r = get(token, path, String.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), path + " -> " + r.getBody());
        return mapper.readTree(r.getBody()).get("data");
    }

    private static JsonNode salesmanRow(JsonNode summary, String name) {
        for (JsonNode s : summary.get("salesmen")) {
            if (name.equals(s.get("salesman").asText())) return s;
        }
        throw new AssertionError("no salesman " + name + " in " + summary.get("salesmen"));
    }

    private static List<String> names(JsonNode rows) {
        List<String> out = new ArrayList<>();
        for (JsonNode r : rows) out.add(r.get("shopName").asText());
        return out;
    }

    // ------------------------------------------------------------------ tests

    @Test
    void onlyAdminAndSalesCanOpenRoutes() {
        Tenant t = tenant("ROLE");
        for (String role : List.of("SALESMAN_LOCAL", "SALESMAN_LMT", "HR", "AGENT")) {
            for (String path : ENDPOINTS) {
                assertEquals(HttpStatus.FORBIDDEN, get(token(t, role), "routes/" + path, String.class).getStatusCode(),
                        role + " must not read /routes/" + path);
            }
        }
        for (String role : List.of("ADMIN", "SALES")) {
            for (String path : ENDPOINTS) {
                assertEquals(HttpStatus.OK, get(token(t, role), "routes/" + path, String.class).getStatusCode(),
                        role + " /routes/" + path);
            }
        }
    }

    @Test
    void aShopIsVisitedOnlyByItsOwnSalesmansScanOrFeedbackAndTotalsMatchTheCockpit() throws Exception {
        Tenant t = tenant("VIS");
        Area north = area(t, "North");
        Agent ali = salesman(t, "SALESMAN_LOCAL", "Ali", ALL_DAYS, true);
        Agent bilal = salesman(t, "SALESMAN_LMT", "Bilal", ALL_DAYS, true);
        CustomerShop scanned = shop(t, north, ali, "A Scanned", true);
        CustomerShop withFeedback = shop(t, north, ali, "B Feedback", true);
        CustomerShop byOther = shop(t, area(t, "South"), ali, "C Other salesman", true);
        CustomerShop failedScan = shop(t, north, ali, "D Failed scan", true);
        CustomerShop lmtShop = shop(t, north, bilal, "E LMT three days", true);
        shop(t, north, ali, "F Inactive shop", false);

        scan(t, ali, scanned, today, ShopVisitStatus.SUCCESS);
        feedback(t, ali, withFeedback, today);
        scan(t, bilal, byOther, today, ShopVisitStatus.SUCCESS);          // not Ali's visit
        scan(t, ali, failedScan, today, ShopVisitStatus.OUTSIDE_GEOFENCE); // not a visit
        scan(t, bilal, lmtShop, today.minusDays(3), ShopVisitStatus.SUCCESS);

        String admin = token(t, "ADMIN");
        JsonNode summary = data(admin, "routes/summary");
        assertTrue(summary.get("singleDay").asBoolean());
        JsonNode a = salesmanRow(summary, "Ali");
        assertEquals("LOCAL", a.get("type").asText());
        assertEquals(4, a.get("assignedShops").asInt(), "the inactive shop is left out");
        assertEquals(2, a.get("visited").asInt());
        assertEquals(2, a.get("notVisited").asInt());
        assertEquals(50.0, a.get("coveragePercent").asDouble());
        JsonNode b = salesmanRow(summary, "Bilal");
        assertEquals("LMT", b.get("type").asText());
        assertEquals(1, b.get("assignedShops").asInt());
        assertEquals(0, b.get("visited").asInt());
        assertEquals(5, summary.get("total").get("assignedShops").asInt());
        assertEquals(3, summary.get("total").get("notVisited").asInt());

        // Same numbers as the Sales cockpit's "Shop visits today" / "Not visited today" cards.
        JsonNode cards = data(admin, "cockpit/sales/highlights").get("cards");
        for (JsonNode c : cards) {
            if ("visits_today".equals(c.get("key").asText())) assertEquals(2, c.get("value").asInt());
            if ("not_visited_today".equals(c.get("key").asText())) assertEquals(3, c.get("value").asInt());
        }

        JsonNode list = data(admin, "routes/not-visited");
        assertEquals(3, list.get("totalElements").asInt());
        // Default order: never visited first, then the longest time since a visit.
        assertEquals(List.of("C Other salesman", "D Failed scan", "E LMT three days"), names(list.get("content")));
        JsonNode lmt = list.get("content").get(2);
        assertEquals(3, lmt.get("daysSinceLastVisit").asInt());
        assertEquals(today.minusDays(3).toString(), lmt.get("lastVisit").asText());
        assertTrue(list.get("content").get(0).get("daysSinceLastVisit").isNull(), "never visited");
        assertEquals("South", list.get("content").get(0).get("area").asText());

        assertEquals(List.of("E LMT three days"), names(data(admin, "routes/not-visited?type=LMT").get("content")));
        assertEquals(List.of("C Other salesman", "D Failed scan"),
                names(data(admin, "routes/not-visited?agentId=" + ali.getId()).get("content")));
        assertEquals(List.of("C Other salesman", "D Failed scan"),
                names(data(admin, "routes/not-visited?minDaysSince=4").get("content")), "never counts as 4+ days");
        assertEquals(List.of("D Failed scan", "E LMT three days"),
                names(data(admin, "routes/not-visited?area=North").get("content")));
        assertEquals(List.of("E LMT three days", "D Failed scan", "C Other salesman"),
                names(data(admin, "routes/not-visited?sort=shopName&dir=desc").get("content")));
        JsonNode page2 = data(admin, "routes/not-visited?size=2&page=1");
        assertEquals(3, page2.get("totalElements").asInt());
        assertEquals(List.of("E LMT three days"), names(page2.get("content")));

        // Another company sees none of it.
        JsonNode other = data(token(tenant("OTHER"), "ADMIN"), "routes/summary");
        assertEquals(0, other.get("total").get("assignedShops").asInt());
        assertEquals(0, other.get("salesmen").size());
    }

    @Test
    void shopsWithNoSalesmanOrAnInactiveSalesmanAreListedAsUnassigned() throws Exception {
        Tenant t = tenant("UN");
        Agent active = salesman(t, "SALESMAN_LOCAL", "Active", ALL_DAYS, true);
        Agent gone = salesman(t, "SALESMAN_LOCAL", "Gone", ALL_DAYS, false);
        CustomerShop none = shop(t, area(t, "Area " + System.nanoTime()), null, "No salesman", true);
        CustomerShop inactive = shop(t, area(t, "Area " + System.nanoTime()), gone, "Inactive salesman", true);
        shop(t, area(t, "Area " + System.nanoTime()), active, "Assigned", true);
        scan(t, active, none, today, ShopVisitStatus.SUCCESS);  // any salesman's visit counts for unassigned shops

        String admin = token(t, "ADMIN");
        JsonNode summary = data(admin, "routes/summary");
        assertEquals(1, summary.get("total").get("assignedShops").asInt());
        assertEquals(1, summary.get("salesmen").size(), "the inactive salesman has no summary row");
        JsonNode un = summary.get("unassigned");
        assertEquals(2, un.get("shops").asInt());
        assertEquals(1, un.get("notVisited").asInt());
        assertEquals(1, un.get("noSalesman").asInt());
        assertEquals(1, un.get("salesmanInactive").asInt());

        JsonNode rows = data(admin, "routes/unassigned").get("content");
        assertEquals("Inactive salesman", rows.get(0).get("shopName").asText(), "not visited first");
        assertEquals("SALESMAN_INACTIVE", rows.get(0).get("reason").asText());
        assertEquals("Gone", rows.get(0).get("assignedName").asText());
        assertEquals("NO_SALESMAN", rows.get(1).get("reason").asText());
        assertEquals(1, rows.get(1).get("visitedDays").asInt());
        assertTrue(rows.get(1).get("assignedName").isNull());
    }

    @Test
    void aRangeSkipsDaysOffAndHolidaysAndCountsEachVisitedDayOnce() throws Exception {
        Tenant t = tenant("RANGE");
        Agent sami = salesman(t, "SALESMAN_LOCAL", "Sami", List.of("MON", "TUE", "WED", "THU", "FRI", "SAT"), true);
        CustomerShop shop = shop(t, area(t, "Area " + System.nanoTime()), sami, "Range shop", true);

        // A 7-day window has exactly one Sunday (Sami's day off); one other past day is a holiday.
        LocalDate from = today.minusDays(6);
        LocalDate sunday = from.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<LocalDate> working = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            if (!d.equals(sunday)) working.add(d);
        }
        LocalDate holidayDate = working.get(0);
        holiday(t, holidayDate, "Test Holiday");
        LocalDate v1 = working.get(1), v2 = working.get(2);
        scan(t, sami, shop, v1, ShopVisitStatus.SUCCESS);
        feedback(t, sami, shop, v1);                            // same day: still one visited day
        feedback(t, sami, shop, v2);
        scan(t, sami, shop, holidayDate, ShopVisitStatus.SUCCESS); // a holiday visit is not a due day

        String admin = token(t, "ADMIN");
        String range = "?startDate=" + from + "&endDate=" + today;
        JsonNode summary = data(admin, "routes/summary" + range);
        assertFalse(summary.get("singleDay").asBoolean());
        assertEquals(1, summary.get("holidays").size());
        JsonNode s = salesmanRow(summary, "Sami");
        assertEquals(5, s.get("due").asInt(), "7 days - Sunday - holiday");
        assertEquals(2, s.get("visited").asInt());
        assertEquals(3, s.get("notVisited").asInt());
        assertEquals(40.0, s.get("coveragePercent").asDouble());
        assertEquals(0, s.get("shopsNotVisited").asInt());

        JsonNode row = data(admin, "routes/not-visited" + range).get("content").get(0);
        assertEquals(5, row.get("dueDays").asInt());
        assertEquals(2, row.get("visitedDays").asInt());
        assertEquals(3, row.get("missedDays").asInt());
        LocalDate last = v2.isAfter(holidayDate) ? v2 : holidayDate;
        assertEquals(last.toString(), row.get("lastVisit").asText(), "last visit includes holiday visits");

        // A single day off: nothing due, "day off", not in the not-visited list.
        String sun = "?startDate=" + sunday + "&endDate=" + sunday;
        JsonNode off = salesmanRow(data(admin, "routes/summary" + sun), "Sami");
        assertTrue(off.get("dayOff").asBoolean());
        assertEquals(0, off.get("due").asInt());
        assertTrue(off.get("coveragePercent").isNull());
        assertEquals(0, data(admin, "routes/not-visited" + sun).get("totalElements").asInt());

        // A single holiday: the holiday is reported and nothing is due.
        String hol = "?startDate=" + holidayDate + "&endDate=" + holidayDate;
        JsonNode holSummary = data(admin, "routes/summary" + hol);
        assertEquals("Test Holiday", holSummary.get("holidays").get(0).get("name").asText());
        assertTrue(holSummary.get("total").get("dayOff").asBoolean());
    }

    @Test
    void badDatesAndFiltersAreRefused() {
        String admin = token(tenant("BAD"), "ADMIN");
        for (String q : List.of("startDate=" + today.minusDays(92) + "&endDate=" + today,
                "endDate=" + today.plusDays(1), "startDate=" + today + "&endDate=" + today.minusDays(1),
                "startDate=yesterday", "type=SOMETHING", "sort=password", "minDaysSince=-1")) {
            assertEquals(HttpStatus.BAD_REQUEST, get(admin, "routes/not-visited?" + q, String.class).getStatusCode(), q);
        }
        assertEquals(HttpStatus.OK, get(admin, "routes/summary?startDate=" + today.minusDays(91) + "&endDate=" + today,
                String.class).getStatusCode(), "92 days is allowed");
    }

    @Test
    void theExcelFileHoldsTheCurrentFilteredList() throws Exception {
        Tenant t = tenant("XL");
        Agent ali = salesman(t, "SALESMAN_LOCAL", "Ali", ALL_DAYS, true);
        Agent bilal = salesman(t, "SALESMAN_LMT", "Bilal", ALL_DAYS, true);
        shop(t, area(t, "Area " + System.nanoTime()), ali, "Local one", true);
        shop(t, area(t, "Area " + System.nanoTime()), ali, "Local two", true);
        shop(t, area(t, "Area " + System.nanoTime()), bilal, "LMT one", true);

        ResponseEntity<byte[]> r = get(token(t, "SALES"), "routes/not-visited/export?type=LOCAL&sort=shopName", byte[].class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertTrue(r.getHeaders().getContentType().toString().contains("spreadsheetml"));
        assertTrue(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("routes-not-visited-" + today + ".xlsx"));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(r.getBody()))) {
            Sheet sheet = wb.getSheet("Not visited");
            List<String> shops = new ArrayList<>();
            boolean inTable = false;
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                String first = row.getCell(0) == null ? "" : row.getCell(0).getStringCellValue();
                if (inTable) shops.add(first);
                if ("Shop".equals(first)) inTable = true;
            }
            assertEquals(List.of("Local one", "Local two"), shops);
            assertTrue(sheet.getRow(2).getCell(0).getStringCellValue().contains("Local only"));
        }
    }
}
