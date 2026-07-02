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

-- 6. Sales & Products Schema Additions
CREATE TABLE IF NOT EXISTS products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    price DOUBLE NOT NULL,
    image_url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS sales_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_id BIGINT,
    total_amount DOUBLE NOT NULL,
    sale_date DATE NOT NULL,
    sale_time TIME NOT NULL,
    location VARCHAR(255),
    created_at DATETIME(6),
    modified_at DATETIME(6),
    modified_by VARCHAR(50),
    override_reason VARCHAR(255),
    FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS sale_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sales_record_id BIGINT,
    product_id BIGINT,
    quantity INT NOT NULL,
    unit_price DOUBLE NOT NULL,
    total_price DOUBLE NOT NULL,
    product_image_url VARCHAR(500),
    FOREIGN KEY (sales_record_id) REFERENCES sales_records(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS sales_sync_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sale_record_id BIGINT,
    synced_to VARCHAR(50), -- 'SALES_DEPARTMENT', 'HR_DEPARTMENT'
    synced_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    sync_status VARCHAR(20), -- 'SUCCESS', 'FAILED', 'PENDING'
    sync_message TEXT,
    FOREIGN KEY (sale_record_id) REFERENCES sales_records(id) ON DELETE CASCADE
);


