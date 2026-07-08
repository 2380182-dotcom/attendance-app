package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TokenProvider;
import com.dawnbread.attendance.service.AgentService;
import com.dawnbread.attendance.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private HttpServletRequest request;

    /**
     * Register a new agent. Requires an authenticated admin — SecurityInterceptor
     * validates the bearer token and attaches its role to the request before this
     * runs, so the role check below only trusts a role claim the server itself
     * verified, never the client-submitted request body.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@RequestBody Agent agent) {
        String callerRole = (String) request.getAttribute("role");
        if (!"ADMIN".equals(callerRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can register new accounts."));
        }
        try {
            Agent created = agentService.createAgent(agent);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", created.getId());
            response.put("agentId", created.getAgentId());
            response.put("name", created.getName());
            response.put("email", created.getEmail());
            response.put("role", created.getRole());
            response.put("department", created.getDepartment());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Registration successful", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Login agent. Company Code identifies the tenant first — agent_id is
     * only unique per-tenant (V14), so the same agentId/password pair
     * resolves to a completely different (or no) account depending on which
     * company's login this is. A right agentId/password under the wrong
     * company code must fail exactly like a wrong password: AgentService
     * looks the agent up scoped to the resolved tenant, so it simply won't
     * find a match.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> loginRequest) {
        String companyCode = loginRequest.get("companyCode");
        String agentId = loginRequest.get("agentId");
        String password = loginRequest.get("password");
        String ip = request != null ? request.getRemoteAddr() : "0.0.0.0";

        if (companyCode == null || agentId == null || password == null) {
            auditLogService.logAction("LOGIN", "UNKNOWN", "Missing credentials", ip, "FAILED");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Company code, agentId, and password are required"));
        }

        Optional<Tenant> tenantOpt = tenantRepository.findByCompanyCodeIgnoreCase(companyCode);
        if (tenantOpt.isEmpty() || !Boolean.TRUE.equals(tenantOpt.get().getIsActive())) {
            // Deliberately the same generic message as a wrong agentId/password
            // below — doesn't confirm or deny whether a company code exists.
            auditLogService.logAction("LOGIN", agentId, "Unknown or inactive company code: " + companyCode, ip, "FAILED");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid company code, agentId, or password"));
        }
        Tenant tenant = tenantOpt.get();

        Optional<Agent> agent = agentService.validateLogin(tenant.getId(), agentId, password);

        if (agent.isPresent()) {
            String token = tokenProvider.generateToken(agent.get().getId(), agent.get().getAgentId(), agent.get().getRole(), tenant.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("id", agent.get().getId());
            response.put("agentId", agent.get().getAgentId());
            response.put("name", agent.get().getName());
            response.put("email", agent.get().getEmail());
            response.put("phone", agent.get().getPhone());
            response.put("role", agent.get().getRole());
            response.put("department", agent.get().getDepartment());
            response.put("token", token);
            response.put("companyCode", tenant.getCompanyCode());
            response.put("tenantName", tenant.getName());
            response.put("message", "Login successful");

            auditLogService.logAction("LOGIN", agentId, "Login successful", ip, "SUCCESS", tenant.getId());

            return ResponseEntity.ok(ApiResponse.success("Login successful", response));
        } else {
            auditLogService.logAction("LOGIN", agentId, "Invalid agentId or password or user is inactive", ip, "FAILED", tenant.getId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid company code, agentId, or password"));
        }
    }

}
