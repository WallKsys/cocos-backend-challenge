-- Spec 009, plan guard: search without criteria lists tradable instruments in ticker order (DEC-08).
CREATE INDEX instruments_tradable_ticker_idx ON instruments (ticker COLLATE "C") WHERE type <> 'MONEDA';
