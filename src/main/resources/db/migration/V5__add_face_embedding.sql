-- On-device ML Kit face embedding stored as base64-encoded float32 array
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_embedding TEXT;

-- Migrate existing registrations: mark agents with face_template as registered
UPDATE agent SET face_registered = TRUE WHERE face_template IS NOT NULL AND face_template != '';
