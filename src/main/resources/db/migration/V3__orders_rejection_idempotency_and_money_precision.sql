-- CM-01: rejection reason. CM-02: idempotency key and request fingerprint (DEC-07, DEC-09, DEC-23, TEC-16).
-- CM-03 + CM-09: money columns NUMERIC(18,2); orders.size accepts cents for CASH_IN / CASH_OUT (DEC-13).

ALTER TABLE orders
    ALTER COLUMN size TYPE NUMERIC(18, 2),
    ALTER COLUMN price TYPE NUMERIC(18, 2),
    ADD COLUMN rejection_code VARCHAR(40),
    ADD COLUMN rejection_message VARCHAR(500),
    ADD COLUMN idempotency_key VARCHAR(255),
    ADD COLUMN request_fingerprint CHAR(64);

ALTER TABLE orders
    -- One key per user across all operation types. NULL keys (seed rows) are not compared.
    ADD CONSTRAINT orders_user_idempotency_key UNIQUE (userid, idempotency_key),
    ADD CONSTRAINT orders_idempotency_pair_check
        CHECK ((idempotency_key IS NULL) = (request_fingerprint IS NULL)),
    -- Only REJECTED orders may carry a rejection reason (the seed REJECTED order has none).
    ADD CONSTRAINT orders_rejection_only_when_rejected_check
        CHECK (status = 'REJECTED' OR (rejection_code IS NULL AND rejection_message IS NULL)),
    ADD CONSTRAINT orders_rejection_code_check
        CHECK (rejection_code IS NULL
               OR rejection_code IN ('INSUFFICIENT_FUNDS', 'INSUFFICIENT_SHARES', 'AMOUNT_BELOW_PRICE', 'NO_MARKET_PRICE')),
    -- Shares are whole numbers; only cash movements may have cents (R-08, DEC-13).
    ADD CONSTRAINT orders_whole_shares_check
        CHECK (side IN ('CASH_IN', 'CASH_OUT') OR size = trunc(size));

ALTER TABLE marketdata
    ALTER COLUMN high TYPE NUMERIC(18, 2),
    ALTER COLUMN low TYPE NUMERIC(18, 2),
    ALTER COLUMN open TYPE NUMERIC(18, 2),
    ALTER COLUMN close TYPE NUMERIC(18, 2),
    ALTER COLUMN previousclose TYPE NUMERIC(18, 2);
