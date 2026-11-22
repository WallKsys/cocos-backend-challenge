-- CM-05: normalized ticker and name (lower case, no accents) with trigram indexes for partial search (DEC-08, DEC-21).

CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- unaccent() is not IMMUTABLE, so it cannot be used in generated columns or indexes directly.
-- This wrapper fixes the dictionary, which makes the result deterministic.
CREATE FUNCTION immutable_unaccent(input TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, input) $$;

ALTER TABLE instruments
    ADD COLUMN ticker_normalized TEXT GENERATED ALWAYS AS (lower(ticker)) STORED,
    ADD COLUMN name_normalized TEXT GENERATED ALWAYS AS (lower(immutable_unaccent(name))) STORED;

CREATE INDEX instruments_ticker_normalized_trgm_idx ON instruments USING gin (ticker_normalized gin_trgm_ops);
CREATE INDEX instruments_name_normalized_trgm_idx ON instruments USING gin (name_normalized gin_trgm_ops);
CREATE UNIQUE INDEX instruments_ticker_normalized_key ON instruments (ticker_normalized);
