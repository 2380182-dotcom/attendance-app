package com.dawnbread.attendance.dto;

/** Input-only shape for POST /api/lmt/hierarchy-persons. No id, no tenantId. */
public class HierarchyPersonCreateDTO {
    private String name;
    private String roleLabel;
    private String contact;

    public HierarchyPersonCreateDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRoleLabel() { return roleLabel; }
    public void setRoleLabel(String roleLabel) { this.roleLabel = roleLabel; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
}
