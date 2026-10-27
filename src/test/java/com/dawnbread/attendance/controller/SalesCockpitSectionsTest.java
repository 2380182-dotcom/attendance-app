package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.*;
import com.dawnbread.attendance.repository.*;
import com.dawnbread.attendance.security.TokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sales cockpit step 2 sections: Returns, Shop coverage (+ heatmap),
 * Salesman scorecard (+ day detail), LMT stock, Products. Each test seeds
 * its own tenant so every number is exact and isolation is proven.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SalesCockpitSectionsTest {

    private static final String BASE = "/api/cockpit/sales/";
    private static final List<String> SECTIONS = List.of("returns", "coverage", "coverage/heatmap", "salesmen",
            "lmt-stock", "products");

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
    private ProductRepository productRepository;
    @Autowired
    private AreaRepository areaRepository;
    @Autowired
    private CustomerShopRepository customerShopRepository;
    @Autowired
    private SalesRecordRepository salesRecordRepository;
    @Autowired
    private ShopVisitScanRepository shopVisitScanRepository;
    @Autowired
    private ShopFeedbackRepository shopFeedbackRepository;
    @Autowired
    private LmtDailyStockRepository lmtDailyStockRepository;
    @Autowired
    private LmtDailyStockItemRepository lmtDailyStockItemRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalDate today = LocalDate.now(com.dawnbread.attendance.util.KarachiTime.ZONE);

    // ------------------------------------------------------------------ seeding

    private Tenant tenant(String label) {
        Tenant t = new Tenant();
        t.setCompanyCode("CS" + label + (System.nanoTime() % 1_000_000));
        t.setName("Sections " + label);
        t.setIsActive(true);
        t.setCreatedAt(LocalDateTime.now());
        t.setCreatedBy("TEST");
        return tenantRepository.save(t);
    }

    private Agent agent(Tenant t, String role, String name) {
        Agent a = new Agent();
        a.setTenantId(t.getId());
        String id = "CS" + System.nanoTime();
        a.setAgentId(id);
        a.setName(name);
        a.setEmail(id.toLowerCase() + "@example.com");
        a.setRole(role);
        a.setIsActive(true);
        a.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(a);
    }

    private Area area(Tenant t) {
        Area area = new Area();
        area.setTenantId(t.getId());
        area.setName("Area " + System.nanoTime());
        area.setIsActive(true);
        area.setCreatedAt(LocalDateTime.now());
        return areaRepository.save(area);
    }

    private CustomerShop shop(Tenant t, Area area, Agent assigned, String name) {
        CustomerShop s = new CustomerShop();
        s.setTenantId(t.getId());
        s.setShopCode("CS-" + System.nanoTime());
        s.setShopName(name);
        s.setArea(area);
        s.setLatitude(0.0);
        s.setLongitude(0.0);
        s.setRadius(100.0);
        s.setIsActive(true);
        s.setCreatedAt(LocalDateTime.now());
        s.setAssignedAgent(assigned);
        return customerShopRepository.save(s);
    }

    private Product product(Tenant t, String name) {
        Product p = new Product();
        p.setTenantId(t.getId());
        p.setName(name + " " + System.nanoTime());
        p.setAgentPrice(10.0);
        p.setSalesmanPrice(10.0);
        p.setIsActive(true);
        p.setCreatedAt(LocalDateTime.now());
        return productRepository.save(p);
    }

    private record Line(Product product, TransactionType type, int qty, double totalPrice) {
    }

    private void voucher(Tenant t, Agent agent, CustomerShop shop, LocalDate date, LocalTime time, Line... lines) {
        SalesRecord r = new SalesRecord();
        r.setTenantId(t.getId());
        r.setAgent(agent);
        r.setCustomerShop(shop);
        r.setStoreName(shop.getShopName());
        r.setSaleDate(date);
        r.setSaleTime(time);
        r.setSubmittedAt(LocalDateTime.now());
        r.setTotalAmount(0.0);
        r.setTotalUnits(0);
        r.setStatus("PENDING");
        r.setCreatedAt(LocalDateTime.now());
        r.setAgentRoleAtSale(agent.getRole());
        r.setDistanceFromShopMeters(20.0);
        for (Line l : lines) {
            SaleItem i = new SaleItem();
            i.setTenantId(t.getId());
            i.setSalesRecord(r);
            i.setAgentId(agent.getId());
            i.setSaleDate(date);
            i.setCustomerShopId(shop.getId());
            i.setTransactionType(l.type());
            i.setProduct(l.product());
            i.setQuantity(l.qty());
            i.setUnitPrice(10.0);
            i.setTotalPrice(l.totalPrice());
            r.getItems().add(i);
        }
        salesRecordRepository.save(r);
    }

    private void scan(Tenant t, Agent agent, CustomerShop shop, LocalDate date, LocalTime time, ShopVisitStatus status) {
        ShopVisitScan s = new ShopVisitScan();
        s.setTenantId(t.getId());
        s.setAgentId(agent.getId());
        s.setCustomerShop(shop);
        s.setScannedCode(shop.getShopCode());
        s.setScanDate(date);
        s.setScanTime(time);
        s.setGpsLatitude(0.0);
        s.setGpsLongitude(0.0);
        s.setGeofenceStatus(status == ShopVisitStatus.SUCCESS ? GeofenceStatus.INSIDE : GeofenceStatus.OUTSIDE);
        s.setVisitStatus(status);
        s.setCreatedAt(LocalDateTime.now());
        shopVisitScanRepository.save(s);
    }

    private void feedback(Tenant t, Agent agent, CustomerShop shop, LocalDate date, ShopFeedbackReason reason) {
        ShopFeedback fb = new ShopFeedback();
        fb.setTenantId(t.getId());
        fb.setAgentId(agent.getId());
        fb.setCustomerShop(shop);
        fb.setReason(reason);
        fb.setGpsLatitude(0.0);
        fb.setGpsLongitude(0.0);
        fb.setGeofenceStatus(GeofenceStatus.INSIDE);
        fb.setVisitDate(date);
        fb.setVisitTime(LocalTime.of(11, 0));
        fb.setCreatedAt(LocalDateTime.now());
        shopFeedbackRepository.save(fb);
    }

    private void lmtStock(Tenant t, Agent agent, Product p, LocalDate date, LmtStockStatus status,
                          int opening, int sold, int returned, int unsold, int rbe, int missing) {
        LmtDailyStock stock = new LmtDailyStock();
        stock.setTenantId(t.getId());
        stock.setAgentId(agent.getId());
        stock.setStockDate(date);
        stock.setStatus(status);
        stock.setCreatedAt(LocalDateTime.now());
        stock = lmtDailyStockRepository.save(stock);
        LmtDailyStockItem item = new LmtDailyStockItem();
        item.setTenantId(t.getId());
        item.setLmtDailyStock(stock);
        item.setProduct(p);
        item.setOpeningStock(opening);
        item.setSoldQty(sold);
        item.setReturnedQty(returned);
        item.setUnsoldQty(unsold);
        item.setRbeQty(rbe);
        item.setMissingQty(missing);
        lmtDailyStockItemRepository.save(item);
    }

    // ------------------------------------------------------------------ http

    private String token(Tenant t, String role) {
        return tokenProvider.generateToken(999L, "CS_" + role, role, t.getId());
    }

    private ResponseEntity<String> get(String token, String pathAndQuery) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("http://localhost:" + port + BASE + pathAndQuery, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private JsonNode data(String token, String pathAndQuery) throws Exception {
        ResponseEntity<String> r = get(token, pathAndQuery);
        assertEquals(HttpStatus.OK, r.getStatusCode(), pathAndQuery + " -> " + r.getBody());
        return mapper.readTree(r.getBody()).get("data");
    }

    // ------------------------------------------------------------------ tests

    @Test
    void salesmenAndHrAreBlockedFromEveryNewSectionWhileAdminAndSalesCanRead() {
        Tenant t = tenant("ROLE");
        Agent s = agent(t, "SALESMAN_LMT", "Someone");
        for (String role : List.of("SALESMAN_LOCAL", "SALESMAN_LMT", "HR", "AGENT")) {
            for (String section : SECTIONS) {
                assertEquals(HttpStatus.FORBIDDEN, get(token(t, role), section).getStatusCode(), role + " /" + section);
            }
            assertEquals(HttpStatus.FORBIDDEN, get(token(t, role), "salesmen/" + s.getId() + "/days").getStatusCode());
        }
        for (String role : List.of("ADMIN", "SALES")) {
            for (String section : SECTIONS) {
                assertEquals(HttpStatus.OK, get(token(t, role), section).getStatusCode(), role + " /" + section);
            }
        }
    }

    @Test
    void returnsSectionShowsRatesByShopSalesmanProductAndExpiredVsReplacedOverTime() throws Exception {
        Tenant t = tenant("RET");
        Agent local = agent(t, "SALESMAN_LOCAL", "Returner");
        Area area = area(t);
        CustomerShop shopA = shop(t, area, local, "High Return Shop");
        CustomerShop shopB = shop(t, area, local, "Clean Shop");
        Product bread = product(t, "Bread");
        voucher(t, local, shopA, today, LocalTime.of(10, 0),
                new Line(bread, TransactionType.SALE, 50, 500.0),
                new Line(bread, TransactionType.RETURN, 10, 100.0),
                new Line(bread, TransactionType.RETURN_BEFORE_EXPIRY, 3, 30.0));
        voucher(t, local, shopB, today, LocalTime.of(11, 0), new Line(bread, TransactionType.SALE, 50, 500.0));

        JsonNode r = data(token(t, "ADMIN"), "returns");
        assertEquals(1000.0, r.get("saleValue").asDouble(), 0.001);
        assertEquals(100.0, r.get("returnValue").asDouble(), 0.001);
        assertEquals(10.0, r.get("returnPercent").asDouble(), 0.001);
        assertEquals(15.0, r.get("alertPercent").asDouble(), 0.001);
        assertEquals(1, r.get("byShop").size(), "only shops that had returns");
        assertEquals("High Return Shop", r.get("byShop").get(0).get("name").asText());
        assertEquals(20.0, r.get("byShop").get(0).get("returnPercent").asDouble(), 0.001);
        assertEquals(10, r.get("byShop").get(0).get("returnedUnits").asLong());
        assertEquals("Returner", r.get("bySalesman").get(0).get("name").asText());
        assertEquals(10.0, r.get("bySalesman").get(0).get("returnPercent").asDouble(), 0.001);
        assertEquals(1, r.get("byProduct").size());
        JsonNode lastDay = r.get("daily").get(r.get("daily").size() - 1);
        assertEquals(today.toString(), lastDay.get("date").asText());
        assertEquals(10, lastDay.get("returnedUnits").asLong());
        assertEquals(3, lastDay.get("rbeUnits").asLong(), "return before expiry tracked separately, in units");
        assertEquals(100.0, lastDay.get("returnValue").asDouble(), 0.001, "RBE never adds money");

        // Drill-down by shop (used when a shop bar is clicked).
        ResponseEntity<String> drill = restTemplate.exchange("http://localhost:" + port
                        + "/api/cockpit/sales/vouchers?shopId=" + shopA.getId(), HttpMethod.GET,
                new HttpEntity<>(headers(token(t, "ADMIN"))), String.class);
        assertEquals(1, mapper.readTree(drill.getBody()).get("data").get("totalElements").asLong());
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    @Test
    void coverageCountsVisitedShopsPerSalesmanTrendReasonsAndTheWeeklyHeatmap() throws Exception {
        Tenant t = tenant("COV");
        Agent lmt = agent(t, "SALESMAN_LMT", "Coverer");
        Area area = area(t);
        CustomerShop scanned = shop(t, area, lmt, "Scanned Shop");
        CustomerShop fedBack = shop(t, area, lmt, "Feedback Shop");
        CustomerShop missed = shop(t, area, lmt, "Missed Shop");
        scan(t, lmt, scanned, today, LocalTime.of(9, 0), ShopVisitStatus.SUCCESS);
        feedback(t, lmt, fedBack, today, ShopFeedbackReason.STOCK_FULL);
        scan(t, lmt, missed, today, LocalTime.of(9, 30), ShopVisitStatus.OUTSIDE_GEOFENCE); // refused: not a visit

        String admin = token(t, "SALES");
        JsonNode c = data(admin, "coverage");
        assertEquals(3, c.get("assignedShops").asLong());
        assertEquals(2, c.get("visitedShops").asLong());
        JsonNode row = c.get("bySalesman").get(0);
        assertEquals("Coverer", row.get("name").asText());
        assertEquals(3, row.get("assignedShops").asLong());
        assertEquals(2, row.get("visitedShops").asLong());
        JsonNode lastDay = c.get("trend").get(c.get("trend").size() - 1);
        assertEquals(2, lastDay.get("visitedShops").asLong());
        assertEquals(1, c.get("feedbackReasons").size());
        assertEquals("STOCK_FULL", c.get("feedbackReasons").get(0).get("reason").asText());

        JsonNode h = data(admin, "coverage/heatmap");
        assertEquals(today.with(DayOfWeek.MONDAY).toString(), h.get("weekStart").asText());
        assertEquals(7, h.get("days").size());
        assertEquals(3, h.get("totalShops").asLong());
        assertEquals("Missed Shop", h.get("rows").get(0).get("shopName").asText(), "least-visited first");
        int todayIndex = today.getDayOfWeek().getValue() - 1;
        for (JsonNode r : h.get("rows")) {
            String state = r.get("days").get(todayIndex).asText();
            switch (r.get("shopName").asText()) {
                case "Scanned Shop" -> assertEquals("SCAN", state);
                case "Feedback Shop" -> assertEquals("FEEDBACK", state);
                default -> assertEquals("NONE", state);
            }
            if (todayIndex < 6) assertEquals("FUTURE", r.get("days").get(6).asText());
        }
        assertEquals(HttpStatus.BAD_REQUEST, get(admin, "coverage/heatmap?weekOf=last-week").getStatusCode());
    }

    @Test
    void scorecardAddsUpVisitsMoneyFlagsAndAverageFirstAndLastVisit() throws Exception {
        Tenant t = tenant("SCORE");
        Agent lmt = agent(t, "SALESMAN_LMT", "Scorer");
        Agent idle = agent(t, "SALESMAN_LMT", "Idle One");
        Area area = area(t);
        CustomerShop a = shop(t, area, lmt, "Shop A");
        CustomerShop b = shop(t, area, lmt, "Shop B");
        Product p = product(t, "Bun");
        scan(t, lmt, a, today, LocalTime.of(9, 0), ShopVisitStatus.SUCCESS);
        voucher(t, lmt, a, today, LocalTime.of(9, 10), new Line(p, TransactionType.SALE, 30, 300.0),
                new Line(p, TransactionType.RETURN, 5, 50.0));
        voucher(t, lmt, b, today, LocalTime.of(15, 0), new Line(p, TransactionType.SALE, 10, 100.0)); // no scan
        scan(t, lmt, b, today, LocalTime.of(14, 50), ShopVisitStatus.OUTSIDE_GEOFENCE);
        scan(t, lmt, a, today.minusDays(1), LocalTime.of(10, 0), ShopVisitStatus.SUCCESS);

        String admin = token(t, "ADMIN");
        JsonNode rows = data(admin, "salesmen").get("rows");
        assertEquals(2, rows.size(), "every salesman in the roster, even with no activity");
        JsonNode s = rows.get(0);
        assertEquals("Scorer", s.get("name").asText());
        assertEquals(2, s.get("visits").asLong(), "shop A today + shop A yesterday (failed scan is not a visit)");
        assertEquals(1, s.get("shopsCovered").asLong());
        assertEquals(2, s.get("vouchers").asLong());
        assertEquals(400.0, s.get("saleValue").asDouble(), 0.001);
        assertEquals(350.0, s.get("netValue").asDouble(), 0.001);
        assertEquals(175.0, s.get("netPerVisit").asDouble(), 0.001);
        assertEquals(2, s.get("flaggedVouchers").asLong(), "1 voucher without scan + 1 off-location attempt");
        assertEquals("09:30", s.get("avgFirstVisit").asText(), "avg of 09:00 (today) and 10:00 (yesterday)");
        assertEquals("12:30", s.get("avgLastVisit").asText(), "avg of 15:00 (today) and 10:00 (yesterday)");
        assertEquals(2, s.get("activeDays").asLong());
        assertEquals("Idle One", rows.get(1).get("name").asText());
        assertEquals(0, rows.get(1).get("visits").asLong());

        JsonNode days = data(admin, "salesmen/" + lmt.getId() + "/days");
        assertEquals(2, days.size());
        assertEquals(today.toString(), days.get(0).get("date").asText(), "newest first");
        assertEquals(1, days.get(0).get("visits").asLong());
        assertEquals(2, days.get(0).get("vouchers").asLong());
        assertEquals(350.0, days.get(0).get("netValue").asDouble(), 0.001);
        assertEquals("09:00", days.get(0).get("firstVisit").asText());
        assertEquals("15:00", days.get(0).get("lastVisit").asText());
    }

    @Test
    void lmtStockShowsWhereOpeningStockWentAndTheStockDayHealth() throws Exception {
        Tenant t = tenant("LMT");
        Agent lmt = agent(t, "SALESMAN_LMT", "Stocker");
        Product p = product(t, "Rusk");
        lmtStock(t, lmt, p, today.minusDays(1), LmtStockStatus.RECONCILED, 50, 40, 2, 5, 1, 2);
        lmtStock(t, lmt, p, today.minusDays(2), LmtStockStatus.RECONCILED, 30, 25, 0, 5, 0, 0);
        lmtStock(t, lmt, p, today.minusDays(3), LmtStockStatus.OPEN, 20, 0, 0, 0, 0, 0);
        lmtStock(t, lmt, p, today, LmtStockStatus.OPEN, 20, 0, 0, 0, 0, 0); // today's open day is normal

        String admin = token(t, "ADMIN");
        JsonNode l = data(admin, "lmt-stock");
        assertTrue(l.get("applicable").asBoolean());
        JsonNode s = l.get("bySalesman").get(0);
        assertEquals(80, s.get("opening").asLong());
        assertEquals(65, s.get("sold").asLong());
        assertEquals(2, s.get("returned").asLong());
        assertEquals(10, s.get("unsold").asLong());
        assertEquals(1, s.get("replaced").asLong());
        assertEquals(2, s.get("missing").asLong());
        assertEquals(2, s.get("stockDays").asLong(), "reconciled days only");
        assertEquals(1, l.get("health").get("okDays").asLong());
        assertEquals(1, l.get("health").get("shortageDays").asLong());
        assertEquals(1, l.get("health").get("openDays").asLong(), "past open day only, not today");

        JsonNode local = data(admin, "lmt-stock?segment=LOCAL");
        assertFalse(local.get("applicable").asBoolean());
        assertEquals(0, local.get("bySalesman").size());
    }

    @Test
    void productsSectionRanksByValueWithAbcClassesAndListsSlowProductsIncludingUnsold() throws Exception {
        Tenant t = tenant("PROD");
        Agent local = agent(t, "SALESMAN_LOCAL", "Seller");
        CustomerShop s = shop(t, area(t), local, "Shop");
        Product big = product(t, "Big");
        Product mid = product(t, "Mid");
        Product small = product(t, "Small");
        Product never = product(t, "Never");
        voucher(t, local, s, today, LocalTime.of(10, 0),
                new Line(big, TransactionType.SALE, 80, 800.0),
                new Line(mid, TransactionType.SALE, 15, 150.0),
                new Line(small, TransactionType.SALE, 5, 50.0));

        JsonNode p = data(token(t, "ADMIN"), "products");
        assertEquals(1000.0, p.get("totalSaleValue").asDouble(), 0.001);
        JsonNode rows = p.get("products");
        assertEquals(3, rows.size());
        assertEquals("A", rows.get(0).get("abcClass").asText());
        assertEquals(80.0, rows.get(0).get("sharePercent").asDouble(), 0.001);
        assertEquals("B", rows.get(1).get("abcClass").asText(), "starts at 80%");
        assertEquals(95.0, rows.get(1).get("cumulativePercent").asDouble(), 0.001);
        assertEquals("C", rows.get(2).get("abcClass").asText(), "starts at 95%");

        JsonNode slow = p.get("slowProducts");
        assertTrue(slow.get(0).get("name").asText().startsWith("Never"), "unsold products first");
        assertEquals(0, slow.get(0).get("soldUnits").asLong());
        assertTrue(slow.get(0).get("lastSold").isNull());
        assertTrue(slow.get(1).get("name").asText().startsWith("Small"));
        assertEquals(today.toString(), slow.get(1).get("lastSold").asText());
        assertNotNull(never);
    }

    @Test
    void anotherCompanySeesNothingAndAnEmptyCompanyGetsEmptySectionsNotErrors() throws Exception {
        Tenant a = tenant("ISOA");
        Tenant b = tenant("ISOB");
        Agent lmt = agent(a, "SALESMAN_LMT", "Tenant A Man");
        CustomerShop s = shop(a, area(a), lmt, "A Shop");
        Product p = product(a, "A Bread");
        voucher(a, lmt, s, today, LocalTime.of(10, 0), new Line(p, TransactionType.SALE, 5, 50.0),
                new Line(p, TransactionType.RETURN, 1, 10.0));
        scan(a, lmt, s, today, LocalTime.of(9, 0), ShopVisitStatus.SUCCESS);
        lmtStock(a, lmt, p, today.minusDays(1), LmtStockStatus.RECONCILED, 10, 8, 1, 1, 0, 0);

        String adminB = token(b, "ADMIN");
        assertEquals(0, data(adminB, "returns").get("byShop").size());
        assertEquals(0.0, data(adminB, "returns").get("saleValue").asDouble(), 0.001);
        assertEquals(0, data(adminB, "coverage").get("assignedShops").asLong());
        assertEquals(0, data(adminB, "coverage/heatmap").get("rows").size());
        assertEquals(0, data(adminB, "salesmen").get("rows").size());
        assertEquals(0, data(adminB, "salesmen/" + lmt.getId() + "/days").size(), "another company's salesman has no rows");
        assertEquals(0, data(adminB, "lmt-stock").get("bySalesman").size());
        assertEquals(0, data(adminB, "products").get("products").size());
        assertEquals(1, data(token(a, "ADMIN"), "products").get("products").size());
    }
}
