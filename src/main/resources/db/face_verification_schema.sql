-- Face verification schema (applied automatically via Hibernate ddl-auto=update)

CREATE TABLE IF NOT EXISTS face_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_id BIGINT NOT NULL,
    feature_vector TEXT NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    FOREIGN KEY (agent_id) REFERENCES agent(id)
);

ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_registered BOOLEAN DEFAULT FALSE;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_verify_on_check_in BOOLEAN DEFAULT TRUE;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_verify_on_check_out BOOLEAN DEFAULT TRUE;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_verify_anytime BOOLEAN DEFAULT TRUE;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_template_updated_at DATETIME(6);
