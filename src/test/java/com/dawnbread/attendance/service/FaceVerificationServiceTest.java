package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.FaceEmbeddingRequest;
import com.dawnbread.attendance.dto.FaceResultRequest;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.FaceVerificationLog;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.FaceVerificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaceVerificationServiceTest {

    @Mock
    private FaceVerificationLogRepository faceVerificationLogRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private FaceVerificationService faceVerificationService;

    private Agent testAgent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(faceVerificationService, "confidenceThreshold", 0.85f);
        ReflectionTestUtils.setField(faceVerificationService, "maxAttempts", 3);

        testAgent = new Agent();
        testAgent.setId(1L);
        testAgent.setAgentId("AG001");
        testAgent.setName("Test Agent");
        testAgent.setFaceVerificationEnabled(true);
    }

    @Test
    void saveFaceEmbedding_storesEmbeddingAndMarksRegistered() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(testAgent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = faceVerificationService.saveFaceEmbedding(1L, "dGVzdC1lbWJlZGRpbmc=");

        assertTrue(result.isRegistered());
        assertEquals("dGVzdC1lbWJlZGRpbmc=", result.getEmbedding());
        assertTrue(testAgent.getFaceRegistered());
        verify(agentRepository).save(testAgent);
    }

    @Test
    void recordFaceResult_passUpdatesAgentCounters() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(testAgent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(faceVerificationLogRepository.save(any(FaceVerificationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FaceResultRequest req = new FaceResultRequest();
        req.setAgentId(1L);
        req.setVerificationResult("PASS");
        req.setConfidenceScore(0.92f);
        req.setCheckpointType("CHECKIN");

        FaceVerificationLog log = faceVerificationService.recordFaceResult(req);

        assertTrue(log.getSuccess());
        assertEquals(0.92, log.getSimilarityScore(), 0.001);
        verify(notificationService, never()).sendFaceVerificationFailureAlert(any(), any(), anyDouble());
    }

    @Test
    void recordFaceResult_failAlertsAdmin() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(testAgent));
        when(faceVerificationLogRepository.save(any(FaceVerificationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FaceResultRequest req = new FaceResultRequest();
        req.setAgentId(1L);
        req.setVerificationResult("FAIL");
        req.setConfidenceScore(0.45f);
        req.setCheckpointType("CHECKIN");

        FaceVerificationLog log = faceVerificationService.recordFaceResult(req);

        assertFalse(log.getSuccess());
        verify(notificationService).sendFaceVerificationFailureAlert(testAgent, "CHECKIN", (double) 0.45f);
    }
}
