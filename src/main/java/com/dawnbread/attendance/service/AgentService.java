package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.security.TokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private HttpServletRequest request;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenProvider tokenProvider;

    private String getCurrentUser() {
        if (request != null) {
            Object username = request.getAttribute("username");
            if (username != null) {
                return username.toString();
            }
        }
        return "SYSTEM";
    }

    // ===== AUTHENTICATION =====
    
    public Optional<Agent> authenticate(String username, String password) {
        Optional<Agent> agentOpt = agentRepository.findByAgentId(username);
        
        if (agentOpt.isPresent()) {
            Agent agent = agentOpt.get();
            
            // Check if agent is active
            if (agent.getIsActive() == null || !agent.getIsActive()) {
                return Optional.empty();
            }
            
            // Verify password with BCrypt
            if (passwordEncoder.matches(password, agent.getPassword())) {
                return agentOpt;
            }
            
            // Legacy plaintext password support (migrate on login)
            if (password.equals(agent.getPassword())) {
                agent.setPassword(passwordEncoder.encode(password));
                agentRepository.save(agent);
                return agentOpt;
            }
        }
        return Optional.empty();
    }

    public Optional<Agent> validateLogin(String username, String password) {
        return authenticate(username, password);
    }

    // ===== CRUD OPERATIONS =====
    
    public Agent createAgent(Agent agent) {
        if (agent.getPassword() != null && !agent.getPassword().isEmpty()) {
            agent.setPassword(passwordEncoder.encode(agent.getPassword()));
        }
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    public Agent updateAgent(Agent agent) {
        if (agent.getPassword() != null && !agent.getPassword().isEmpty()) {
            if (!agent.getPassword().startsWith("$2a$")) {
                agent.setPassword(passwordEncoder.encode(agent.getPassword()));
            }
        }
        return agentRepository.save(agent);
    }

    public Agent updateAgent(Long id, Agent agentDetails) {
        Agent existing = agentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + id));

        if (agentDetails.getAgentId() != null) {
            existing.setAgentId(agentDetails.getAgentId());
        }
        if (agentDetails.getName() != null) {
            existing.setName(agentDetails.getName());
        }
        if (agentDetails.getEmail() != null) {
            existing.setEmail(agentDetails.getEmail());
        }
        if (agentDetails.getPhone() != null) {
            existing.setPhone(agentDetails.getPhone());
        }
        if (agentDetails.getRole() != null) {
            existing.setRole(agentDetails.getRole());
        }
        if (agentDetails.getDepartment() != null) {
            existing.setDepartment(agentDetails.getDepartment());
        }
        if (agentDetails.getIsActive() != null) {
            existing.setIsActive(agentDetails.getIsActive());
        }
        if (agentDetails.getFaceVerifyOnCheckIn() != null) {
            existing.setFaceVerifyOnCheckIn(agentDetails.getFaceVerifyOnCheckIn());
        }
        if (agentDetails.getFaceVerifyOnCheckOut() != null) {
            existing.setFaceVerifyOnCheckOut(agentDetails.getFaceVerifyOnCheckOut());
        }
        if (agentDetails.getFaceVerifyAnytime() != null) {
            existing.setFaceVerifyAnytime(agentDetails.getFaceVerifyAnytime());
        }
        if (agentDetails.getFaceRegistered() != null) {
            existing.setFaceRegistered(agentDetails.getFaceRegistered());
        }
        if (agentDetails.getFaceTemplate() != null) {
            existing.setFaceTemplate(agentDetails.getFaceTemplate());
        }
        if (agentDetails.getFaceVerificationEnabled() != null) {
            existing.setFaceVerificationEnabled(agentDetails.getFaceVerificationEnabled());
        }
        if (agentDetails.getFaceVerificationFrequency() != null) {
            existing.setFaceVerificationFrequency(agentDetails.getFaceVerificationFrequency());
        }
        if (agentDetails.getFaceVerificationTimes() != null) {
            existing.setFaceVerificationTimes(agentDetails.getFaceVerificationTimes());
        }
        if (agentDetails.getShiftStartTime() != null) {
            existing.setShiftStartTime(agentDetails.getShiftStartTime());
        }
        if (agentDetails.getShiftEndTime() != null) {
            existing.setShiftEndTime(agentDetails.getShiftEndTime());
        }
        if (agentDetails.getGracePeriodMinutes() != null) {
            existing.setGracePeriodMinutes(agentDetails.getGracePeriodMinutes());
        }
        if (agentDetails.getWorkingDays() != null) {
            existing.setWorkingDays(agentDetails.getWorkingDays());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        if (agentDetails.getPassword() != null && !agentDetails.getPassword().isEmpty()) {
            if (!agentDetails.getPassword().startsWith("$2a$")) {
                existing.setPassword(passwordEncoder.encode(agentDetails.getPassword()));
            } else {
                existing.setPassword(agentDetails.getPassword());
            }
        }

        return agentRepository.save(existing);
    }

    public void deleteAgent(Long id) {
        agentRepository.deleteById(id);
    }

    // ===== QUERY METHODS =====
    
    public Optional<Agent> getAgentById(Long id) {
        return agentRepository.findById(id);
    }

    public Optional<Agent> getAgentByAgentId(String agentId) {
        return agentRepository.findByAgentId(agentId);
    }

    public Optional<Agent> getAgentByEmail(String email) {
        return agentRepository.findByEmail(email);
    }

    public List<Agent> getAllAgents() {
        return agentRepository.findAll();
    }

    public List<Agent> searchAgentsByName(String name) {
        return agentRepository.findByNameContainingIgnoreCase(name);
    }

    public List<Agent> getActiveAgents() {
        return agentRepository.findActiveAgents(LocalDateTime.now().minusDays(7));
    }

    public List<Agent> getAgentsCheckedInToday() {
        LocalDateTime start = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime end = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        return agentRepository.findAgentsWithCheckInToday(start, end);
    }

    public long countAgents() {
        return agentRepository.count();
    }

    public boolean existsByAgentId(String agentId) {
        return agentRepository.existsByAgentId(agentId);
    }

    public boolean existsByEmail(String email) {
        return agentRepository.existsByEmail(email);
    }

    // ===== TOKEN METHODS =====
    
    public boolean validateTokenAndUser(String token, String username) {
        try {
            Claims claims = tokenProvider.parseToken(token);
            String tokenUsername = claims.getSubject();
            return tokenUsername.equals(username) && !tokenProvider.isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public Long getAgentIdFromToken(String token) {
        try {
            Claims claims = tokenProvider.parseToken(token);
            Object id = claims.get("id");
            if (id != null) {
                return Long.valueOf(id.toString());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public String getRoleFromToken(String token) {
        try {
            Claims claims = tokenProvider.parseToken(token);
            Object role = claims.get("role");
            return role != null ? role.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public List<Agent> getAgentsByCreatedDateRange(LocalDateTime start, LocalDateTime end) {
        return agentRepository.findByCreatedAtBetween(start, end);
    }
}
