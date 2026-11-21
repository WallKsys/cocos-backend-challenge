-- CM-04: unique ticker. CM-10: integrity constraints. CM-11: timestamps in UTC (DEC-18).
-- Existing seed rows satisfy every constraint below.

ALTER TABLE users
    ALTER COLUMN email SET NOT NULL,
    ALTER COLUMN accountnumber SET NOT NULL,
    ADD CONSTRAINT users_accountnumber_key UNIQUE (accountnumber);

ALTER TABLE instruments
    ALTER COLUMN ticker SET NOT NULL,
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN type SET NOT NULL,
    ADD CONSTRAINT instruments_ticker_key UNIQUE (ticker),
    ADD CONSTRAINT instruments_type_check CHECK (type IN ('ACCIONES', 'MONEDA'));

-- The unique constraint also serves "latest price of an instrument" (CM-06) with a backward index scan.
ALTER TABLE marketdata
    ALTER COLUMN instrumentid SET NOT NULL,
    ALTER COLUMN date SET NOT NULL,
    ALTER COLUMN close SET NOT NULL,
    ADD CONSTRAINT marketdata_instrument_date_key UNIQUE (instrumentid, date);

ALTER TABLE orders
    ALTER COLUMN instrumentid SET NOT NULL,
    ALTER COLUMN userid SET NOT NULL,
    ALTER COLUMN size SET NOT NULL,
    ALTER COLUMN type SET NOT NULL,
    ALTER COLUMN side SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN datetime SET NOT NULL,
    ADD CONSTRAINT orders_side_check CHECK (side IN ('BUY', 'SELL', 'CASH_IN', 'CASH_OUT')),
    ADD CONSTRAINT orders_type_check CHECK (type IN ('MARKET', 'LIMIT')),
    ADD CONSTRAINT orders_status_check CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED')),
    ADD CONSTRAINT orders_size_not_negative_check CHECK (size >= 0);

-- Seed timestamps carry no time zone: they are interpreted as UTC (PRD SUP-03).
ALTER TABLE orders
    ALTER COLUMN datetime TYPE TIMESTAMPTZ USING datetime AT TIME ZONE 'UTC';
