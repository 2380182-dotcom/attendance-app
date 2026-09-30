-- One-voucher-per-visit (Task 2): client-generated idempotency key so a
-- double-tap or a slow-network retry of POST /sales/shop-visit can't create
-- two SalesRecords for the same visit. Nullable — old app versions that
-- never send a requestId are unaffected, and a UNIQUE index treats every
-- NULL as distinct from every other NULL, so legacy rows never collide.
ALTER TABLE sales_records ADD COLUMN IF NOT EXISTS request_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS ux_sales_records_request_id ON sales_records(request_id);
