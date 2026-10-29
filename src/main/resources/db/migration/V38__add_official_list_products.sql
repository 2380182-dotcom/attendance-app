-- New products from Dawn Bread's official product list (2026-10).
--
-- Prices are not known yet, so a price can now be empty: NULL = "Not set"
-- (0 stays a valid price). Every new product is added HIDDEN (is_active =
-- false): hidden products are not offered to salesmen or agents, and sales
-- and stock entries for them are refused. Admin or Sales enters both prices
-- on the Pricing page; only an administrator switches the product on there,
-- and switching on is refused while either price is "Not set".
--
-- Fixed ids 41-70: the mobile app's bundled photos are keyed by product id.
-- "Tortilla Bread (1*8)" on the list is the existing "Tortilla Bread 10in
-- (1X8)" and is not added.
-- Only for the DAWNBREAD company (a database without it gets nothing), and a
-- name that already exists for the company is skipped, as in V4. Existing
-- products are not changed.
ALTER TABLE products ALTER COLUMN agent_price DROP NOT NULL;
ALTER TABLE products ALTER COLUMN salesman_price DROP NOT NULL;

INSERT INTO products (id, tenant_id, name, category, unit, agent_price, salesman_price, is_active, created_at, updated_at)
SELECT v.id, t.id, v.name, v.category, v.unit, NULL, NULL, false, NOW(), NOW()
FROM tenants t
CROSS JOIN (VALUES
    (41, 'Medium Bread',                        'BREAD',   'piece'),
    (42, 'Milky Bread Large',                   'BREAD',   'piece'),
    (43, 'Pitta Bread (1*5)',                   'BREAD',   'pack'),
    (44, 'MAC Royal B/Bun B/Seeded (1*2)',      'BUN',     'pack'),
    (45, 'Fruity Bun',                          'BUN',     'piece'),
    (46, 'Almond Biscuit',                      'BISCUIT', 'pack'),
    (47, 'Choco Drop Biscuit',                  'BISCUIT', 'pack'),
    (48, 'Pistachio Biscuit',                   'BISCUIT', 'pack'),
    (49, 'Chocolate Puzzle Biscuit',            'BISCUIT', 'pack'),
    (50, 'Muffin Butter (1*4)',                 'MUFFIN',  'pack'),
    (51, 'Muffin Vanilla (1*12)',               'MUFFIN',  'pack'),
    (52, 'Muffin Mango (1*12)',                 'MUFFIN',  'pack'),
    (53, 'Muffin Chocolate (1*12)',             'MUFFIN',  'pack'),
    (54, 'Samosa Patti',                        'PASTRY',  'pack'),
    (55, 'Paratha 1*5',                         'PARATHA', 'pack'),
    (56, 'Paratha 1*20',                        'PARATHA', 'pack'),
    (57, 'Paratha 1*30',                        'PARATHA', 'pack'),
    (58, 'S/W Bran Bread',                      'BREAD',   'piece'),
    (59, 'MAC Royal B/Bun W/Seeded 4in (1X2)',  'BUN',     'pack'),
    (60, 'MAC Royal B/Bun B/Seeded 4in (1X2)',  'BUN',     'pack'),
    (61, 'MAC Royal Brioche B/Bun (1*2)',       'BUN',     'pack'),
    (62, 'Corn Meal B/Bun Uncut (1*2)',         'BUN',     'pack'),
    (63, 'Corn Meal B/Bun 4" (1*2)',            'BUN',     'pack'),
    (64, 'Potato B/Bun 4" (1*2)',               'BUN',     'pack'),
    (65, 'Hot Dog B/Bun (1*4)',                 'BUN',     'pack'),
    (66, 'Jumbo B/Bun B/Seeded (1*2)',          'BUN',     'pack'),
    (67, 'Boat Cake Plain (1X6)',               'CAKE',    'pack'),
    (68, 'Boat Cake Mango (1X6)',               'CAKE',    'pack'),
    (69, 'S/W Bread - Jumbo',                   'BREAD',   'piece'),
    (70, 'Panini Bread',                        'BREAD',   'pack')
) AS v(id, name, category, unit)
WHERE t.company_code = 'DAWNBREAD'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.tenant_id = t.id AND lower(p.name) = lower(v.name));

-- Keep the id counter past the fixed ids.
SELECT setval(pg_get_serial_sequence('products', 'id'), (SELECT max(id) FROM products));
