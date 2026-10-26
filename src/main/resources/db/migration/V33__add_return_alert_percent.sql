-- Cockpit "Needs attention": shops/products whose return % (return value /
-- sale value) is above this threshold are flagged. Additive only; every
-- existing tenant row gets the agreed default of 15%.
ALTER TABLE lmt_settings ADD COLUMN IF NOT EXISTS return_alert_percent DOUBLE PRECISION NOT NULL DEFAULT 15;
