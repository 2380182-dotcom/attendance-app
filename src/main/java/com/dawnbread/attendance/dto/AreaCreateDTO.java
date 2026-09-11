package com.dawnbread.attendance.dto;

/** Input-only shape for POST /api/lmt/areas. No id, no tenantId. */
public class AreaCreateDTO {
    private String name;
    private Long tseId;
    private Long srTseId;
    private Long asmId;

    public AreaCreateDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getTseId() { return tseId; }
    public void setTseId(Long tseId) { this.tseId = tseId; }

    public Long getSrTseId() { return srTseId; }
    public void setSrTseId(Long srTseId) { this.srTseId = srTseId; }

    public Long getAsmId() { return asmId; }
    public void setAsmId(Long asmId) { this.asmId = asmId; }
}
