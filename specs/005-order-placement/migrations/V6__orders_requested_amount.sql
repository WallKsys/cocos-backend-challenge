-- CM-12: amount requested by the client for BUY / SELL orders placed by amount (DEC-14).
-- Stored so that a replay of the same request returns the original response, including the remaining amount
-- (DEC-09). NULL for orders placed by quantity, for cash movements and for seed rows.

ALTER TABLE orders
    ADD COLUMN requested_amount NUMERIC(18, 2),
    ADD CONSTRAINT orders_requested_amount_check
        CHECK (requested_amount IS NULL OR (requested_amount > 0 AND side IN ('BUY', 'SELL')));
