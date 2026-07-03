package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.*;
import com.dawnbread.attendance.service.FaceVerificationService;
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

    /**
     * Store on-device ML Kit face embedding (enrollment).
     */
    @PostMapping("/embedding")
    public ResponseEntity<ApiResponse<FaceEmbeddingResponse>> saveEmbedding(
            @Valid @RequestBody FaceEmbeddingRequest request) {
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
     * Retrieve enrolled face embedding for on-device comparison.
     */
    @GetMapping("/embedding/{agentId}")
    public ResponseEntity<ApiResponse<FaceEmbeddingResponse>> getEmbedding(@PathVariable Long agentId) {
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
        try {
            List<String> schedule = faceVerificationService.getVerificationSchedule(agentId);
            return ResponseEntity.ok(ApiResponse.success("Verification schedule retrieved", schedule));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/config/{agentId}")
    public ResponseEntity<ApiResponse<FaceConfigDTO>> updateConfig(
            @PathVariable Long agentId,
            @RequestBody FaceConfigDTO config) {
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
