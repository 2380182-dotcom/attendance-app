-- Stage 2: first migration touching sales_records/sale_items directly.
-- Every ADD COLUMN below has a constant DEFAULT (metadata-only on this
-- deployment's Postgres 15 — no table rewrite, no lock held beyond a brief
-- catalog update). The index rebuild scans the whole table, but this is
-- weeks of data from 25 agents — sub-second, brief write-block only, reads
-- unaffected. No ALTER on attendance/agent/mart.

-- Header-level, real relation — one shop per visit, null for the untouched
-- legacy /entry and /entry-with-images flow. This one keeps its FK; it's
-- not part of any unique constraint, so plain NULL is fine here.
ALTER TABLE sales_records ADD COLUMN customer_shop_id BIGINT REFERENCES customer_shops(id);
ALTER TABLE sales_records ADD COLUMN distance_from_shop_meters DOUBLE PRECISION;

-- Denormalized copy for the widened unique index below, same reasoning and
-- same shape as V17's own agent_id column on this table: NOT NULL, no FK
-- (agent_id there never had one either — that constraint's job is purely
-- to back an index, real referential integrity is enforced through
-- sales_record_id -> sales_records -> agent/customer_shop).
--
-- Uses a NOT NULL sentinel (-1) rather than nullable + a NULL-tolerant
-- expression index. An earlier draft of this migration used
-- COALESCE(customer_shop_id, -1) on a nullable column instead — caught as
-- wrong before it shipped: this project's test suite runs with Flyway
-- disabled (ddl-auto=create-drop derives schema from JPA annotations
-- instead), and a COALESCE expression index can't be expressed via
-- @UniqueConstraint, so the COALESCE version could never actually be
-- exercised by any test — only a plain, non-expression column list can be,
-- and that's exactly what this NOT NULL sentinel gives us for free. Every
-- environment (prod via this file, tests via Hibernate's DDL generation)
-- now describes the physically identical index shape.
ALTER TABLE sale_items ADD COLUMN customer_shop_id BIGINT NOT NULL DEFAULT -1;
ALTER TABLE sale_items ADD COLUMN transaction_type VARCHAR(20) NOT NULL DEFAULT 'SALE';

-- Widen V17's guard: an agent can now legitimately sell/return/carry-unsold
-- the same product on the same day across different shops. The old
-- (agent, product, date) key alone would incorrectly block the second
-- shop's identical product line. Every existing row already satisfies the
-- wider key trivially (it already satisfied the narrower one, and now all
-- share the same -1 sentinel for customer_shop_id), so this cannot
-- introduce a duplicate that wasn't already prevented — the legacy flow's
-- one-row-per-agent/product/day guarantee is exactly preserved (-1 always
-- equals -1, unlike NULL never equaling NULL), and LMT rows get the full,
-- correct wider key via their real shop id.
DROP INDEX ux_sale_items_agent_product_date;
CREATE UNIQUE INDEX ux_sale_items_agent_product_date_shop_type
    ON sale_items (agent_id, product_id, sale_date, customer_shop_id, transaction_type);
