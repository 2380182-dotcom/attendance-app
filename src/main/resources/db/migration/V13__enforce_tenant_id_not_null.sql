-- Every operational row now has a tenant_id (V12 backfilled them all).
-- Super Admin lives in its own table (V15) with no tenant_id at all, so no
-- exception is needed here — every one of these 12 tables can be strict.
ALTER TABLE agent ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE mart ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE attendance ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE sales_records ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE sale_items ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE geo_fence_logs ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE face_verification_log ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE face_template ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE audit_logs ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE products ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE sales_sync_log ALTER COLUMN tenant_id SET NOT NULL;
