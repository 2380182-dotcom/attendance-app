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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sales cockpit endpoints (step 1: highlights, attention, sales section,
 * voucher drill-down, filter options). Every test seeds its OWN tenant and
 * calls the API with that tenant's token, so totals are exact and other
 * tests' data can never leak in — which is also what proves the endpoints
 * are tenant-scoped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SalesCockpitTest {

    private static final String BASE = "/api/cockpit/sales/";
    private static final List<String> SECTIONS = List.of("highlights", "attention", "sales", "vouchers", "filter-options");

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
    private LmtDailyStockRepository lmtDailyStockRepository;
    @Autowired
    private LmtDailyStockItemRepository lmtDailyStockItemRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Karachi"));

    // ------------------------------------------------------------------ seeding

    private Tenant tenant(String label) {
        Tenant t = new Tenant();
        String code = "CK" + label + (System.nanoTime() % 1_000_000);
        t.setCompanyCode(code);
        t.setName("Cockpit " + label);
        t.setIsActive(true);
        t.setCreatedAt(LocalDateTime.now());
        t.setCreatedBy("TEST");
        return tenantRepository.save(t);
    }

    private Agent agent(Tenant t, String role, String name) {
        Agent a = new Agent();
        a.setTenantId(t.getId());
        String id = "CK" + System.nanoTime();
        a.setAgentId(id);
        a.setName(name);
        a.setEmail(id.toLowerCase() + "@example.com");
        a.setRole(role);
        a.setCreatedAt(LocalDateTime.now());
        a.setIsActive(true);
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

    private CustomerShop shop(Tenant t, Area area, Agent assigned, String name) {
        CustomerShop s = new CustomerShop();
        s.setTenantId(t.getId());
        s.setShopCode("CK-" + System.nanoTime());
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
        p.setAgentPrice(50.0);
        p.setSalesmanPrice(50.0);
        p.setIsActive(true);
        p.setCreatedAt(LocalDateTime.now());
        return productRepository.save(p);
    }

    private record Line(Product product, TransactionType type, int qty, double totalPrice) {
    }

    private SalesRecord voucher(Tenant t, Agent agent, CustomerShop shop, LocalDate date, Double distance, Line... lines) {
        SalesRecord r = new SalesRecord();
        r.setTenantId(t.getId());
        r.setAgent(agent);
        r.setCustomerShop(shop);
        r.setStoreName(shop.getShopName());
        r.setSaleDate(date);
        r.setSaleTime(LocalTime.of(10, 0));
        r.setSubmittedAt(LocalDateTime.now());
        r.setTotalAmount(0.0);
        r.setTotalUnits(0);
        r.setStatus("PENDING");
        r.setCreatedAt(LocalDateTime.now());
        r.setAgentRoleAtSale(agent.getRole());
        r.setDistanceFromShopMeters(distance);
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
            i.setUnitPrice(50.0);
            i.setTotalPrice(l.totalPrice());
            r.getItems().add(i);
        }
        return salesRecordRepository.save(r);
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

    private void lmtStock(Tenant t, Agent agent, Product p, LocalDate date, LmtStockStatus status, int missing) {
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
        item.setOpeningStock(50);
        item.setSoldQty(40);
        item.setMissingQty(missing);
        lmtDailyStockItemRepository.save(item);
    }

    // ------------------------------------------------------------------ http

    private String token(Tenant t, String role) {
        return tokenProvider.generateToken(999L, "CK_" + role, role, t.getId());
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

    private static JsonNode card(JsonNode highlights, String key) {
        for (JsonNode c : highlights.get("cards")) {
            if (key.equals(c.get("key").asText())) return c;
        }
        throw new AssertionError("no card " + key);
    }

    private static JsonNode alert(JsonNode attention, String key) {
        for (JsonNode a : attention.get("alerts")) {
            if (key.equals(a.get("key").asText())) return a;
        }
        return null;
    }

    // ------------------------------------------------------------------ tests

    @Test
    void salesmenAndHrAreBlockedWhileAdminAndSalesCanReadEverySection() {
        Tenant t = tenant("ROLE");
        for (String role : List.of("SALESMAN_LOCAL", "SALESMAN_LMT", "HR", "AGENT")) {
            for (String section : SECTIONS) {
                assertEquals(HttpStatus.FORBIDDEN, get(token(t, role), section).getStatusCode(),
                        role + " must not read /" + section);
            }
        }
        for (String role : List.of("ADMIN", "SALES")) {
            for (String section : SECTIONS) {
                assertEquals(HttpStatus.OK, get(token(t, role), section).getStatusCode(), role + " /" + section);
            }
        }
    }

    @Test
    void unitsAreSoldOnlyAndReturnBeforeExpiryNeverChangesTheNetTotal() throws Exception {
        Tenant t = tenant("NET");
        Agent local = agent(t, "SALESMAN_LOCAL", "Local One");
        Area area = area(t, "Gulberg");
        CustomerShop shop = shop(t, area, local, "Shop A");
        Product bread = product(t, "Bread");
        Product bun = product(t, "Bun");
        // 10 sold (500) + 4 sold (200), 2 returned (100), 3 RBE deliberately priced (150) to prove it is ignored.
        SalesRecord v = voucher(t, local, shop, today, 20.0,
                new Line(bread, TransactionType.SALE, 10, 500.0),
                new Line(bun, TransactionType.SALE, 4, 200.0),
                new Line(bread, TransactionType.RETURN, 2, 100.0),
                new Line(bun, TransactionType.RETURN_BEFORE_EXPIRY, 3, 150.0),
                new Line(bun, TransactionType.UNSOLD, 5, 0.0));
        String admin = token(t, "ADMIN");

        JsonNode sales = data(admin, "sales");
        JsonNode todayRow = sales.get("daily").get(sales.get("daily").size() - 1);
        assertEquals(today.toString(), todayRow.get("date").asText());
        assertEquals(700.0, todayRow.get("saleValue").asDouble(), 0.001);
        assertEquals(100.0, todayRow.get("returnValue").asDouble(), 0.001);
        assertEquals(600.0, todayRow.get("netValue").asDouble(), 0.001, "net = sales - returns; RBE never counted");
        assertEquals(14, todayRow.get("soldUnits").asLong(), "units = SOLD units only");
        assertEquals(30, sales.get("daily").size(), "default range is the last 30 days, every day present");

        JsonNode seg = sales.get("bySegment").get(0);
        assertEquals("LOCAL", seg.get("segment").asText());
        assertEquals(600.0, seg.get("netValue").asDouble(), 0.001);
        JsonNode byAgent = sales.get("bySalesman").get(0);
        assertEquals("Local One", byAgent.get("name").asText());
        assertEquals(1, byAgent.get("vouchers").asLong());
        assertEquals("Gulberg", sales.get("byArea").get(0).get("name").asText());
        assertEquals(2, sales.get("topProducts").size());
        assertEquals(500.0, sales.get("topProducts").get(0).get("saleValue").asDouble(), 0.001);

        JsonNode highlights = data(admin, "highlights");
        assertEquals(600.0, card(highlights, "net_sales").get("value").asDouble(), 0.001);
        assertEquals(700.0, card(highlights, "sale_return").get("value").asDouble(), 0.001);
        assertEquals(100.0, card(highlights, "sale_return").get("secondaryValue").asDouble(), 0.001);
        assertEquals(3.0, card(highlights, "rbe").get("value").asDouble(), 0.001);
        assertEquals(14, card(highlights, "net_sales").get("sparkline").size());
        assertEquals(10, highlights.get("cards").size());

        JsonNode drill = data(admin, "vouchers");
        assertEquals(1, drill.get("totalElements").asLong());
        JsonNode row = drill.get("content").get(0);
        assertEquals(v.getId().longValue(), row.get("id").asLong());
        assertEquals(600.0, row.get("netValue").asDouble(), 0.001);
        assertEquals(14, row.get("soldUnits").asLong());
        assertEquals(3, row.get("rbeUnits").asLong());
        assertEquals(1, data(admin, "vouchers?productId=" + bun.getId()).get("totalElements").asLong());
    }

    @Test
    void segmentAreaAndSalesmanFiltersNarrowEveryNumber() throws Exception {
        Tenant t = tenant("FLT");
        Agent local = agent(t, "SALESMAN_LOCAL", "Local");
        Agent lmt = agent(t, "SALESMAN_LMT", "Lmt");
        Area north = area(t, "North");
        Area south = area(t, "South");
        CustomerShop shopN = shop(t, north, local, "North Shop");
        CustomerShop shopS = shop(t, south, lmt, "South Shop");
        Product p = product(t, "Bread");
        voucher(t, local, shopN, today, 20.0, new Line(p, TransactionType.SALE, 2, 100.0));
        voucher(t, lmt, shopS, today, 20.0, new Line(p, TransactionType.SALE, 6, 300.0));
        String admin = token(t, "SALES");

        assertEquals(400.0, card(data(admin, "highlights"), "net_sales").get("value").asDouble(), 0.001);
        assertEquals(100.0, card(data(admin, "highlights?segment=LOCAL"), "net_sales").get("value").asDouble(), 0.001);
        assertEquals(300.0, card(data(admin, "highlights?segment=LMT"), "net_sales").get("value").asDouble(), 0.001);
        assertEquals(300.0, card(data(admin, "highlights?areaId=" + south.getId()), "net_sales").get("value").asDouble(), 0.001);
        assertEquals(100.0, card(data(admin, "highlights?agentId=" + local.getId()), "net_sales").get("value").asDouble(), 0.001);
        assertEquals(0.0, card(data(admin, "highlights?segment=LMT&areaId=" + north.getId()), "net_sales").get("value").asDouble(), 0.001);
        assertEquals(1, data(admin, "sales?segment=LMT").get("bySalesman").size());
        assertEquals(1, data(admin, "vouchers?areaId=" + north.getId()).get("totalElements").asLong());

        // A range that excludes today has nothing.
        String old = "startDate=" + today.minusDays(60) + "&endDate=" + today.minusDays(31);
        assertEquals(0.0, card(data(admin, "highlights?" + old), "net_sales").get("value").asDouble(), 0.001);
        assertEquals(0, data(admin, "vouchers?" + old).get("totalElements").asLong());
    }

    @Test
    void anotherTenantSeesNoneOfThisTenantsNumbers() throws Exception {
        Tenant a = tenant("TA");
        Tenant b = tenant("TB");
        Agent local = agent(a, "SALESMAN_LOCAL", "Tenant A Salesman");
        CustomerShop shop = shop(a, area(a, "A Area"), local, "A Shop");
        voucher(a, local, shop, today, 20.0, new Line(product(a, "Bread"), TransactionType.SALE, 5, 250.0));

        String adminB = token(b, "ADMIN");
        assertEquals(0.0, card(data(adminB, "highlights"), "net_sales").get("value").asDouble(), 0.001);
        assertEquals(0.0, card(data(adminB, "highlights"), "visits_today").get("secondaryValue").asDouble(), 0.001,
                "Tenant A's assigned shop must not count for Tenant B");
        assertEquals(0, data(adminB, "vouchers").get("totalElements").asLong());
        assertEquals(0, data(adminB, "sales").get("bySalesman").size());
        assertEquals(0, data(adminB, "filter-options").get("salesmen").size());
        assertEquals(250.0, card(data(token(a, "ADMIN"), "highlights"), "net_sales").get("value").asDouble(), 0.001);
    }

    @Test
    void emptyTenantGetsZerosAndFriendlyEmptyListsNeverErrors() throws Exception {
        String admin = token(tenant("EMPTY"), "ADMIN");
        JsonNode h = data(admin, "highlights");
        assertEquals(10, h.get("cards").size());
        for (JsonNode c : h.get("cards")) {
            assertEquals(0.0, c.get("value").asDouble(), 0.001, c.get("key").asText());
        }
        assertEquals(0, data(admin, "attention").get("alerts").size());
        JsonNode s = data(admin, "sales");
        assertEquals(30, s.get("daily").size());
        assertEquals(0, s.get("topProducts").size());
        assertEquals(0, data(admin, "vouchers").get("totalElements").asLong());
    }

    @Test
    void needsAttentionFlagsStaleShopsMissingStockOpenDaysAndHighReturns() throws Exception {
        Tenant t = tenant("ATT");
        Agent lmt = agent(t, "SALESMAN_LMT", "Lmt Salesman");
        Area area = area(t, "Area");
        CustomerShop visited = shop(t, area, lmt, "Visited Shop");
        CustomerShop stale = shop(t, area, lmt, "Stale Shop");
        Product p = product(t, "Bread");
        scan(t, lmt, visited, today.minusDays(1), ShopVisitStatus.SUCCESS);
        scan(t, lmt, stale, today.minusDays(5), ShopVisitStatus.SUCCESS);
        scan(t, lmt, stale, today, ShopVisitStatus.OUTSIDE_GEOFENCE);   // a refused attempt is not a visit
        // 40% returns at the visited shop (above the 15% default); no scan that day -> voucher without scan.
        voucher(t, lmt, visited, today.minusDays(2), null,
                new Line(p, TransactionType.SALE, 10, 500.0), new Line(p, TransactionType.RETURN, 4, 200.0));
        lmtStock(t, lmt, p, today.minusDays(2), LmtStockStatus.RECONCILED, 7);
        lmtStock(t, lmt, p, today.minusDays(3), LmtStockStatus.OPEN, 0);
        String admin = token(t, "ADMIN");

        JsonNode att = data(admin, "attention");
        JsonNode staleAlert = alert(att, "not_visited_3d");
        assertNotNull(staleAlert, att.toString());
        assertEquals(1, staleAlert.get("count").asInt(), "only the shop last visited 5 days ago");
        assertTrue(staleAlert.get("items").get(0).get("label").asText().startsWith("Stale Shop"));
        assertEquals("1 shop not visited for 3+ days", staleAlert.get("title").asText());

        assertEquals(7, alert(att, "lmt_missing").get("count").asInt());
        assertEquals("CRITICAL", alert(att, "lmt_missing").get("severity").asText());
        assertEquals(1, alert(att, "lmt_not_reconciled").get("count").asInt());
        assertEquals(1, alert(att, "off_location_today").get("count").asInt());
        JsonNode high = alert(att, "high_return");
        assertNotNull(high);
        assertTrue(high.get("items").toString().contains("Visited Shop"), high.toString());
        assertEquals(15.0, att.get("returnAlertPercent").asDouble(), 0.001);
        assertEquals("CRITICAL", att.get("alerts").get(0).get("severity").asText(), "most urgent first");

        JsonNode h = data(admin, "highlights");
        assertEquals(7.0, card(h, "lmt_missing").get("value").asDouble(), 0.001);
        assertEquals(1.0, card(h, "lmt_missing").get("secondaryValue").asDouble(), 0.001);
        assertEquals(1.0, card(h, "off_location").get("value").asDouble(), 0.001);
        assertEquals(1.0, card(h, "off_location").get("secondaryValue").asDouble(), 0.001, "voucher with no distance = location not checked");
        assertEquals(1.0, card(h, "without_scan").get("value").asDouble(), 0.001);
        assertEquals(2.0, card(h, "visits_today").get("secondaryValue").asDouble(), 0.001);
        assertEquals(0.0, card(h, "visits_today").get("value").asDouble(), 0.001);

        // The LOCAL segment has no LMT stock, so those alerts disappear.
        JsonNode local = data(admin, "attention?segment=LOCAL");
        assertNull(alert(local, "lmt_missing"));
        assertNull(alert(local, "lmt_not_reconciled"));
    }

    @Test
    void invalidFiltersAreRejectedWithAClearMessage() {
        String admin = token(tenant("BAD"), "ADMIN");
        assertEquals(HttpStatus.BAD_REQUEST, get(admin, "highlights?segment=WHOLESALE").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, get(admin, "sales?startDate=2026-10-30&endDate=2026-10-01").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, get(admin, "sales?startDate=2024-01-01&endDate=2026-01-01").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, get(admin, "attention?endDate=yesterday").getStatusCode());
    }
}
