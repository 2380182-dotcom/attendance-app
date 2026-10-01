-- Task 4 (Local/LMT Sales Voucher sections): which section a voucher
-- belongs to is decided by the salesman's role, but a.role is a LIVE join
-- (see SalesRecordRepository.findBySaleDateAndAgentRole) — if an agent's
-- role is ever changed later, that would retroactively move ALL of their
-- historical vouchers to the other section. This column snapshots the
-- role at submission time so new vouchers never move. Nullable/additive:
-- every pre-existing row and every legacy non-shop-visit submission keeps
-- NULL here and falls back to the live agent.role join exactly as before
-- (see COALESCE usage in SalesVoucherService).
ALTER TABLE sales_records ADD COLUMN IF NOT EXISTS agent_role_at_sale VARCHAR(20);

-- Backfill every existing row from its agent's CURRENT role, so old
-- vouchers are frozen into their section right now too and never move if
-- that agent's role changes later — not deployed yet, so this is safe to
-- add here rather than as a separate one-time script. The COALESCE in
-- SalesVoucherService/SalesRecordRepository stays only as a safety net
-- for any row this backfill can't reach (e.g. a legacy row whose agent_id
-- no longer resolves to a live agent).
UPDATE sales_records
SET agent_role_at_sale = (SELECT a.role FROM agent a WHERE a.id = sales_records.agent_id)
WHERE agent_role_at_sale IS NULL;
