package com.dawnbread.attendance.config;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.repository.AgentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private AgentRepository agentRepository;

    @Override
    public void run(String... args) throws Exception {
        // Seed Admin user if absent
        if (!agentRepository.existsByAgentId("ADMIN001")) {
            Agent admin = new Agent();
            admin.setAgentId("ADMIN001");
            admin.setName("System Admin");
            admin.setEmail("admin@attendance.com");
            admin.setPhone("1234567890");
            admin.setPassword("admin");
            admin.setRole("ADMIN");
            admin.setDepartment("MANAGEMENT");
            admin.setCreatedAt(LocalDateTime.now());
            admin.setCreatedBy("SYSTEM");
            admin.setIsActive(true);
            agentRepository.save(admin);
            System.out.println("Seeded admin user: ADMIN001 / admin");
        }

        // Seed Demo Agent user if absent
        if (!agentRepository.existsByAgentId("DEMO001")) {
            Agent agent = new Agent();
            agent.setAgentId("DEMO001");
            agent.setName("Demo Agent");
            agent.setEmail("demo@attendance.com");
            agent.setPhone("0987654321");
            agent.setPassword("password");
            agent.setRole("AGENT");
            agent.setDepartment("SALES");
            agent.setCreatedAt(LocalDateTime.now());
            agent.setCreatedBy("SYSTEM");
            agent.setIsActive(true);
            agentRepository.save(agent);
            System.out.println("Seeded demo agent user: DEMO001 / password");
        }
    }
}
