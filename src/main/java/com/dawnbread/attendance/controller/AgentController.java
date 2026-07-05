package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.AgentDTO;
import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.service.AgentService;
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

    /**
     * Create a new agent
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AgentDTO>> createAgent(@RequestBody Agent agent) {
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

    /**
     * Get all agents
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentDTO>>> getAllAgents() {
        List<Agent> agents = agentService.getAllAgents();
        List<AgentDTO> dtos = agents.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Agents retrieved successfully", dtos));
    }

    /**
     * Get agent by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentDTO>> getAgentById(@PathVariable Long id) {
        return agentService.getAgentById(id)
                .map(agent -> ResponseEntity.ok(ApiResponse.success("Agent found", convertToDTO(agent))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Agent not found with id: " + id)));
    }

    /**
     * Get agent by Agent ID
     */
    @GetMapping("/agentId/{agentId}")
    public ResponseEntity<ApiResponse<AgentDTO>> getAgentByAgentId(@PathVariable String agentId) {
        return agentService.getAgentByAgentId(agentId)
                .map(agent -> ResponseEntity.ok(ApiResponse.success("Agent found", convertToDTO(agent))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Agent not found with agentId: " + agentId)));
    }

    /**
     * Update agent
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentDTO>> updateAgent(@PathVariable Long id, @RequestBody Agent agentDetails) {
        try {
            Agent updated = agentService.updateAgent(id, agentDetails);
            return ResponseEntity.ok(ApiResponse.success("Agent updated successfully", convertToDTO(updated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Delete agent
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAgent(@PathVariable Long id) {
        try {
            agentService.deleteAgent(id);
            return ResponseEntity.ok(ApiResponse.success("Agent deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Search agents by name
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<AgentDTO>>> searchAgents(@RequestParam String name) {
        List<Agent> agents = agentService.searchAgentsByName(name);
        List<AgentDTO> dtos = agents.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Agents found", dtos));
    }

    /**
     * Get active agents
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<AgentDTO>>> getActiveAgents() {
        List<Agent> agents = agentService.getActiveAgents();
        List<AgentDTO> dtos = agents.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Active agents retrieved", dtos));
    }

    /**
     * Get agents checked in today
     */
    @GetMapping("/checked-in-today")
    public ResponseEntity<ApiResponse<List<AgentDTO>>> getAgentsCheckedInToday() {
        List<Agent> agents = agentService.getAgentsCheckedInToday();
        List<AgentDTO> dtos = agents.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Agents checked in today", dtos));
    }

    /**
     * Count agents
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> countAgents() {
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
