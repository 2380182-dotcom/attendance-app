package com.dawnbread.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class FaceEmbeddingRequest {

    @NotNull
    private Long agentId;

    @NotBlank
    private String embedding; // base64-encoded float32 array

    public FaceEmbeddingRequest() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }
}
