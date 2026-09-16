package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * LMT flow refinement: the salesman must never see Sold, Returned, or
 * Missing — proves this at the actual HTTP-response level for
 * GET /lmt/stock/today, not just the service layer, since that's the
 * boundary a SALESMAN_LMT's own app actually receives.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LmtStockControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Long tenantId() {
        return tenantRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setCompanyCode("DAWNBREAD");
                    t.setName("Dawn Bread");
                    t.setIsActive(true);
                    t.setCreatedAt(LocalDateTime.now());
                    t.setCreatedBy("TEST");
                    return tenantRepository.save(t);
                })
                .getId();
    }

    private Agent seedLmt(String agentId) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole("SALESMAN_LMT");
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getTodayResponseData(String token, Long agentId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/lmt/stock/today?agentId=" + agentId), HttpMethod.GET, withToken(token), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        return (Map<String, Object>) body.get("data");
    }

    @Test
    void salesmanCallingTodayNeverSeesSoldReturnedOrMissing() {
        Agent lmt = seedLmt("LMTCTRL_SELF_1");
        String token = tokenProvider.generateToken(lmt.getId(), lmt.getAgentId(), "SALESMAN_LMT");

        Map<String, Object> morningBody = new HashMap<>();
        morningBody.put("agentId", lmt.getId());
        Map<String, Object> item = new HashMap<>();
        item.put("productId", firstActiveProductId(token));
        item.put("openingStock", 10);
        morningBody.put("items", List.of(item));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        restTemplate.exchange(url("/api/lmt/stock/morning"), HttpMethod.POST, new HttpEntity<>(morningBody, headers), String.class);

        Map<String, Object> data = getTodayResponseData(token, lmt.getId());
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertEquals(1, items.size());
        Map<String, Object> itemData = items.get(0);

        assertNull(itemData.get("soldQty"), "SALESMAN_LMT must never see soldQty from /today");
        assertNull(itemData.get("returnedQty"), "SALESMAN_LMT must never see returnedQty from /today");
        assertNull(itemData.get("missingQty"), "SALESMAN_LMT must never see missingQty from /today");
        assertNotNull(itemData.get("openingStock"), "Opening stock itself is plain data entry, not reconciliation, and must still be visible");
    }

    @Test
    void adminCallingTodayOnBehalfOfLmtStillSeesFullFigures() {
        Agent lmt = seedLmt("LMTCTRL_ADMIN_1");
        String lmtToken = tokenProvider.generateToken(lmt.getId(), lmt.getAgentId(), "SALESMAN_LMT");
        String adminToken = tokenProvider.generateToken(999L, "LMTCTRL_ADMIN", "ADMIN");

        Map<String, Object> morningBody = new HashMap<>();
        morningBody.put("agentId", lmt.getId());
        Map<String, Object> item = new HashMap<>();
        item.put("productId", firstActiveProductId(lmtToken));
        item.put("openingStock", 10);
        morningBody.put("items", List.of(item));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(lmtToken);
        restTemplate.exchange(url("/api/lmt/stock/morning"), HttpMethod.POST, new HttpEntity<>(morningBody, headers), String.class);

        Map<String, Object> data = getTodayResponseData(adminToken, lmt.getId());
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        Map<String, Object> itemData = items.get(0);

        assertNotNull(itemData.get("soldQty"), "An ADMIN calling on the LMT's behalf must still see the full reconciliation figures");
        assertNotNull(itemData.get("returnedQty"));
        assertNotNull(itemData.get("missingQty"));
    }

    @SuppressWarnings("unchecked")
    private Long firstActiveProductId(String token) {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/sales/products"), HttpMethod.GET, withToken(token), Map.class);
        List<Map<String, Object>> products = (List<Map<String, Object>>) response.getBody().get("data");
        return ((Number) products.get(0).get("id")).longValue();
    }
}
