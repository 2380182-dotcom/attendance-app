-- Ensure products table has the necessary columns (in case V1 was skipped due to baseline)
ALTER TABLE products ADD COLUMN IF NOT EXISTS category VARCHAR(50);
ALTER TABLE products ADD COLUMN IF NOT EXISTS unit VARCHAR(50);
ALTER TABLE products ADD COLUMN IF NOT EXISTS created_at TIMESTAMP(6);
ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);

-- Ensure sales_records table has the necessary columns (in case V1 was skipped due to baseline)
ALTER TABLE sales_records ADD COLUMN IF NOT EXISTS store_name VARCHAR(255);
ALTER TABLE sales_records ADD COLUMN IF NOT EXISTS total_units INT DEFAULT 0;
ALTER TABLE sales_records ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP(6);
ALTER TABLE sales_records ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE sales_records ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE sales_records ADD COLUMN IF NOT EXISTS created_by BIGINT;

-- Seed product catalog (skip if product name already exists)
INSERT INTO products (name, category, unit, price, is_active, created_at)
SELECT v.name, v.category, v.unit, v.price, true, NOW()
FROM (VALUES
    ('Large Bread', 'BREAD', 'piece', 195.00),
    ('Small Bread', 'BREAD', 'piece', 107.00),
    ('Milky Bread Small', 'BREAD', 'piece', 100.00),
    ('Bran Bread', 'BREAD', 'piece', 151.00),
    ('Multigrain Bread', 'BREAD', 'piece', 234.00),
    ('S/W Bread', 'BREAD', 'piece', 265.00),
    ('Pitta Bread (1*4)', 'BREAD', 'pack', 108.00),
    ('Tortilla Bread 10in (1X8)', 'BREAD', 'pack', 307.00),
    ('Tortilla Bread 8in (1*8)', 'BREAD', 'pack', 206.00),
    ('QP B/Bun W/Seeded Uncut (1*2)', 'BUN', 'pack', 71.00),
    ('QP B/Bun W/Seeded Uncut (1*4)', 'BUN', 'pack', 133.00),
    ('MAC Royal B/Bun W/Seeded (1*2)', 'BUN', 'pack', 78.00),
    ('Hot Dog B/Bun (1*1)', 'BUN', 'piece', 40.00),
    ('Sheermall', 'BREAD', 'piece', 48.00),
    ('Fruit Bun', 'BUN', 'piece', 41.00),
    ('Fruit Cake', 'CAKE', 'piece', 198.00),
    ('Plain Cake', 'CAKE', 'piece', 198.00),
    ('Mini Fruit Cake', 'CAKE', 'piece', 110.00),
    ('Mini Plain Cake', 'CAKE', 'piece', 109.00),
    ('Cake Rusk', 'RUSK', 'pack', 293.00),
    ('Mini Cake Rusk', 'RUSK', 'pack', 105.00),
    ('Gol Cake (1*2)', 'CAKE', 'pack', 78.00),
    ('Lemon Cake', 'CAKE', 'piece', 276.00),
    ('Marble Cake', 'CAKE', 'piece', 306.00),
    ('Old Fashion Cake', 'CAKE', 'piece', 307.00),
    ('Muffin Pine Apple (1*6)', 'MUFFIN', 'pack', 208.00),
    ('Muffin Strawberry (1*6)', 'MUFFIN', 'pack', 209.00),
    ('Muffin Mango (1*6)', 'MUFFIN', 'pack', 208.00),
    ('Muffin Chocolate (1*6)', 'MUFFIN', 'pack', 209.00),
    ('Muffin Chocolate Chip (1*4)', 'MUFFIN', 'pack', 307.00),
    ('Muffin Double Chocolate (1*4)', 'MUFFIN', 'pack', 312.00),
    ('Dawn Rusk', 'RUSK', 'pack', 137.00),
    ('Bran Rusk', 'RUSK', 'pack', 140.00),
    ('Crispy Rusk', 'RUSK', 'pack', 200.00),
    ('OTR Large', 'OTHER', 'piece', 206.00),
    ('OTR Small', 'OTHER', 'piece', 125.00),
    ('Crumbs', 'OTHER', 'pack', 140.00),
    ('Bakarkhani (1*2)', 'OTHER', 'pack', 90.00),
    ('Round Rusk', 'RUSK', 'pack', 170.00),
    ('Spring Roll Pastry (1*24)', 'PASTRY', 'pack', 210.00)
) AS v(name, category, unit, price)
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.name = v.name);
