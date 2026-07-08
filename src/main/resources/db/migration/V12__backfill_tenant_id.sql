-- Every existing row belongs to the default tenant seeded in V10.
UPDATE agent SET tenant_id = (SELECT id FROM tenants WHERE company_code = 'DAWNBREAD') WHERE tenant_id IS NULL;
UPDATE mart SET tenant_id = (SELECT id FROM tenants WHERE company_code = 'DAWNBREAD') WHERE tenant_id IS NULL;
UPDATE attendance SET tenant_id = (SELECT id FROM tenants WHERE company_code = 'DAWNBREAD') WHERE tenant_id IS NULL;
UPDATE sales_records SET tenant_id = (SELECT id FROM tenants WHERE company_code = 'DAWNBREAD') WHERE tenant_id IS NULL;
UPDATE sale_items SET tenant_id = (SELECT id FROM tenants WHERE company_code = 'DAWNBREAD') WHERE tenant_id IS NULL;
UPDATE geo_fence_logs SET tenant_id = (SELECT id FROM tenants WHERE company_code = 'DAWNBREAD') WHERE tenant_id IS NULL;
UPDATE notifications SET tenant_id = (SELECT id FROM tenants WHERE company_code = 'DAWNBREAD') WHERE tenant_id IS NULL;
UPDATE face_verification_log SET tenant_id = (SELECT id FROM tenants WHERE company_code = 'DAWNBREAD') WHERE tenant_id IS NULL;
UPDATE face_template SET tenant_id = (SELECT id FROM tenants WHERE company_code = 'DAWNBREAD') WHERE tenant_id IS NULL;
UPDATE audit_logs SET tenant_id = (SELECT id FROM tenants WHERE company_code = 'DAWNBREAD') WHERE tenant_id IS NULL;
UPDATE products SET tenant_id = (SELECT id FROM tenants WHERE company_code = 'DAWNBREAD') WHERE tenant_id IS NULL;
UPDATE sales_sync_log SET tenant_id = (SELECT id FROM tenants WHERE company_code = 'DAWNBREAD') WHERE tenant_id IS NULL;
