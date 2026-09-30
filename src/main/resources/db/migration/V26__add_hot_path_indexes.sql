-- Performance: attendance and sales_records are the two busiest tables
-- (every check-in/out, every sale/return) but only had an index on
-- tenant_id. Every other filter/join column used by the repositories
-- (agent_id, mart_id, check_in_time, sale_date) was doing a sequential
-- scan. Additive only — no data change, no column change.

CREATE INDEX IF NOT EXISTS ix_attendance_agent_id ON attendance(agent_id);
CREATE INDEX IF NOT EXISTS ix_attendance_mart_id ON attendance(mart_id);
CREATE INDEX IF NOT EXISTS ix_attendance_check_in_time ON attendance(check_in_time);

CREATE INDEX IF NOT EXISTS ix_sales_records_agent_id ON sales_records(agent_id);
CREATE INDEX IF NOT EXISTS ix_sales_records_sale_date ON sales_records(sale_date);
