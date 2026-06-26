package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.security.TokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AgentService {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private HttpServletRequest request;

    private String getCurrentUser() {
        if (request != null) {
            TokenProvider.Claims claims = (TokenProvider.Claims) request.getAttribute("userClaims");
            if (claims != null) {
                return claims.getAgentId();
            }
        }
        return "SYSTEM";
    }

    private String getClientIp() {
        return request != null ? request.getRemoteAddr() : "0.0.0.0";
    }

    public Agent createAgent(Agent agent) {
        String currentUser = getCurrentUser();
        String ip = getClientIp();
        if (agentRepository.existsByAgentId(agent.getAgentId())) {
            auditLogService.logAction("CREATE_USER", currentUser, "Failed to create user: Agent ID already exists: " + agent.getAgentId(), ip, "FAILED");
            throw new RuntimeException("Agent ID already exists: " + agent.getAgentId());
        }
        if (agentRepository.existsByEmail(agent.getEmail())) {
            auditLogService.logAction("CREATE_USER", currentUser, "Failed to create user: Email already registered: " + agent.getEmail(), ip, "FAILED");
            throw new RuntimeException("Email already registered: " + agent.getEmail());
        }
        agent.setCreatedAt(LocalDateTime.now());
        agent.setCreatedBy(currentUser);
        agent.setIsActive(true);
        Agent created = agentRepository.save(agent);
        auditLogService.logAction("CREATE_USER", currentUser, "Created user: " + created.getAgentId() + " (" + created.getRole() + ")", ip, "SUCCESS");
        return created;
    }

    public List<Agent> getAllAgents() {
        return agentRepository.findAll();
    }

    public Optional<Agent> getAgentById(Long id) {
        return agentRepository.findById(id);
    }

    public Optional<Agent> getAgentByAgentId(String agentId) {
        return agentRepository.findByAgentId(agentId);
    }

    public Optional<Agent> getAgentByEmail(String email) {
        return agentRepository.findByEmail(email);
    }

    public List<Agent> searchAgentsByName(String name) {
        return agentRepository.findByNameContainingIgnoreCase(name);
    }

    public Agent updateAgent(Long id, Agent agentDetails) {
        String currentUser = getCurrentUser();
        String ip = getClientIp();
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> {
                    auditLogService.logAction("UPDATE_USER", currentUser, "Failed to update user: Not found with id " + id, ip, "FAILED");
                    return new RuntimeException("Agent not found with id: " + id);
                });
        
        if (agentDetails.getName() != null) {
            agent.setName(agentDetails.getName());
        }
        if (agentDetails.getEmail() != null) {
            Optional<Agent> existingAgent = agentRepository.findByEmail(agentDetails.getEmail());
            if (existingAgent.isPresent() && !existingAgent.get().getId().equals(id)) {
                auditLogService.logAction("UPDATE_USER", currentUser, "Failed to update user: Email already taken: " + agentDetails.getEmail(), ip, "FAILED");
                throw new RuntimeException("Email already taken: " + agentDetails.getEmail());
            }
            agent.setEmail(agentDetails.getEmail());
        }
        if (agentDetails.getPhone() != null) {
            agent.setPhone(agentDetails.getPhone());
        }
        if (agentDetails.getPassword() != null) {
            agent.setPassword(agentDetails.getPassword());
        }
        if (agentDetails.getRole() != null) {
            agent.setRole(agentDetails.getRole());
        }
        if (agentDetails.getDepartment() != null) {
            agent.setDepartment(agentDetails.getDepartment());
        }
        if (agentDetails.getIsActive() != null) {
            agent.setIsActive(agentDetails.getIsActive());
        }
        
        Agent updated = agentRepository.save(agent);
        auditLogService.logAction("UPDATE_USER", currentUser, "Updated user: " + updated.getAgentId(), ip, "SUCCESS");
        return updated;
    }

    public void deleteAgent(Long id) {
        String currentUser = getCurrentUser();
        String ip = getClientIp();
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> {
                    auditLogService.logAction("DELETE_USER", currentUser, "Failed to delete user: Not found with id " + id, ip, "FAILED");
                    return new RuntimeException("Agent not found with id: " + id);
                });
        agentRepository.deleteById(id);
        auditLogService.logAction("DELETE_USER", currentUser, "Deleted user: " + agent.getAgentId(), ip, "SUCCESS");
    }

    public void deleteAgentByAgentId(String agentId) {
        String currentUser = getCurrentUser();
        String ip = getClientIp();
        Agent agent = agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> {
                    auditLogService.logAction("DELETE_USER", currentUser, "Failed to delete user: Not found with agentId " + agentId, ip, "FAILED");
                    return new RuntimeException("Agent not found with agentId: " + agentId);
                });
        agentRepository.delete(agent);
        auditLogService.logAction("DELETE_USER", currentUser, "Deleted user: " + agentId, ip, "SUCCESS");
    }

    public List<Agent> getActiveAgents() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        return agentRepository.findActiveAgents(sevenDaysAgo);
    }

    public List<Agent> getAgentsCheckedInToday() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        return agentRepository.findAgentsWithCheckInToday(startOfDay, endOfDay);
    }

    public Optional<Agent> validateLogin(String agentId, String password) {
        Optional<Agent> agent = agentRepository.findByAgentId(agentId);
        if (agent.isPresent() && agent.get().getPassword().equals(password) && Boolean.TRUE.equals(agent.get().getIsActive())) {
            return agent;
        }
        return Optional.empty();
    }

    public boolean existsByAgentId(String agentId) {
        return agentRepository.existsByAgentId(agentId);
    }

    public long countAgents() {
        return agentRepository.count();
    }
}
