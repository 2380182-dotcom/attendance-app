package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.*;
import com.dawnbread.attendance.repository.*;
import com.dawnbread.attendance.security.TokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sales > Routes and the Sales cockpit's "Shop visits today" / "Not visited
 * today" must ALWAYS show the same numbers: same visited rule, same due days
 * (each salesman's working days, minus company holidays) and shops of an
 * inactive salesman under Unassigned, not assigned. The cockpit's "Coverage
 * % per day" chart follows the same rule day by day. "Today" is set with a
 * settable clock so a weekday, a Sunday and a holiday are each tested. Each
 * case seeds its own company (no cached answer from another case).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Its own in-memory database: this class's context (it has its own clock) recreates the schema on start, which
// would otherwise reset the shared test database's ids under other classes' cached cockpit answers.
@org.springframework.test.context.TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:routes_cockpit_match;DB_CLOSE_DELAY=-1;MODE=MySQL")
class RoutesCockpitMatchTest {

    private static final ZoneId PK = ZoneId.of("Asia/Karachi");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 15);
    private static final LocalDate TUESDAY_HOLIDAY = LocalDate.of(2026, 11, 17);
    private static final List<String> MON_SAT = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT");
    private static final List<String> TUE_SAT = List.of("TUE", "WED", "THU", "FRI", "SAT");

    /** A clock the test moves to the day it needs (Pakistan time, 1 PM). */
    static final class SettableClock extends Clock {
        private volatile Instant now = MONDAY.atTime(13, 0).atZone(PK).toInstant();

        void setDay(LocalDate day) {
            now = day.atTime(13, 0).atZone(PK).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return PK;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        SettableClock settableClock() {
            return new SettableClock();
        }
    }

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private TokenProvider tokenProvider;
    @Autowired
    private SettableClock clock;
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

    private final ObjectMapper mapper = new ObjectMapper();

    // ------------------------------------------------------------------ seeding

    private Tenant tenant(String label) {
        Tenant t = new Tenant();
        t.setCompanyCode("RM" + label + (System.nanoTime() % 1_000_000));
        t.setName("Routes match " + label);
        t.setIsActive(true);
        t.setCreatedAt(LocalDateTime.now());
        t.setCreatedBy("TEST");
        return tenantRepository.save(t);
    }

    private Agent salesman(Tenant t, String name, List<String> workingDays, boolean active) {
        Agent a = new Agent();
        a.setTenantId(t.getId());
        String id = "RM" + System.nanoTime();
        a.setAgentId(id);
        a.setName(name);
        a.setEmail(id.toLowerCase() + "@example.com");
        a.setRole("SALESMAN_LOCAL");
        a.setWorkingDays(workingDays);
        a.setCreatedAt(LocalDateTime.now());
        a.setIsActive(active);
        return agentRepository.save(a);
    }

    private CustomerShop shop(Tenant t, Agent assigned, String name) {
        Area area = new Area();
        area.setTenantId(t.getId());
        area.setName("Area " + System.nanoTime());
        area.setIsActive(true);
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);
        CustomerShop s = new CustomerShop();
        s.setTenantId(t.getId());
        s.setShopCode("RM-" + System.nanoTime());
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

    private void scan(Tenant t, Agent agent, CustomerShop shop, LocalDate date) {
        ShopVisitScan s = new ShopVisitScan();
        s.setTenantId(t.getId());
        s.setAgentId(agent.getId());
        s.setCustomerShop(shop);
        s.setScannedCode(shop.getShopCode());
        s.setScanDate(date);
        s.setScanTime(LocalTime.of(10, 0));
        s.setGpsLatitude(0.0);
        s.setGpsLongitude(0.0);
        s.setGeofenceStatus(GeofenceStatus.INSIDE);
        s.setVisitStatus(ShopVisitStatus.SUCCESS);
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

    // ------------------------------------------------------------------ http

    private JsonNode data(Tenant t, String path) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.generateToken(999L, "RM_ADMIN", "ADMIN", t.getId()));
        ResponseEntity<String> r = restTemplate.exchange("http://localhost:" + port + "/api/" + path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), path + " -> " + r.getBody());
        return mapper.readTree(r.getBody()).get("data");
    }

    private static JsonNode card(JsonNode highlights, String key) {
        for (JsonNode c : highlights.get("cards")) {
            if (key.equals(c.get("key").asText())) return c;
        }
        throw new AssertionError("no card " + key);
    }

    /** Routes for today vs the cockpit's two "today" cards: equal, and the expected numbers. */
    private JsonNode assertSameNumbers(Tenant t, int visited, int notVisited, int due) throws Exception {
        JsonNode routes = data(t, "routes/summary").get("total");
        JsonNode cockpit = data(t, "cockpit/sales/highlights");
        JsonNode visits = card(cockpit, "visits_today");
        JsonNode missed = card(cockpit, "not_visited_today");
        assertEquals(routes.get("visited").asInt(), visits.get("value").asInt(), "visited: Routes vs cockpit");
        assertEquals(routes.get("notVisited").asInt(), missed.get("value").asInt(), "not visited: Routes vs cockpit");
        assertEquals(routes.get("due").asInt(), visits.get("secondaryValue").asInt(), "due today: Routes vs cockpit");
        assertEquals(visited, visits.get("value").asInt());
        assertEquals(notVisited, missed.get("value").asInt());
        assertEquals(due, routes.get("due").asInt());
        return cockpit;
    }

    // ------------------------------------------------------------------ tests

    @Test
    void aWeekdayWithAnInactiveSalesmanAndASalesmanOnHisDayOff() throws Exception {
        clock.setDay(MONDAY);
        Tenant t = tenant("WEEK");
        Agent ali = salesman(t, "Ali", MON_SAT, true);
        Agent offMonday = salesman(t, "Off Monday", TUE_SAT, true);
        Agent gone = salesman(t, "Gone", MON_SAT, false);
        CustomerShop a1 = shop(t, ali, "Ali scanned");
        CustomerShop a2 = shop(t, ali, "Ali feedback");
        shop(t, ali, "Ali not visited");
        CustomerShop o1 = shop(t, offMonday, "Day off but scanned");
        shop(t, offMonday, "Day off");
        CustomerShop g1 = shop(t, gone, "Inactive salesman's shop");
        scan(t, ali, a1, MONDAY);
        feedback(t, ali, a2, MONDAY);
        scan(t, offMonday, o1, MONDAY);   // not a due day for him: does not count
        scan(t, gone, g1, MONDAY);        // his shop is unassigned: not in the assigned numbers

        assertSameNumbers(t, 2, 1, 3);
        JsonNode routes = data(t, "routes/summary");
        assertEquals(5, routes.get("total").get("assignedShops").asInt(), "inactive salesman's shop not assigned");
        assertEquals(1, routes.get("unassigned").get("salesmanInactive").asInt());
    }

    @Test
    void aSundayIsADayOffInBoth() throws Exception {
        clock.setDay(SUNDAY);
        Tenant t = tenant("SUN");
        Agent ali = salesman(t, "Ali", MON_SAT, true);
        CustomerShop a1 = shop(t, ali, "Sunday visit");
        shop(t, ali, "Sunday no visit");
        scan(t, ali, a1, SUNDAY);

        JsonNode cockpit = assertSameNumbers(t, 0, 0, 0);
        assertTrue(card(cockpit, "visits_today").get("context").asText().startsWith("Day off"));
        assertTrue(card(cockpit, "not_visited_today").get("context").asText().startsWith("Day off"));
        assertTrue(data(t, "routes/summary").get("total").get("dayOff").asBoolean());
    }

    @Test
    void aCompanyHolidayHasNothingDueInBoth() throws Exception {
        clock.setDay(TUESDAY_HOLIDAY);
        Tenant t = tenant("HOL");
        CompanyHoliday h = new CompanyHoliday();
        h.setTenantId(t.getId());
        h.setHolidayDate(TUESDAY_HOLIDAY);
        h.setName("Test Day");
        h.setCreatedAt(LocalDateTime.now());
        companyHolidayRepository.save(h);
        Agent ali = salesman(t, "Ali", MON_SAT, true);
        CustomerShop a1 = shop(t, ali, "Holiday visit");
        shop(t, ali, "Holiday no visit");
        scan(t, ali, a1, TUESDAY_HOLIDAY);

        JsonNode cockpit = assertSameNumbers(t, 0, 0, 0);
        assertEquals("Company holiday: Test Day · no shops are due today", card(cockpit, "not_visited_today").get("context").asText());
        // The day before (Monday) was a normal day for the sparkline: 2 due, none visited.
        JsonNode spark = card(cockpit, "not_visited_today").get("sparkline");
        assertEquals(2, spark.get(spark.size() - 2).get("value").asInt());
    }

    @Test
    void theCoverageTrendMatchesRoutesDayByDayAndSkipsSundaysAndHolidays() throws Exception {
        clock.setDay(TUESDAY_HOLIDAY);
        Tenant t = tenant("TREND");
        CompanyHoliday h = new CompanyHoliday();
        h.setTenantId(t.getId());
        h.setHolidayDate(TUESDAY_HOLIDAY);
        h.setName("Test Day");
        h.setCreatedAt(LocalDateTime.now());
        companyHolidayRepository.save(h);
        Agent ali = salesman(t, "Ali", MON_SAT, true);
        Agent offMonday = salesman(t, "Off Monday", TUE_SAT, true);
        Agent gone = salesman(t, "Gone", MON_SAT, false);
        CustomerShop a1 = shop(t, ali, "A1");
        CustomerShop a2 = shop(t, ali, "A2");
        CustomerShop o1 = shop(t, offMonday, "O1");
        CustomerShop g1 = shop(t, gone, "G1");
        LocalDate saturday = SUNDAY.minusDays(1);
        for (LocalDate d = saturday; !d.isAfter(TUESDAY_HOLIDAY); d = d.plusDays(1)) {
            scan(t, ali, a1, d);      // every day, including Sunday and the holiday
            scan(t, offMonday, o1, d);
            scan(t, gone, g1, d);
        }
        feedback(t, ali, a2, saturday);

        JsonNode trend = data(t, "cockpit/sales/coverage?startDate=" + saturday + "&endDate=" + TUESDAY_HOLIDAY).get("trend");
        assertEquals(4, trend.size());
        for (JsonNode day : trend) {
            LocalDate d = LocalDate.parse(day.get("date").asText());
            JsonNode routes = data(t, "routes/summary?startDate=" + d + "&endDate=" + d).get("total");
            assertEquals(routes.get("visited").asInt(), day.get("visitedShops").asInt(), d + " visited: Routes vs cockpit");
            assertEquals(routes.get("due").asInt(), day.get("dueShops").asInt(), d + " due: Routes vs cockpit");
            if (routes.get("due").asInt() == 0) {
                assertTrue(day.get("coveragePercent").isNull(), d + ": a day with nothing due is not 0%");
            } else {
                assertEquals(routes.get("coveragePercent").asDouble(), day.get("coveragePercent").asDouble(), 0.05, d + " coverage %");
            }
        }
        // Saturday: Ali 2 + Off Monday 1 due, all visited. Sunday: day off. Monday: Ali's 2 due (1 visited),
        // Off Monday is off. Tuesday: holiday. The inactive salesman's shop is never counted.
        assertEquals(List.of("3/3", "Day off", "1/2", "Company holiday: Test Day"), trendText(trend));
    }

    private static List<String> trendText(JsonNode trend) {
        List<String> out = new java.util.ArrayList<>();
        for (JsonNode d : trend) {
            out.add(d.get("dayOff").isNull() ? d.get("visitedShops").asInt() + "/" + d.get("dueShops").asInt() : d.get("dayOff").asText());
        }
        return out;
    }
}
