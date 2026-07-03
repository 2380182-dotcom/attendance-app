-- Face verification configuration on agent
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_verification_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_verification_frequency INT DEFAULT 2;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_verification_times JSONB;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_verified_at TIMESTAMP(6);
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_verification_count_today INT DEFAULT 0;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS face_last_verification_date DATE;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE agent ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

CREATE TABLE IF NOT EXISTS face_verification_log (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    verification_time TIMESTAMP(6),
    scheduled_time TIME,
    success BOOLEAN,
    similarity_score DECIMAL(5,4),
    ip_address VARCHAR(45),
    user_agent TEXT,
    FOREIGN KEY (agent_id) REFERENCES agent(id)
);

CREATE TABLE IF NOT EXISTS face_template (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    feature_vector TEXT NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    FOREIGN KEY (agent_id) REFERENCES agent(id)
);

CREATE INDEX IF NOT EXISTS idx_face_log_agent_date ON face_verification_log(agent_id, verification_time);
CREATE INDEX IF NOT EXISTS idx_face_template_agent ON face_template(agent_id);
