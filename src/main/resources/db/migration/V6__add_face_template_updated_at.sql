-- Track last update time for agent's stored face template
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_template_updated_at TIMESTAMP(6);

-- Face verification trigger flags (missing from earlier migrations)
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_verify_on_check_in BOOLEAN DEFAULT TRUE;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_verify_on_check_out BOOLEAN DEFAULT TRUE;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_verify_anytime BOOLEAN DEFAULT TRUE;
