-- Role-based pricing (Feature 1, P1): replaces the single products.price
-- column with agent_price and salesman_price. Both are backfilled from
-- the existing price value, so on day one every product's agent_price
-- equals exactly what price was — the 25 live Agents' sale computations
-- are byte-for-byte unchanged (proven by a dedicated test in P2).
-- salesman_price also starts equal to the old price; an admin
-- differentiates it afterward via the new pricing dashboard (P3).
--
-- Existing sale_items rows are completely untouched by this migration —
-- unit_price/total_price on those rows were written once at submission
-- time and are never recomputed, so historical sales keep whatever price
-- was true when they were made, regardless of any later product price
-- change (this was already true before this migration; nothing here
-- changes that guarantee).

ALTER TABLE products ADD COLUMN agent_price DOUBLE PRECISION;
UPDATE products SET agent_price = price;
ALTER TABLE products ALTER COLUMN agent_price SET NOT NULL;

ALTER TABLE products ADD COLUMN salesman_price DOUBLE PRECISION;
UPDATE products SET salesman_price = price;
ALTER TABLE products ALTER COLUMN salesman_price SET NOT NULL;

ALTER TABLE products DROP COLUMN price;
