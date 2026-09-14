-- LMT flow redesign: adds ONE column to the existing mart table, nothing
-- else. DEFAULT 'REGULAR' backfills every existing row (including all
-- marts the 25 live Agents already check into), so no existing check-in
-- behavior changes — this column is inert until an admin explicitly marks
-- a mart COMPANY (see AdminMartScreen) and the LMT check-in gate (a later
-- stage, SalesService.submitShopVisit) starts reading it.
ALTER TABLE mart ADD COLUMN mart_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR';
