-- Add tenant_id to every operational table. Nullable for now — V12 backfills
-- existing rows against the default tenant, then V13 enforces NOT NULL.
-- Super Admin (V15) intentionally has no tenant_id — it's not tenant data.
ALTER TABLE agent ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE mart ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE sales_records ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE geo_fence_logs ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE face_verification_log ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE face_template ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE products ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE sales_sync_log ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);

CREATE INDEX IF NOT EXISTS ix_agent_tenant_id ON agent (tenant_id);
CREATE INDEX IF NOT EXISTS ix_mart_tenant_id ON mart (tenant_id);
CREATE INDEX IF NOT EXISTS ix_attendance_tenant_id ON attendance (tenant_id);
CREATE INDEX IF NOT EXISTS ix_sales_records_tenant_id ON sales_records (tenant_id);
CREATE INDEX IF NOT EXISTS ix_sale_items_tenant_id ON sale_items (tenant_id);
CREATE INDEX IF NOT EXISTS ix_geo_fence_logs_tenant_id ON geo_fence_logs (tenant_id);
CREATE INDEX IF NOT EXISTS ix_notifications_tenant_id ON notifications (tenant_id);
CREATE INDEX IF NOT EXISTS ix_face_verification_log_tenant_id ON face_verification_log (tenant_id);
CREATE INDEX IF NOT EXISTS ix_face_template_tenant_id ON face_template (tenant_id);
CREATE INDEX IF NOT EXISTS ix_audit_logs_tenant_id ON audit_logs (tenant_id);
CREATE INDEX IF NOT EXISTS ix_products_tenant_id ON products (tenant_id);
CREATE INDEX IF NOT EXISTS ix_sales_sync_log_tenant_id ON sales_sync_log (tenant_id);
