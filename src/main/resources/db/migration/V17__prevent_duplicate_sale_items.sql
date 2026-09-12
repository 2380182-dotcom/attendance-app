-- Finding 09 (security audit): the same shape of race as Finding 03. Two
-- concurrent sales submissions for the same agent/product/day can both pass
-- the in-memory duplicate check (SalesService builds existingProductIds
-- from a read, then inserts) before either commits, double-recording a
-- sale. The application's own invariant is per-product-per-day, and
-- sale_date lives on the parent sales_records row, not on sale_items — so
-- enforcing this at the database level requires denormalizing agent_id and
-- sale_date onto sale_items itself.
--
-- Confirmed via a read-only check against production first: zero existing
-- (agent, product, date) duplicates, so the unique index applies cleanly.
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS agent_id BIGINT;
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS sale_date DATE;

UPDATE sale_items si
SET agent_id = sr.agent_id,
    sale_date = sr.sale_date
FROM sales_records sr
WHERE si.sales_record_id = sr.id
  AND si.agent_id IS NULL;

ALTER TABLE sale_items ALTER COLUMN agent_id SET NOT NULL;
ALTER TABLE sale_items ALTER COLUMN sale_date SET NOT NULL;

CREATE UNIQUE INDEX ux_sale_items_agent_product_date ON sale_items (agent_id, product_id, sale_date);
