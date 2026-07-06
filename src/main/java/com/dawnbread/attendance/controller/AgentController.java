package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.AgentDTO;
import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.AgentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private HttpServletRequest request;

    /**
     * Create a new agent. Requires an authenticated admin — SecurityInterceptor
     * validates the bearer token and attaches its role to the request before this
     * runs, so the role check below only trusts a role claim the server itself
     * verified, never the client-submitted request body. Same pattern as
     * AuthController.register().
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AgentDTO>> createAgent(@RequestBody Agent agent) {
        String callerRole = (String) request.getAttribute("role");
        if (!"ADMIN".equals(callerRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can create new accounts."));
        }
        try {
            Agent created = agentService.createAgent(agent);
            AgentDTO dto = convertToDTO(created);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Agent created successfully", dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    private static final String[] MANAGEMENT_ROLES = { "ADMIN", "HR", "SALES" };

    private <T> ResponseEntity<ApiResponse<T>> managementOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only Admin, HR, or Sales can browse the agent roster."));
    }

    /**
     * Get all agents. Confirmed callers: SalesAgentReportScreen,
     * HRAgentAttendanceReportScreen, ReportGeneratorScreen, and
     * AdminUsersScreen — all agent-picker UIs for Sales/HR/Admin only. A bare
     * Agent-role token has no legitimate reason to enumerate the whole roster.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentDTO>>> getAllAgents() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<Agent> agents = agentService.getAllAgents();
        List<AgentDTO> dtos = agents.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Agents retrieved successfully", dtos));
    }

    /**
     * Get agent by ID. Same roster-browsing rationale as getAllAgents().
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentDTO>> getAgentById(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        return agentService.getAgentById(id)
                .map(agent -> ResponseEntity.ok(ApiResponse.success("Agent found", convertToDTO(agent))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Agent not found with id: " + id)));
    }

    /**
     * Get agent by Agent ID. Same roster-browsing rationale as getAllAgents().
     */
    @GetMapping("/agentId/{agentId}")
    public ResponseEntity<ApiResponse<AgentDTO>> getAgentByAgentId(@PathVariable String agentId) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        return agentService.getAgentByAgentId(agentId)
                .map(agent -> ResponseEntity.ok(ApiResponse.success("Agent found", convertToDTO(agent))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Agent not found with agentId: " + agentId)));
    }

    /**
     * Update agent. Requires an authenticated admin — same pattern as
     * createAgent() above. This matters beyond the usual "don't let random
     * users edit other accounts" concern: AgentService.updateAgent() copies
     * agentDetails.getRole() straight onto the target record with no ownership
     * or role check of its own, so without this guard any authenticated caller
     * could PUT their own (or anyone's) id with {"role": "ADMIN"} and grant
     * themselves administrator access.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentDTO>> updateAgent(@PathVariable Long id, @RequestBody Agent agentDetails) {
        String callerRole = (String) request.getAttribute("role");
        if (!"ADMIN".equals(callerRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can update accounts."));
        }
        try {
            Agent updated = agentService.updateAgent(id, agentDetails);
            return ResponseEntity.ok(ApiResponse.success("Agent updated successfully", convertToDTO(updated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Delete agent. Requires an authenticated admin — same pattern as
     * createAgent() above.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAgent(@PathVariable Long id) {
        String callerRole = (String) request.getAttribute("role");
        if (!"ADMIN".equals(callerRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can delete accounts."));
        }
        try {
            agentService.deleteAgent(id);
            return ResponseEntity.ok(ApiResponse.success("Agent deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Search agents by name. Same roster-browsing rationale as getAllAgents().
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<AgentDTO>>> searchAgents(@RequestParam String name) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<Agent> agents = agentService.searchAgentsByName(name);
        List<AgentDTO> dtos = agents.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Agents found", dtos));
    }

    /**
     * Get active agents. Same roster-browsing rationale as getAllAgents().
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<AgentDTO>>> getActiveAgents() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<Agent> agents = agentService.getActiveAgents();
        List<AgentDTO> dtos = agents.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Active agents retrieved", dtos));
    }

    /**
     * Get agents checked in today. Same roster-browsing rationale as getAllAgents().
     */
    @GetMapping("/checked-in-today")
    public ResponseEntity<ApiResponse<List<AgentDTO>>> getAgentsCheckedInToday() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        List<Agent> agents = agentService.getAgentsCheckedInToday();
        List<AgentDTO> dtos = agents.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Agents checked in today", dtos));
    }

    /**
     * Count agents. Same roster-browsing rationale as getAllAgents().
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> countAgents() {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        long count = agentService.countAgents();
        return ResponseEntity.ok(ApiResponse.success("Total agents count", count));
    }

    // ===== Helper Methods =====
    private AgentDTO convertToDTO(Agent agent) {
        AgentDTO dto = new AgentDTO(
                agent.getId(),
                agent.getAgentId(),
                agent.getName(),
                agent.getEmail(),
                agent.getPhone(),
                agent.getRole(),
                agent.getDepartment(),
                agent.getCreatedAt(),
                agent.getFaceVerifyOnCheckIn(),
                agent.getFaceVerifyOnCheckOut(),
                agent.getFaceVerifyAnytime(),
                agent.getFaceRegistered(),
                agent.getFaceTemplate()
        );
        dto.setShiftStartTime(agent.getShiftStartTime());
        dto.setShiftEndTime(agent.getShiftEndTime());
        dto.setGracePeriodMinutes(agent.getGracePeriodMinutes());
        dto.setWorkingDays(agent.getWorkingDays());
        return dto;
    }
}
