-- Database Update Script for Geo-Fencing & Departmental Notifications

-- 1. Update agent table with role and department
ALTER TABLE agent ADD COLUMN role VARCHAR(50) DEFAULT 'AGENT';
ALTER TABLE agent ADD COLUMN department VARCHAR(50) DEFAULT 'AGENT';

-- Update any existing agents to have AGENT role and department
UPDATE agent SET role = 'AGENT', department = 'AGENT' WHERE role IS NULL;

-- 2. Update mart table with geo-fencing enabled flag
ALTER TABLE mart ADD COLUMN geo_fencing_enabled BOOLEAN DEFAULT TRUE;

-- 3. Create Geo-Fence Logs table
CREATE TABLE IF NOT EXISTS geo_fence_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_id BIGINT,
    mart_id BIGINT,
    action VARCHAR(20), -- 'ENTERED', 'EXITED'
    latitude DOUBLE,
    longitude DOUBLE,
    created_at DATETIME(6),
    FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE,
    FOREIGN KEY (mart_id) REFERENCES mart(id) ON DELETE CASCADE
);

-- 4. Create Notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_id BIGINT,
    agent_name VARCHAR(100),
    message TEXT,
    type VARCHAR(50), -- 'CHECK_IN', 'CHECK_OUT', 'LATE', 'ABSENT', 'AUTO_CHECK_IN', 'AUTO_CHECK_OUT'
    department VARCHAR(20), -- 'SALES', 'HR'
    is_read BOOLEAN DEFAULT FALSE,
    created_at DATETIME(6),
    FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
);

-- 5. Insert Sample Data for Testing
INSERT INTO agent (agent_id, name, email, password, role, department)
VALUES ('DEMO001', 'Demo Agent', 'demo@test.com', 'password', 'AGENT', 'SALES')
ON DUPLICATE KEY UPDATE role='AGENT', department='SALES';

