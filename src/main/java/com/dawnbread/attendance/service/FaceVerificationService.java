package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.FaceEmbeddingResponse;
import com.dawnbread.attendance.dto.FaceResultRequest;
import com.dawnbread.attendance.dto.FaceStatusDTO;
import com.dawnbread.attendance.dto.FaceVerificationStatusDTO;
import com.dawnbread.attendance.dto.VerificationRequiredDTO;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.FaceVerificationLog;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.FaceVerificationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class FaceVerificationService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private FaceVerificationLogRepository faceVerificationLogRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private HttpServletRequest request;

    @Value("${face.verification.confidence-threshold:0.85}")
    private float confidenceThreshold;

    @Value("${face.verification.max-attempts:3}")
    private int maxAttempts;

    /**
     * Store on-device ML Kit face embedding (base64 float32 array). No image processing on server.
     */
    public FaceEmbeddingResponse saveFaceEmbedding(Long agentId, String embeddingBase64) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));

        if (embeddingBase64 == null || embeddingBase64.isBlank()) {
            throw new RuntimeException("Face embedding is required");
        }

        LocalDateTime now = LocalDateTime.now();
        agent.setFaceEmbedding(embeddingBase64);
        agent.setFaceRegistered(true);
        agent.setFaceTemplateUpdatedAt(now);
        agent.setUpdatedAt(now);
        agentRepository.save(agent);

        logAudit("FACE_EMBEDDING_SAVE", agent.getAgentId(),
                "On-device face embedding stored for agent id " + agentId, "SUCCESS");

        return new FaceEmbeddingResponse(agentId, embeddingBase64, true, now);
    }

    /**
     * Retrieve enrolled face embedding for on-device comparison.
     */
    public FaceEmbeddingResponse getFaceEmbedding(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));

        boolean registered = hasFaceEmbedding(agent);
        return new FaceEmbeddingResponse(
                agentId,
                registered ? agent.getFaceEmbedding() : null,
                registered,
                agent.getFaceTemplateUpdatedAt());
    }

    /**
     * Record verification result from mobile app. Backend does not process images or embeddings.
     */
    public FaceVerificationLog recordFaceResult(FaceResultRequest req) {
        Agent agent = agentRepository.findById(req.getAgentId())
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + req.getAgentId()));

        boolean passed = "PASS".equalsIgnoreCase(req.getVerificationResult());
        LocalDateTime verificationTime = req.getTimestamp() != null ? req.getTimestamp() : LocalDateTime.now();
        double score = req.getConfidenceScore() != null ? req.getConfidenceScore() : 0.0;

        FaceVerificationLog log = logVerificationAttempt(
                req.getAgentId(), passed, score, verificationTime, resolveScheduledTime(req.getCheckpointType(), agent));

        if (passed) {
            ensureDailyCounterReset(agent);
            agent.setFaceVerifiedAt(verificationTime);
            agent.setFaceVerificationCountToday(
                    (agent.getFaceVerificationCountToday() != null ? agent.getFaceVerificationCountToday() : 0) + 1);
            agent.setFaceLastVerificationDate(LocalDate.now());
            agentRepository.save(agent);
        } else {
            notificationService.sendFaceVerificationFailureAlert(agent, req.getCheckpointType(), score);
        }

        logAudit("FACE_RESULT", agent.getAgentId(),
                String.format("checkpoint=%s result=%s confidence=%.4f",
                        req.getCheckpointType(), req.getVerificationResult(), score),
                passed ? "SUCCESS" : "FAILED");

        return log;
    }

    public VerificationRequiredDTO checkVerificationRequired(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));

        VerificationRequiredDTO dto = new VerificationRequiredDTO();
        dto.setDate(LocalDate.now());
        dto.setSchedule(getVerificationSchedule(agentId));

        if (!Boolean.TRUE.equals(agent.getFaceVerificationEnabled())
                || agent.getFaceVerificationFrequency() == null
                || agent.getFaceVerificationFrequency() == 0) {
            dto.setRequired(false);
            return dto;
        }

        ensureDailyCounterReset(agent);
        List<String> times = resolveVerificationTimes(agent);
        LocalTime now = LocalTime.now();
        List<FaceVerificationLog> todayLogs = getTodaySuccessfulLogs(agentId);

        for (String timeStr : times) {
            LocalTime scheduled = LocalTime.parse(timeStr, TIME_FMT);
            if (now.isBefore(scheduled.minusMinutes(30))) {
                continue;
            }
            if (!isVerifiedAtTime(todayLogs, scheduled)) {
                dto.setRequired(true);
                dto.setNextRequiredTime(timeStr);
                return dto;
            }
        }

        dto.setRequired(false);
        return dto;
    }

    public List<String> getVerificationSchedule(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));
        return resolveVerificationTimes(agent);
    }

    public FaceVerificationStatusDTO getVerificationStatus(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));

        ensureDailyCounterReset(agent);
        List<String> times = resolveVerificationTimes(agent);
        List<FaceVerificationLog> todayLogs = getTodaySuccessfulLogs(agentId);
        LocalTime now = LocalTime.now();

        List<FaceVerificationStatusDTO.VerificationSlot> slots = new ArrayList<>();
        String nextRequired = null;

        for (String timeStr : times) {
            LocalTime scheduled = LocalTime.parse(timeStr, TIME_FMT);
            FaceVerificationLog match = findLogForTime(todayLogs, scheduled);
            String status;
            if (match != null) {
                status = "COMPLETED";
            } else if (now.isAfter(scheduled.plusMinutes(30))) {
                status = "MISSED";
            } else if (now.isAfter(scheduled.minusMinutes(30))) {
                status = "PENDING";
                if (nextRequired == null) {
                    nextRequired = timeStr;
                }
            } else {
                status = "UPCOMING";
            }
            slots.add(new FaceVerificationStatusDTO.VerificationSlot(
                    timeStr, status, match != null ? match.getVerificationTime() : null));
        }

        VerificationRequiredDTO required = checkVerificationRequired(agentId);

        FaceVerificationStatusDTO dto = new FaceVerificationStatusDTO();
        dto.setAgentId(agentId);
        dto.setDate(LocalDate.now());
        dto.setRegistered(hasFaceEmbedding(agent) || Boolean.TRUE.equals(agent.getFaceRegistered()));
        dto.setVerificationRequired(required.isRequired());
        dto.setNextRequiredTime(nextRequired != null ? nextRequired : required.getNextRequiredTime());
        dto.setVerifications(slots);
        return dto;
    }

    public void resetDailyVerificationCount(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));
        agent.setFaceVerificationCountToday(0);
        agent.setFaceLastVerificationDate(LocalDate.now());
        agentRepository.save(agent);
    }

    public void resetAllDailyVerificationCounts() {
        LocalDate today = LocalDate.now();
        for (Agent agent : agentRepository.findAll()) {
            if (agent.getFaceLastVerificationDate() == null || !agent.getFaceLastVerificationDate().equals(today)) {
                agent.setFaceVerificationCountToday(0);
                agentRepository.save(agent);
            }
        }
    }

    public FaceStatusDTO getFaceStatus(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));

        boolean registered = hasFaceEmbedding(agent) || Boolean.TRUE.equals(agent.getFaceRegistered());

        FaceStatusDTO status = new FaceStatusDTO();
        status.setAgentId(agentId);
        status.setRegistered(registered);
        status.setTemplateUpdatedAt(agent.getFaceTemplateUpdatedAt());
        status.setFaceVerifyOnCheckIn(agent.getFaceVerifyOnCheckIn());
        status.setFaceVerifyOnCheckOut(agent.getFaceVerifyOnCheckOut());
        status.setFaceVerifyAnytime(agent.getFaceVerifyAnytime());
        status.setFaceVerificationEnabled(agent.getFaceVerificationEnabled());
        status.setFaceVerificationFrequency(agent.getFaceVerificationFrequency());
        status.setFaceVerificationTimes(agent.getFaceVerificationTimes());
        status.setFaceVerificationCountToday(agent.getFaceVerificationCountToday());
        status.setVerificationStatus(getVerificationStatus(agentId));
        status.setConfidenceThreshold(confidenceThreshold);
        return status;
    }

    public void updateFaceConfig(Long agentId, Boolean enabled, Integer frequency, List<String> times) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));

        if (enabled != null) {
            agent.setFaceVerificationEnabled(enabled);
        }
        if (frequency != null) {
            agent.setFaceVerificationFrequency(frequency);
            if (times == null || times.isEmpty()) {
                agent.setFaceVerificationTimes(defaultTimesForFrequency(frequency));
            }
        }
        if (times != null && !times.isEmpty()) {
            agent.setFaceVerificationTimes(times);
        }
        agent.setUpdatedAt(LocalDateTime.now());
        agentRepository.save(agent);
    }

    public float getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public boolean isFaceVerificationRequired(Long agentId, String checkpointType) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));

        if (!Boolean.TRUE.equals(agent.getFaceVerificationEnabled())) {
            return false;
        }

        return switch (checkpointType) {
            case "CHECKIN" -> Boolean.TRUE.equals(agent.getFaceVerifyOnCheckIn());
            case "CHECKOUT" -> Boolean.TRUE.equals(agent.getFaceVerifyOnCheckOut());
            case "MIDSHIFT" -> checkVerificationRequired(agentId).isRequired()
                    || Boolean.TRUE.equals(agent.getFaceVerifyAnytime());
            default -> false;
        };
    }

    public FaceVerificationLog logVerificationAttempt(Long agentId, boolean success, double score,
                                                       LocalDateTime time, LocalTime scheduledTime) {
        FaceVerificationLog log = new FaceVerificationLog();
        log.setAgentId(agentId);
        log.setVerificationTime(time);
        log.setScheduledTime(scheduledTime);
        log.setSuccess(success);
        log.setSimilarityScore(score);
        log.setIpAddress(request != null ? request.getRemoteAddr() : "0.0.0.0");
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        return faceVerificationLogRepository.save(log);
    }

    private boolean hasFaceEmbedding(Agent agent) {
        return agent.getFaceEmbedding() != null && !agent.getFaceEmbedding().isBlank();
    }

    private LocalTime resolveScheduledTime(String checkpointType, Agent agent) {
        if (!"MIDSHIFT".equals(checkpointType)) {
            return null;
        }
        VerificationRequiredDTO required = checkVerificationRequired(agent.getId());
        if (required.getNextRequiredTime() != null) {
            return LocalTime.parse(required.getNextRequiredTime(), TIME_FMT);
        }
        return null;
    }

    private List<String> resolveVerificationTimes(Agent agent) {
        if (agent.getFaceVerificationTimes() != null && !agent.getFaceVerificationTimes().isEmpty()) {
            return agent.getFaceVerificationTimes();
        }
        int freq = agent.getFaceVerificationFrequency() != null ? agent.getFaceVerificationFrequency() : 2;
        return defaultTimesForFrequency(freq);
    }

    private List<String> defaultTimesForFrequency(int frequency) {
        return switch (frequency) {
            case 0 -> Collections.emptyList();
            case 1 -> List.of("09:00");
            case 3 -> List.of("09:00", "13:00", "17:00");
            default -> List.of("09:00", "17:00");
        };
    }

    private void ensureDailyCounterReset(Agent agent) {
        LocalDate today = LocalDate.now();
        if (agent.getFaceLastVerificationDate() == null || !agent.getFaceLastVerificationDate().equals(today)) {
            agent.setFaceVerificationCountToday(0);
            agent.setFaceLastVerificationDate(today);
            agentRepository.save(agent);
        }
    }

    private List<FaceVerificationLog> getTodaySuccessfulLogs(Long agentId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().atTime(23, 59, 59);
        return faceVerificationLogRepository.findByAgentIdAndVerificationTimeBetween(agentId, start, end)
                .stream()
                .filter(l -> Boolean.TRUE.equals(l.getSuccess()))
                .collect(Collectors.toList());
    }

    private boolean isVerifiedAtTime(List<FaceVerificationLog> logs, LocalTime scheduled) {
        return findLogForTime(logs, scheduled) != null;
    }

    private FaceVerificationLog findLogForTime(List<FaceVerificationLog> logs, LocalTime scheduled) {
        for (FaceVerificationLog log : logs) {
            if (log.getScheduledTime() != null && log.getScheduledTime().equals(scheduled)) {
                return log;
            }
            if (log.getVerificationTime() != null) {
                LocalTime actual = log.getVerificationTime().toLocalTime();
                if (!actual.isBefore(scheduled.minusMinutes(30)) && !actual.isAfter(scheduled.plusMinutes(30))) {
                    return log;
                }
            }
        }
        return null;
    }

    private void logAudit(String action, String username, String details, String status) {
        String ip = request != null ? request.getRemoteAddr() : "0.0.0.0";
        auditLogService.logAction(action, username, details, ip, status);
    }
}
