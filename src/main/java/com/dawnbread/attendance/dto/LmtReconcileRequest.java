package com.dawnbread.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * POST /api/lmt/stock/reconcile — the SALESMAN_LMT / ADMIN-only flow.
 *
 * items may be a partial list (or even empty) — any product from the
 * morning's opening stock not named here is reconciled with
 * returnedQty=0, unsoldQty=0 rather than rejecting the whole request. Per
 * the build plan, a missing product entry is a real discrepancy to record
 * (as Missing), not something to block reconciliation over.
 */
public class LmtReconcileRequest {

    @NotNull(message = "Agent ID is required")
    private Long agentId;

    @Valid
    private List<LmtReconcileItemRequest> items;

    public LmtReconcileRequest() {}

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public List<LmtReconcileItemRequest> getItems() { return items; }
    public void setItems(List<LmtReconcileItemRequest> items) { this.items = items; }
}
