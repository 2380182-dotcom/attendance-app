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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Official price-list order (V39): every product list follows the list's
 * SR No. (sortOrder), products without one come after it by id, never by
 * name; families are the apps' filter buttons. Every test seeds its OWN
 * company, so the lists hold exactly the products seeded here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductOrderTest {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private TokenProvider tokenProvider;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductFamilyRepository productFamilyRepository;
    @Autowired
    private AgentRepository agentRepository;
    @Autowired
    private SalesRecordRepository salesRecordRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    // ------------------------------------------------------------------ seeding

    private Tenant tenant(String label) {
        Tenant t = new Tenant();
        t.setCompanyCode("PO" + label + (System.nanoTime() % 1_000_000));
        t.setName("Product order " + label);
        t.setIsActive(true);
        t.setCreatedAt(LocalDateTime.now());
        t.setCreatedBy("TEST");
        return tenantRepository.save(t);
    }

    private ProductFamily family(Tenant t, String name, int order) {
        ProductFamily f = new ProductFamily();
        f.setTenantId(t.getId());
        f.setName(name);
        f.setSortOrder(order);
        f.setCreatedAt(LocalDateTime.now());
        return productFamilyRepository.save(f);
    }

    /** Names chosen so that name order would be wrong: A-names sit late on the list. */
    private Product product(Tenant t, String name, Integer sortOrder, ProductFamily family, boolean active) {
        Product p = new Product();
        p.setTenantId(t.getId());
        p.setName(name);
        p.setAgentPrice(50.0);
        p.setSalesmanPrice(55.0);
        p.setIsActive(active);
        p.setSortOrder(sortOrder);
        p.setFamilyId(family == null ? null : family.getId());
        p.setOnPriceList(sortOrder != null && sortOrder <= 59);
        p.setCreatedAt(LocalDateTime.now());
        return productRepository.save(p);
    }

    private Agent salesman(Tenant t, String role) {
        Agent a = new Agent();
        a.setTenantId(t.getId());
        String id = "PO" + System.nanoTime();
        a.setAgentId(id);
        a.setName("Order " + role);
        a.setEmail(id.toLowerCase() + "@example.com");
        a.setRole(role);
        a.setIsActive(true);
        a.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(a);
    }

    // ------------------------------------------------------------------ http

    private String token(Tenant t, String role) {
        return tokenProvider.generateToken(999L, "PO_" + role, role, t.getId());
    }

    private ResponseEntity<String> call(HttpMethod method, String token, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("http://localhost:" + port + "/api/" + path, method, new HttpEntity<>(body, headers),
                String.class);
    }

    private JsonNode data(HttpMethod method, String token, String path, Object body) throws Exception {
        ResponseEntity<String> r = call(method, token, path, body);
        assertEquals(HttpStatus.OK, r.getStatusCode(), path + " -> " + r.getBody());
        return mapper.readTree(r.getBody()).get("data");
    }

    private static List<String> names(JsonNode rows, String field) {
        List<String> out = new ArrayList<>();
        for (JsonNode r : rows) out.add(r.get(field).asText());
        return out;
    }

    /** Three listed products seeded out of order, one unplaced, one hidden. */
    private record Seed(Tenant t, ProductFamily bread, ProductFamily rusk, Product large, Product small, Product crumbs,
                        Product unplaced, Product hidden) {
    }

    private Seed seed(String label) {
        Tenant t = tenant(label);
        ProductFamily rusk = family(t, "Rusk", 7);
        ProductFamily bread = family(t, "Bread", 1);
        Product unplaced = product(t, "AAA Unplaced", null, null, true);
        Product crumbs = product(t, "Aa Crumbs", 52, rusk, true);
        Product small = product(t, "Small Bread", 3, bread, true);
        Product large = product(t, "Large Bread", 1, bread, true);
        Product hidden = product(t, "Hidden Bread", 2, bread, false);
        return new Seed(t, bread, rusk, large, small, crumbs, unplaced, hidden);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void theAppsGetSwitchedOnProductsInListOrderWithFamiliesNeverByName() throws Exception {
        Seed s = seed("APP");
        String salesman = token(s.t(), "SALESMAN_LOCAL");

        JsonNode products = data(HttpMethod.GET, salesman, "sales/products-with-images", null);
        assertEquals(List.of("Large Bread", "Small Bread", "Aa Crumbs", "AAA Unplaced"), names(products, "name"));
        assertEquals(s.bread().getId(), products.get(0).get("familyId").asLong());
        assertEquals(1, products.get(0).get("sortOrder").asInt());
        assertTrue(products.get(0).get("onPriceList").asBoolean());
        assertTrue(products.get(3).get("familyId").isNull(), "an unplaced product has no family");

        assertEquals(List.of("Large Bread", "Small Bread", "Aa Crumbs", "AAA Unplaced"),
                names(data(HttpMethod.GET, salesman, "sales/products", null), "name"), "the catalog too");

        JsonNode families = data(HttpMethod.GET, salesman, "sales/product-families", null);
        assertEquals(List.of("Bread", "Rusk"), names(families, "name"), "button order, not the order they were added");
    }

    @Test
    void thePricingListIsInListOrderWithHiddenProductsAndPlacement() throws Exception {
        Seed s = seed("PRICE");
        JsonNode rows = data(HttpMethod.GET, token(s.t(), "SALES"), "products/pricing", null);
        assertEquals(List.of("Large Bread", "Hidden Bread", "Small Bread", "Aa Crumbs", "AAA Unplaced"), names(rows, "name"));
        assertEquals(52, rows.get(3).get("sortOrder").asInt());
        assertEquals(s.rusk().getId(), rows.get(3).get("familyId").asLong());
        assertTrue(rows.get(4).get("sortOrder").isNull());
    }

    @Test
    void onlyAnAdminPlacesProductsAndManagesFamilies() throws Exception {
        Seed s = seed("ADMIN");
        Map<String, Object> placement = new HashMap<>();
        placement.put("familyId", s.bread().getId());
        placement.put("sortOrder", 2);
        placement.put("onPriceList", true);
        Map<String, Object> fam = new HashMap<>(Map.of("name", "Frozen", "sortOrder", 8));
        for (String role : List.of("SALES", "HR", "SALESMAN_LOCAL", "SALESMAN_LMT", "AGENT")) {
            String t = token(s.t(), role);
            assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.PUT, t, "products/" + s.unplaced().getId() + "/placement", placement).getStatusCode(), role);
            assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.POST, t, "products/families", fam).getStatusCode(), role);
            assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.DELETE, t, "products/families/" + s.rusk().getId(), null).getStatusCode(), role);
        }

        String admin = token(s.t(), "ADMIN");
        JsonNode placed = data(HttpMethod.PUT, admin, "products/" + s.unplaced().getId() + "/placement", placement);
        assertEquals(2, placed.get("sortOrder").asInt());
        JsonNode list = data(HttpMethod.GET, admin, "products/pricing", null);
        assertEquals("AAA Unplaced", list.get(1).get("name").asText(), "placed at 2, next to the hidden product (tie by id)");

        // A family of another company, a bad order number and a bad body are refused.
        Tenant other = tenant("OTHER");
        ProductFamily foreign = family(other, "Bread", 1);
        placement.put("familyId", foreign.getId());
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.PUT, admin, "products/" + s.unplaced().getId() + "/placement", placement).getStatusCode());
        placement.put("familyId", null);
        placement.put("sortOrder", 0);
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.PUT, admin, "products/" + s.unplaced().getId() + "/placement", placement).getStatusCode());
        placement.put("sortOrder", null);
        placement.remove("onPriceList");
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.PUT, admin, "products/" + s.unplaced().getId() + "/placement", placement).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.PUT, token(other, "ADMIN"), "products/" + s.large().getId() + "/placement",
                Map.of("onPriceList", true)).getStatusCode(), "another company's product");

        // Families: add (Urdu optional), rename, duplicates refused, only an empty one can be deleted.
        fam.put("nameUr", "منجمد");
        JsonNode frozen = data(HttpMethod.POST, admin, "products/families", fam);
        assertEquals("منجمد", frozen.get("nameUr").asText());
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.POST, admin, "products/families", Map.of("name", "bread", "sortOrder", 9)).getStatusCode());
        JsonNode renamed = data(HttpMethod.PUT, admin, "products/families/" + frozen.get("id").asLong(), Map.of("name", "Frozen Food", "sortOrder", 8));
        assertEquals("Frozen Food", renamed.get("name").asText());
        assertTrue(renamed.get("nameUr").isNull(), "an empty Urdu name clears it");
        ResponseEntity<String> inUse = call(HttpMethod.DELETE, admin, "products/families/" + s.rusk().getId(), null);
        assertEquals(HttpStatus.BAD_REQUEST, inUse.getStatusCode());
        assertTrue(inUse.getBody().contains("still has 1 product"), inUse.getBody());
        assertEquals(HttpStatus.OK, call(HttpMethod.DELETE, admin, "products/families/" + frozen.get("id").asLong(), null).getStatusCode());
        assertEquals(List.of("Bread", "Rusk"), names(data(HttpMethod.GET, admin, "sales/product-families", null), "name"));
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.PUT, admin, "products/families/" + foreign.getId(),
                Map.of("name", "Mine", "sortOrder", 1)).getStatusCode(), "another company's family");
    }

    @Test
    void lmtStockLinesAndVoucherLinesFollowTheListOrder() throws Exception {
        Seed s = seed("LINES");
        Agent lmt = salesman(s.t(), "SALESMAN_LMT");
        String lmtToken = tokenProvider.generateToken(lmt.getId(), lmt.getAgentId(), "SALESMAN_LMT", s.t().getId());

        // Morning stock entered in name order; read back in list order.
        List<Map<String, Object>> items = new ArrayList<>();
        for (Product p : List.of(s.crumbs(), s.large(), s.small())) {
            items.add(Map.of("productId", p.getId(), "openingStock", 5));
        }
        ResponseEntity<String> morning = call(HttpMethod.POST, lmtToken, "lmt/stock/morning", Map.of("agentId", lmt.getId(), "items", items));
        assertTrue(morning.getStatusCode().is2xxSuccessful(), morning.getBody());
        JsonNode today = data(HttpMethod.GET, lmtToken, "lmt/stock/today?agentId=" + lmt.getId(), null);
        assertEquals(List.of("Large Bread", "Small Bread", "Aa Crumbs"), names(today.get("items"), "productName"));

        // A voucher saved with its lines out of order shows them in list order.
        SalesRecord r = new SalesRecord();
        r.setTenantId(s.t().getId());
        r.setAgent(lmt);
        r.setStoreName("Order shop");
        r.setSaleDate(LocalDate.now(ZoneId.of("Asia/Karachi")));
        r.setSaleTime(LocalTime.of(10, 0));
        r.setSubmittedAt(LocalDateTime.now());
        r.setTotalAmount(0.0);
        r.setTotalUnits(0);
        r.setStatus("PENDING");
        r.setCreatedAt(LocalDateTime.now());
        r.setAgentRoleAtSale(lmt.getRole());
        for (Product p : List.of(s.unplaced(), s.crumbs(), s.large())) {
            SaleItem i = new SaleItem();
            i.setTenantId(s.t().getId());
            i.setSalesRecord(r);
            i.setAgentId(lmt.getId());
            i.setSaleDate(r.getSaleDate());
            i.setTransactionType(TransactionType.SALE);
            i.setProduct(p);
            i.setQuantity(1);
            i.setUnitPrice(50.0);
            i.setTotalPrice(50.0);
            r.getItems().add(i);
        }
        r = salesRecordRepository.save(r);
        JsonNode voucher = data(HttpMethod.GET, token(s.t(), "ADMIN"), "sales/vouchers/" + r.getId(), null);
        assertEquals(List.of("Large Bread", "Aa Crumbs", "AAA Unplaced"), names(voucher.get("items"), "productName"));
    }
}
