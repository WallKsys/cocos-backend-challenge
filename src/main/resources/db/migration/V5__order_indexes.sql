-- CM-07 / CM-08: indexes for per-user aggregation, reservations and cursor-paginated history (TEC-17).

-- Cash and positions of one user: index-only scans over FILLED orders grouped by instrument.
CREATE INDEX orders_user_status_instrument_idx
    ON orders (userid, status, instrumentid) INCLUDE (side, size, price, datetime);

-- Reservations: only the user's NEW orders.
CREATE INDEX orders_user_new_idx
    ON orders (userid) INCLUDE (instrumentid, side, size, price)
    WHERE status = 'NEW';

-- History: newest first, stable cursor (datetime, id).
CREATE INDEX orders_user_history_idx
    ON orders (userid, datetime DESC, id DESC);
