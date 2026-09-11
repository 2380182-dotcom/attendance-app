package com.dawnbread.attendance.dto;

import java.time.LocalDateTime;

public class AreaDTO {
    private Long id;
    private String name;
    private HierarchyPersonDTO tse;
    private HierarchyPersonDTO srTse;
    private HierarchyPersonDTO asm;
    private Boolean isActive;
    private LocalDateTime createdAt;

    public AreaDTO() {}

    public AreaDTO(Long id, String name, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public HierarchyPersonDTO getTse() { return tse; }
    public void setTse(HierarchyPersonDTO tse) { this.tse = tse; }

    public HierarchyPersonDTO getSrTse() { return srTse; }
    public void setSrTse(HierarchyPersonDTO srTse) { this.srTse = srTse; }

    public HierarchyPersonDTO getAsm() { return asm; }
    public void setAsm(HierarchyPersonDTO asm) { this.asm = asm; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
