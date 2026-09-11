package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.HierarchyPerson;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
import com.dawnbread.attendance.repository.HierarchyPersonRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LMT Stage 1 (Area, HierarchyPerson, CustomerShop, LmtSettings): reads stay
 * open to any authenticated role, writes are admin-only — same shape as
 * MartControllerSecurityTest. Also exercises the full throwaway
 * hierarchy-person -> area -> customer-shop chain end to end, standing in
 * for the manual throwaway-data test this stage otherwise requires.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LmtControllerSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private HierarchyPersonRepository hierarchyPersonRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private CustomerShopRepository customerShopRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HierarchyPerson seedPerson(String name) {
        HierarchyPerson p = new HierarchyPerson();
        p.setTenantId(TenantTestHelper.defaultTenantId(tenantRepository));
        p.setName(name);
        p.setCreatedAt(LocalDateTime.now());
        return hierarchyPersonRepository.save(p);
    }

    private Area seedArea(String name) {
        Area a = new Area();
        a.setTenantId(TenantTestHelper.defaultTenantId(tenantRepository));
        a.setName(name);
        a.setCreatedAt(LocalDateTime.now());
        return areaRepository.save(a);
    }

    private HttpEntity<Map<String, Object>> entityWithToken(Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }

    // ===== HierarchyPerson =====

    @Test
    void hierarchyPersonReadOpenWriteAdminOnly() {
        String agentToken = tokenProvider.generateToken(20L, "LMT_AGENT_READ", "AGENT");
        String adminToken = tokenProvider.generateToken(21L, "LMT_ADMIN", "ADMIN");

        HttpHeaders agentHeaders = new HttpHeaders();
        agentHeaders.setBearerAuth(agentToken);
        ResponseEntity<String> readResponse = restTemplate.exchange(
                url("/api/lmt/hierarchy-persons"), HttpMethod.GET, new HttpEntity<>(agentHeaders), String.class);
        assertEquals(HttpStatus.OK, readResponse.getStatusCode(), "Reads must stay open to any authenticated role");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Agent Attempted TSE");
        ResponseEntity<String> forbiddenCreate = restTemplate.exchange(
                url("/api/lmt/hierarchy-persons"), HttpMethod.POST, entityWithToken(body, agentToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenCreate.getStatusCode());

        ResponseEntity<String> okCreate = restTemplate.exchange(
                url("/api/lmt/hierarchy-persons"), HttpMethod.POST, entityWithToken(body, adminToken), String.class);
        assertEquals(HttpStatus.CREATED, okCreate.getStatusCode());
    }

    @Test
    void hierarchyPersonDeactivateReactivateRequireAdmin() {
        String adminToken = tokenProvider.generateToken(22L, "LMT_ADMIN2", "ADMIN");
        String salesToken = tokenProvider.generateToken(23L, "LMT_SALES", "SALES");
        HierarchyPerson person = seedPerson("Deactivate Target TSE");

        HttpHeaders salesHeaders = new HttpHeaders();
        salesHeaders.setBearerAuth(salesToken);
        ResponseEntity<String> forbiddenDelete = restTemplate.exchange(
                url("/api/lmt/hierarchy-persons/" + person.getId()), HttpMethod.DELETE, new HttpEntity<>(salesHeaders), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenDelete.getStatusCode());

        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        ResponseEntity<String> okDelete = restTemplate.exchange(
                url("/api/lmt/hierarchy-persons/" + person.getId()), HttpMethod.DELETE, new HttpEntity<>(adminHeaders), String.class);
        assertEquals(HttpStatus.OK, okDelete.getStatusCode());
        assertEquals(false, hierarchyPersonRepository.findById(person.getId()).orElseThrow().getIsActive());

        ResponseEntity<String> forbiddenReactivate = restTemplate.exchange(
                url("/api/lmt/hierarchy-persons/" + person.getId() + "/reactivate"), HttpMethod.PATCH,
                new HttpEntity<>(salesHeaders), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenReactivate.getStatusCode());

        ResponseEntity<String> okReactivate = restTemplate.exchange(
                url("/api/lmt/hierarchy-persons/" + person.getId() + "/reactivate"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), String.class);
        assertEquals(HttpStatus.OK, okReactivate.getStatusCode());
        assertTrue(hierarchyPersonRepository.findById(person.getId()).orElseThrow().getIsActive());
    }

    // ===== Area =====

    @Test
    void areaWritesRequireAdmin() {
        String adminToken = tokenProvider.generateToken(24L, "LMT_ADMIN3", "ADMIN");
        String salesToken = tokenProvider.generateToken(25L, "LMT_SALES2", "SALES");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Test Area North");
        ResponseEntity<String> forbiddenCreate = restTemplate.exchange(
                url("/api/lmt/areas"), HttpMethod.POST, entityWithToken(body, salesToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenCreate.getStatusCode());

        ResponseEntity<String> okCreate = restTemplate.exchange(
                url("/api/lmt/areas"), HttpMethod.POST, entityWithToken(body, adminToken), String.class);
        assertEquals(HttpStatus.CREATED, okCreate.getStatusCode());
        assertTrue(okCreate.getBody().contains("Test Area North"));
    }

    @Test
    void areaLinksToHierarchyPersonsCorrectly() {
        String adminToken = tokenProvider.generateToken(26L, "LMT_ADMIN4", "ADMIN");
        HierarchyPerson tse = seedPerson("Linked TSE");
        HierarchyPerson asm = seedPerson("Linked ASM");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Area With Hierarchy");
        body.put("tseId", tse.getId());
        body.put("asmId", asm.getId());
        ResponseEntity<String> createResponse = restTemplate.exchange(
                url("/api/lmt/areas"), HttpMethod.POST, entityWithToken(body, adminToken), String.class);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertTrue(createResponse.getBody().contains("Linked TSE"));
        assertTrue(createResponse.getBody().contains("Linked ASM"));
    }

    // ===== CustomerShop =====

    @Test
    void customerShopWritesRequireAdminAndShopCodeIsUniquePerTenant() {
        String adminToken = tokenProvider.generateToken(27L, "LMT_ADMIN5", "ADMIN");
        String salesToken = tokenProvider.generateToken(28L, "LMT_SALES3", "SALES");
        Area area = seedArea("Shop Test Area");

        Map<String, Object> body = new HashMap<>();
        body.put("shopCode", "SHOP-TEST-001");
        body.put("shopName", "Throwaway Test Shop");
        body.put("areaId", area.getId());
        body.put("latitude", 31.5);
        body.put("longitude", 74.3);
        body.put("radius", 100.0);

        ResponseEntity<String> forbiddenCreate = restTemplate.exchange(
                url("/api/lmt/customer-shops"), HttpMethod.POST, entityWithToken(body, salesToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenCreate.getStatusCode());

        ResponseEntity<String> okCreate = restTemplate.exchange(
                url("/api/lmt/customer-shops"), HttpMethod.POST, entityWithToken(body, adminToken), String.class);
        assertEquals(HttpStatus.CREATED, okCreate.getStatusCode());
        assertTrue(okCreate.getBody().contains("Shop Test Area"), "Response should embed the linked area");

        // Duplicate shop code rejected.
        ResponseEntity<String> duplicateCreate = restTemplate.exchange(
                url("/api/lmt/customer-shops"), HttpMethod.POST, entityWithToken(body, adminToken), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, duplicateCreate.getStatusCode());
    }

    @Test
    void customerShopLookupByCodeWorksForAutoFill() {
        String adminToken = tokenProvider.generateToken(29L, "LMT_ADMIN6", "ADMIN");
        Area area = seedArea("Lookup Test Area");

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(TenantTestHelper.defaultTenantId(tenantRepository));
        shop.setShopCode("SHOP-LOOKUP-001");
        shop.setShopName("Lookup Test Shop");
        shop.setArea(area);
        shop.setCreatedAt(LocalDateTime.now());
        customerShopRepository.save(shop);

        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/lmt/customer-shops/code/SHOP-LOOKUP-001"), HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Lookup Test Shop"));

        ResponseEntity<String> notFound = restTemplate.exchange(
                url("/api/lmt/customer-shops/code/DOES-NOT-EXIST"), HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
    }

    @Test
    void customerShopDeactivateIsSoftDelete() {
        String adminToken = tokenProvider.generateToken(30L, "LMT_ADMIN7", "ADMIN");
        Area area = seedArea("Deactivate Test Area");
        CustomerShop shop = new CustomerShop();
        shop.setTenantId(TenantTestHelper.defaultTenantId(tenantRepository));
        shop.setShopCode("SHOP-DEACTIVATE-001");
        shop.setShopName("Deactivate Test Shop");
        shop.setArea(area);
        shop.setCreatedAt(LocalDateTime.now());
        shop = customerShopRepository.save(shop);

        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        ResponseEntity<String> okDelete = restTemplate.exchange(
                url("/api/lmt/customer-shops/" + shop.getId()), HttpMethod.DELETE, new HttpEntity<>(adminHeaders), String.class);
        assertEquals(HttpStatus.OK, okDelete.getStatusCode());

        CustomerShop reloaded = customerShopRepository.findById(shop.getId()).orElseThrow();
        assertEquals(false, reloaded.getIsActive());
        assertTrue(customerShopRepository.existsById(shop.getId()), "Soft delete must not remove the row");
    }

    // ===== LmtSettings =====

    @Test
    void settingsReadOpenWriteAdminOnlyWithDefaultBuffer() {
        String agentToken = tokenProvider.generateToken(31L, "LMT_SETTINGS_AGENT", "AGENT");
        String adminToken = tokenProvider.generateToken(32L, "LMT_SETTINGS_ADMIN", "ADMIN");

        HttpHeaders agentHeaders = new HttpHeaders();
        agentHeaders.setBearerAuth(agentToken);
        ResponseEntity<String> readResponse = restTemplate.exchange(
                url("/api/lmt/settings"), HttpMethod.GET, new HttpEntity<>(agentHeaders), String.class);
        assertEquals(HttpStatus.OK, readResponse.getStatusCode());
        assertTrue(readResponse.getBody().contains("50.0") || readResponse.getBody().contains("geofenceBufferMeters"));

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("geofenceBufferMeters", 75.0);
        ResponseEntity<String> forbiddenUpdate = restTemplate.exchange(
                url("/api/lmt/settings"), HttpMethod.PUT, entityWithToken(updateBody, agentToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenUpdate.getStatusCode());

        ResponseEntity<String> okUpdate = restTemplate.exchange(
                url("/api/lmt/settings"), HttpMethod.PUT, entityWithToken(updateBody, adminToken), String.class);
        assertEquals(HttpStatus.OK, okUpdate.getStatusCode());
        assertTrue(okUpdate.getBody().contains("75.0"));
    }

    @Test
    void noTokenIsRejectedOnWriteEndpoints() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "No Auth Area");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/lmt/areas"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
