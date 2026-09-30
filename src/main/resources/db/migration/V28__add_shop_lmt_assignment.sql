-- Task 3: "Not Visited" for an LMT salesman means their ASSIGNED outlets
-- weren't scanned, not just any unscanned shop — no such assignment
-- concept existed before this. Nullable, additive: every existing shop is
-- simply unassigned until an admin sets it. Local shops are never
-- "assigned" (Task 3 treats Local's Not Visited as all not-scanned active
-- shops, labeled as unassigned) — this column is just unused for them.
ALTER TABLE customer_shops ADD COLUMN IF NOT EXISTS assigned_agent_id BIGINT REFERENCES agent(id);
CREATE INDEX IF NOT EXISTS ix_customer_shops_assigned_agent_id ON customer_shops(assigned_agent_id);
