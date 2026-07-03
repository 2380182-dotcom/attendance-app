package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;

public class FaceEmbeddingResponse {

    private Long agentId;
    private String embedding;
    private boolean registered;
    private LocalDateTime updatedAt;

    public FaceEmbeddingResponse() {}

    public FaceEmbeddingResponse(Long agentId, String embedding, boolean registered, LocalDateTime updatedAt) {
        this.agentId = agentId;
        this.embedding = embedding;
        this.registered = registered;
        this.updatedAt = updatedAt;
    }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }

    public boolean isRegistered() { return registered; }
    public void setRegistered(boolean registered) { this.registered = registered; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
