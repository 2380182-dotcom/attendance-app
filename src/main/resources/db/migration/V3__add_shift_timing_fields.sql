-- Shift timing on agent
ALTER TABLE agent ADD COLUMN IF NOT EXISTS shift_start_time TIME;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS shift_end_time TIME;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS grace_period_minutes INT DEFAULT 15;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS working_days JSONB;

-- Shift compliance on attendance
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS shift_start_time TIME;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS shift_end_time TIME;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS late_minutes INT DEFAULT 0;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS face_verified_checkin BOOLEAN DEFAULT FALSE;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS face_verified_checkout BOOLEAN DEFAULT FALSE;

-- Set defaults for existing agents
UPDATE agent SET shift_start_time = '09:00:00' WHERE shift_start_time IS NULL;
UPDATE agent SET shift_end_time = '17:00:00' WHERE shift_end_time IS NULL;
UPDATE agent SET grace_period_minutes = 15 WHERE grace_period_minutes IS NULL;
