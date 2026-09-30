-- One-time old-data timezone correction (post Task 3 audit): a marker
-- column so the correction script (kept local, not committed — see
-- .gitignore's *.sql rule and scripts/task2-split-voucher-report.sql for
-- the same pattern) is provably safe to run only once. Purely additive —
-- no data changes here, just the column the correction will stamp.
ALTER TABLE sales_records ADD COLUMN IF NOT EXISTS timezone_corrected_at TIMESTAMP;
ALTER TABLE shop_visit_scan ADD COLUMN IF NOT EXISTS timezone_corrected_at TIMESTAMP;
ALTER TABLE lmt_daily_stock ADD COLUMN IF NOT EXISTS timezone_corrected_at TIMESTAMP;
-- sale_items has no timestamp column of its own and is corrected by
-- joining to its already-marked parent sales_records row, so it needs no
-- marker of its own.
