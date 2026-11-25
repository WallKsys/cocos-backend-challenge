-- Volume dataset for PRD M-08 to M-11 (spec 009). Runs after migrations V1-V6, on top of the seed.
-- 100,000 extra instruments (with two days of market data), 1,000 extra users with 10,000 orders each
-- (10,000,000 orders). Every generated row satisfies the schema constraints of spec 002.
SET LOCAL TIME ZONE 'UTC';

INSERT INTO instruments (ticker, name, type)
SELECT 'V' || lpad(n::text, 6, '0'), 'Volume Instrument ' || n || ' S.A.', 'ACCIONES'
FROM generate_series(1, 100000) AS n;

INSERT INTO marketdata (instrumentid, date, open, high, low, close, previousclose)
SELECT i.id, d.day, 100.00, 110.00, 90.00,
       100.00 + (i.id % 900) + CASE WHEN d.day = DATE '2023-07-14' THEN 1.50 ELSE 0 END,
       100.00 + (i.id % 900)
FROM instruments i
CROSS JOIN (VALUES (DATE '2023-07-13'), (DATE '2023-07-14')) AS d(day)
WHERE i.ticker ~ '^V[0-9]{6}$';

INSERT INTO users (email, accountnumber)
SELECT 'volume' || n || '@test.com', (20000 + n)::text
FROM generate_series(1, 1000) AS n;

-- One large deposit per volume user (first order of each user).
INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime)
SELECT (SELECT id FROM instruments WHERE ticker = 'ARS'), u.id, 100000000.00, 1, 'MARKET', 'CASH_IN', 'FILLED',
       TIMESTAMPTZ '2023-01-01T00:00:00Z'
FROM users u WHERE u.email LIKE 'volume%';

-- 9,999 trades per volume user over 50 instruments: 7 of 10 FILLED buys, 1 FILLED sell, 1 NEW limit buy,
-- 1 CANCELLED limit buy. Positions stay positive (buys dominate).
INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime)
SELECT v.first_id + (s.n % 50),
       u.id,
       1 + (s.n % 10),
       100.00 + (s.n % 900),
       CASE WHEN s.n % 10 IN (8, 9) THEN 'LIMIT' ELSE 'MARKET' END,
       CASE WHEN s.n % 10 = 7 THEN 'SELL' ELSE 'BUY' END,
       CASE s.n % 10 WHEN 8 THEN 'NEW' WHEN 9 THEN 'CANCELLED' ELSE 'FILLED' END,
       TIMESTAMPTZ '2023-01-01T00:00:00Z' + (s.n || ' seconds')::interval
FROM users u
CROSS JOIN generate_series(1, 9999) AS s(n)
CROSS JOIN (SELECT min(id) AS first_id FROM instruments WHERE ticker ~ '^V[0-9]{6}$') AS v
WHERE u.email LIKE 'volume%';

ANALYZE;
