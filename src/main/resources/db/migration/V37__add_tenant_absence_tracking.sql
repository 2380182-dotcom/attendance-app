-- Per-company "Absence tracking" switch, OFF by default. While off, the
-- 10 PM job saves no absences and sends no absence notifications for that
-- company, and the HR cockpit shows "Absence tracking is off" instead of
-- absence numbers. An administrator turns it on (Admin > Holidays).
ALTER TABLE tenants ADD COLUMN absence_tracking_enabled BOOLEAN NOT NULL DEFAULT FALSE;
