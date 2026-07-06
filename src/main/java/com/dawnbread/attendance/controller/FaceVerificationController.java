package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.*;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.FaceVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/face")
public class FaceVerificationController {

    @Autowired
    private FaceVerificationService faceVerificationService;

    @Autowired
    private HttpServletRequest request;

    /**
     * Store on-device ML Kit face embedding (enrollment). Self-enrollment or
     * an admin enrolling on an agent's behalf (AdminUsersScreen does this
     * during account creation/editing) are both legitimate — anyone else is not.
     */
    @PostMapping("/embedding")
    public ResponseEntity<ApiResponse<FaceEmbeddingResponse>> saveEmbedding(
            @Valid @RequestBody FaceEmbeddingRequest request) {
        if (!AccessControl.isSelfOrRole(this.request, request.getAgentId(), "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You can only enroll your own face."));
        }
        try {
            FaceEmbeddingResponse result = faceVerificationService.saveFaceEmbedding(
                    request.getAgentId(), request.getEmbedding());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Face embedding stored successfully", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Retrieve enrolled face embedding for on-device comparison. Self-or-admin.
     */
    @GetMapping("/embedding/{agentId}")
    public ResponseEntity<ApiResponse<FaceEmbeddingResponse>> getEmbedding(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You can only read your own face data."));
        }
        try {
            FaceEmbeddingResponse result = faceVerificationService.getFaceEmbedding(agentId);
            return ResponseEntity.ok(ApiResponse.success("Face embedding retrieved", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/status/{agentId}")
    public ResponseEntity<ApiResponse<FaceStatusDTO>> getFaceStatus(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You can only read your own face status."));
        }
        try {
            FaceStatusDTO status = faceVerificationService.getFaceStatus(agentId);
            return ResponseEntity.ok(ApiResponse.success("Face status retrieved", status));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/schedule/{agentId}")
    public ResponseEntity<ApiResponse<List<String>>> getSchedule(@PathVariable Long agentId) {
        if (!AccessControl.isSelfOrRole(request, agentId, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You can only read your own verification schedule."));
        }
        try {
            List<String> schedule = faceVerificationService.getVerificationSchedule(agentId);
            return ResponseEntity.ok(ApiResponse.success("Verification schedule retrieved", schedule));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Admin-only — changes an agent's face verification policy (enabled,
     * frequency, scheduled times). Not currently called by the mobile app
     * (AdminUsersScreen sets these fields via AgentController.updateAgent
     * instead) but this endpoint is live and must be gated regardless.
     */
    @PutMapping("/config/{agentId}")
    public ResponseEntity<ApiResponse<FaceConfigDTO>> updateConfig(
            @PathVariable Long agentId,
            @RequestBody FaceConfigDTO config) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can update face verification config."));
        }
        try {
            faceVerificationService.updateFaceConfig(
                    agentId, config.getEnabled(), config.getFrequency(), config.getVerificationTimes());
            return ResponseEntity.ok(ApiResponse.success("Face config updated", config));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/config/threshold")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getThresholdConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("confidenceThreshold", faceVerificationService.getConfidenceThreshold());
        config.put("maxAttempts", faceVerificationService.getMaxAttempts());
        return ResponseEntity.ok(ApiResponse.success("Face verification config", config));
    }
}
