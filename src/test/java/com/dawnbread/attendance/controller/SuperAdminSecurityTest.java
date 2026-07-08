package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.SuperAdmin;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.SuperAdminRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the Super Admin (Option B) design goal end-to-end via real HTTP:
 * it can fully manage tenants, but has ZERO access to any tenant's
 * operational data by default — not "returns an empty list", but an actual
 * 403, the same allowlist-by-role denial every other cross-role boundary in
 * this codebase uses. Also proves its login is a wholly separate endpoint
 * from the regular agent login, and that regular tenant tokens can't reach
 * Super Admin endpoints either.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private SuperAdminRepository superAdminRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private SuperAdmin seedSuperAdmin(String username, String rawPassword) {
        SuperAdmin sa = new SuperAdmin();
        sa.setUsername(username);
        sa.setPassword(passwordEncoder.encode(rawPassword));
        sa.setName("Seed " + username);
        sa.setEmail(username.toLowerCase() + "@platform.example.com");
        sa.setIsActive(true);
        sa.setCreatedAt(LocalDateTime.now());
        return superAdminRepository.save(sa);
    }

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    @Test
    void loginIsPublicAndSeparateFromRegularAgentLogin() {
        seedSuperAdmin("PLATFORM_OWNER", "SuperSecret123");

        Map<String, String> body = new HashMap<>();
        body.put("username", "PLATFORM_OWNER");
        body.put("password", "SuperSecret123");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // No Authorization header at all — must still reach the controller.
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/super-admin/login"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        assertTrue(response.getBody().contains("\"role\":\"SUPER_ADMIN\""), response.getBody());

        // Wrong password must be rejected same as any other login.
        Map<String, String> badBody = new HashMap<>();
        badBody.put("username", "PLATFORM_OWNER");
        badBody.put("password", "WrongPassword");
        ResponseEntity<String> badResponse = restTemplate.exchange(
                url("/api/super-admin/login"), HttpMethod.POST, new HttpEntity<>(badBody, headers), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, badResponse.getStatusCode());
    }

    @Test
    void superAdminCanFullyManageTenantsButHasNoAccessToOperationalData() {
        SuperAdmin sa = seedSuperAdmin("TENANT_MANAGER", "Password123");
        String superAdminToken = tokenProvider.generateSuperAdminToken(sa.getId(), sa.getUsername());

        // --- Create a tenant ---
        Map<String, Object> newTenant = new HashMap<>();
        newTenant.put("companyCode", "NEWCO");
        newTenant.put("name", "New Company Inc");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(superAdminToken);

        ResponseEntity<String> createResponse = restTemplate.exchange(
                url("/api/super-admin/tenants"), HttpMethod.POST, new HttpEntity<>(newTenant, headers), String.class);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode(), createResponse.getBody());
        assertTrue(createResponse.getBody().contains("NEWCO"));

        Tenant created = tenantRepository.findByCompanyCodeIgnoreCase("NEWCO").orElseThrow();

        // --- List tenants ---
        ResponseEntity<String> listResponse = restTemplate.exchange(
                url("/api/super-admin/tenants"), HttpMethod.GET, withToken(superAdminToken), String.class);
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertTrue(listResponse.getBody().contains("NEWCO"));

        // --- Suspend it ---
        ResponseEntity<String> suspendResponse = restTemplate.exchange(
                url("/api/super-admin/tenants/" + created.getId() + "/suspend"),
                HttpMethod.PUT, withToken(superAdminToken), String.class);
        assertEquals(HttpStatus.OK, suspendResponse.getStatusCode());
        assertTrue(suspendResponse.getBody().contains("\"isActive\":false"), suspendResponse.getBody());

        // --- Reactivate it ---
        ResponseEntity<String> activateResponse = restTemplate.exchange(
                url("/api/super-admin/tenants/" + created.getId() + "/activate"),
                HttpMethod.PUT, withToken(superAdminToken), String.class);
        assertEquals(HttpStatus.OK, activateResponse.getStatusCode());
        assertTrue(activateResponse.getBody().contains("\"isActive\":true"), activateResponse.getBody());

        // --- Zero access to operational data — a real 403, not an empty list ---
        ResponseEntity<String> agentsResponse = restTemplate.exchange(
                url("/api/agents"), HttpMethod.GET, withToken(superAdminToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, agentsResponse.getStatusCode(),
                "Super Admin must be denied, not just see an empty roster: " + agentsResponse.getBody());

        ResponseEntity<String> martsResponse = restTemplate.exchange(
                url("/api/marts"), HttpMethod.GET, withToken(superAdminToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, martsResponse.getStatusCode(),
                "Super Admin must be denied mart access too: " + martsResponse.getBody());

        ResponseEntity<String> attendanceResponse = restTemplate.exchange(
                url("/api/attendance"), HttpMethod.GET, withToken(superAdminToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, attendanceResponse.getStatusCode(),
                "Super Admin must be denied attendance access too: " + attendanceResponse.getBody());
    }

    @Test
    void regularTenantAdminCannotReachSuperAdminEndpoints() {
        // A plain tenant-scoped ADMIN token, minted the same way any other
        // test does, must not be able to touch tenant management at all.
        String regularAdminToken = tokenProvider.generateToken(999L, "SOME_ADMIN", "ADMIN", 1L);

        ResponseEntity<String> listResponse = restTemplate.exchange(
                url("/api/super-admin/tenants"), HttpMethod.GET, withToken(regularAdminToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, listResponse.getStatusCode(), listResponse.getBody());

        Map<String, Object> newTenant = new HashMap<>();
        newTenant.put("companyCode", "SHOULDFAIL");
        newTenant.put("name", "Should Not Be Created");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(regularAdminToken);
        ResponseEntity<String> createResponse = restTemplate.exchange(
                url("/api/super-admin/tenants"), HttpMethod.POST, new HttpEntity<>(newTenant, headers), String.class);
        assertEquals(HttpStatus.FORBIDDEN, createResponse.getStatusCode(), createResponse.getBody());
        assertTrue(tenantRepository.findByCompanyCodeIgnoreCase("SHOULDFAIL").isEmpty(),
                "A regular admin token must never be able to create a tenant");
    }
}
