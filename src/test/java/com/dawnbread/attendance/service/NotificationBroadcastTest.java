package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.NotificationDTO;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Notification;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Regression test for "Failed to broadcast notification: Could not write
 * JSON: No serializer found for class ByteBuddyInterceptor ...
 * Notification["agent"]->Agent$HibernateProxy". The live /topic/alerts
 * broadcast must send a NotificationDTO, never the entity, because the
 * entity's lazy agent is a Hibernate proxy Jackson cannot serialize.
 */
@SpringBootTest
class NotificationBroadcastTest {

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private AgentRepository agentRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @Transactional
    void liveAlertIsBroadcastAsASerializableDtoEvenWhenTheAgentIsALazyProxy() throws Exception {
        Tenant t = new Tenant();
        t.setCompanyCode("NB" + (System.nanoTime() % 1_000_000));
        t.setName("Broadcast");
        t.setIsActive(true);
        t.setCreatedAt(LocalDateTime.now());
        t.setCreatedBy("TEST");
        t = tenantRepository.save(t);

        Agent a = new Agent();
        a.setTenantId(t.getId());
        a.setAgentId("NB" + System.nanoTime());
        a.setName("Broadcast Agent");
        a.setEmail(a.getAgentId().toLowerCase() + "@example.com");
        a.setRole("AGENT");
        a.setCreatedAt(LocalDateTime.now());
        Long agentId = agentRepository.saveAndFlush(a).getId();
        entityManager.clear();

        // Exactly how notifications are built from attendance rows: the agent is a lazy proxy.
        Agent proxy = entityManager.getReference(Agent.class, agentId);
        assertTrue(proxy.getClass().getName().contains("HibernateProxy"), "test must use a real lazy proxy");

        Notification n = new Notification();
        n.setAgent(proxy);
        n.setAgentName("Broadcast Agent");
        n.setMessage("Broadcast Agent checked in");
        n.setType("CHECK_IN");
        n.setDepartment("SALES");
        Notification saved = notificationService.saveNotification(n);

        // The old payload (the entity) really cannot be written — this is the reported error.
        assertThrows(Exception.class, () -> objectMapper.writeValueAsString(saved));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts"), payload.capture());
        assertInstanceOf(NotificationDTO.class, payload.getValue());

        String json = objectMapper.writeValueAsString(payload.getValue());
        assertTrue(json.contains("\"message\":\"Broadcast Agent checked in\""), json);
        assertTrue(json.contains("\"type\":\"CHECK_IN\""), json);
        assertTrue(json.contains("\"department\":\"SALES\""), json);
        assertTrue(json.contains("\"agentId\":" + agentId), json);
        assertTrue(json.contains("\"createdAt\""), json);
    }
}
