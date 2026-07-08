-- Every row currently in production belongs to Dawn Bread. Seed it as
-- tenant #1 so V12 can backfill every existing table against a real id.
INSERT INTO tenants (company_code, name, is_active, created_at, created_by)
VALUES ('DAWNBREAD', 'Dawn Bread', TRUE, CURRENT_TIMESTAMP, 'SYSTEM_MIGRATION');
