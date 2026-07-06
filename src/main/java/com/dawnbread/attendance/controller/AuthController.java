package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.entity.Agent;
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
     * Login agent
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> loginRequest) {
        String agentId = loginRequest.get("agentId");
        String password = loginRequest.get("password");
        String ip = request != null ? request.getRemoteAddr() : "0.0.0.0";
        
        if (agentId == null || password == null) {
            auditLogService.logAction("LOGIN", "UNKNOWN", "Missing credentials", ip, "FAILED");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("AgentId and password are required"));
        }
        
        Optional<Agent> agent = agentService.validateLogin(agentId, password);
        
        if (agent.isPresent()) {
            // Generate custom token
            String token = tokenProvider.generateToken(agent.get().getId(), agent.get().getAgentId(), agent.get().getRole());
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", agent.get().getId());
            response.put("agentId", agent.get().getAgentId());
            response.put("name", agent.get().getName());
            response.put("email", agent.get().getEmail());
            response.put("phone", agent.get().getPhone());
            response.put("role", agent.get().getRole());
            response.put("department", agent.get().getDepartment());
            response.put("token", token);
            response.put("message", "Login successful");
            
            auditLogService.logAction("LOGIN", agentId, "Login successful", ip, "SUCCESS");
            
            return ResponseEntity.ok(ApiResponse.success("Login successful", response));
        } else {
            auditLogService.logAction("LOGIN", agentId, "Invalid agentId or password or user is inactive", ip, "FAILED");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid agentId or password"));
        }
    }

    /**
     * Check if agent exists
     */
    @GetMapping("/exists/{agentId}")
    public ResponseEntity<ApiResponse<Boolean>> checkAgentExists(@PathVariable String agentId) {
        boolean exists = agentService.existsByAgentId(agentId);
        return ResponseEntity.ok(ApiResponse.success("Agent exists check", exists));
    }
}
