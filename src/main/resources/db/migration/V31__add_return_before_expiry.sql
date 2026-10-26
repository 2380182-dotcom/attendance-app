-- "Salesman updates" feature, part 1: a new SaleItem transaction type,
-- RETURN_BEFORE_EXPIRY — the salesman takes back still-unexpired bread and
-- replaces it with equal quantity of fresh bread from his own stock. Priced
-- like RETURN (excluded from totalAmount) but consumes van stock like a
-- SALE (see lmt_daily_stock_item.rbe_qty below and LmtStockService.reconcile).
--
-- transaction_type widened from VARCHAR(20) to VARCHAR(30) — the new value
-- is exactly 20 characters, right at the old limit, so headroom is added
-- for any future value too. Purely additive; no existing row's value changes.
ALTER TABLE sale_items ALTER COLUMN transaction_type TYPE VARCHAR(30);

-- New per-product reconciliation column, additive, defaults to 0 for every
-- existing row (old vouchers show 0 Return-Before-Expiry, as required).
ALTER TABLE lmt_daily_stock_item ADD COLUMN IF NOT EXISTS rbe_qty INTEGER NOT NULL DEFAULT 0;
