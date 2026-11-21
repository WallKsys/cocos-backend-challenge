# Data model (after migrations V1–V5)

Column names of the original schema are **unquoted camelCase in the provided script, so PostgreSQL stores them in
lower case** (`instrumentid`, `userid`, `accountnumber`, `previousclose`). Always write them in lower case in SQL.
Columns added by this project use `snake_case`.

## users

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `SERIAL` | no | PK |
| `email` | `VARCHAR(255)` | no | |
| `accountnumber` | `VARCHAR(20)` | no | unique (`users_accountnumber_key`). Shown in rejection messages of the user's own account |

The row of a user is the **lock target** of every operation with effect (TEC-16):
`SELECT id FROM users WHERE id = :userId FOR UPDATE`.

## instruments

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `SERIAL` | no | PK |
| `ticker` | `VARCHAR(10)` | no | unique (`instruments_ticker_key`) |
| `name` | `VARCHAR(255)` | no | |
| `type` | `VARCHAR(10)` | no | `ACCIONES` or `MONEDA` (`instruments_type_check`). ARS (`MONEDA`, id 66) is cash |
| `ticker_normalized` | `TEXT` | — | generated: `lower(ticker)`; unique |
| `name_normalized` | `TEXT` | — | generated: `lower(immutable_unaccent(name))` |

Search (spec 004) matches against `ticker_normalized` and `name_normalized`, normalizing the user's input the same
way in SQL: `lower(immutable_unaccent(:text))`.

## marketdata

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `SERIAL` | no | PK |
| `instrumentid` | `INT` | no | FK → `instruments.id` |
| `high`, `low`, `open` | `NUMERIC(18,2)` | yes | DOME has `NULL` values in the seed |
| `close` | `NUMERIC(18,2)` | no | last price |
| `previousclose` | `NUMERIC(18,2)` | yes | previous day's close |
| `date` | `DATE` | no | unique per instrument (`marketdata_instrument_date_key`) |

**Latest price of an instrument** = the row with the greatest `date` for that `instrumentid`. Seed: two dates
(2023-07-13 and 2023-07-14) for 63 instruments; **no rows for PGR (3), IRCP (10) and ARS (66)**.

## orders

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `SERIAL` | no | PK |
| `instrumentid` | `INT` | no | FK → `instruments.id`. ARS (66) for `CASH_IN` / `CASH_OUT` |
| `userid` | `INT` | no | FK → `users.id` |
| `size` | `NUMERIC(18,2)` | no | `>= 0`. Shares (whole number) for `BUY`/`SELL`; amount in pesos for cash movements |
| `price` | `NUMERIC(18,2)` | yes | execution or limit price; `1` for cash movements; `NULL` only for `REJECTED` with `NO_MARKET_PRICE` |
| `type` | `VARCHAR(10)` | no | `MARKET`, `LIMIT` |
| `side` | `VARCHAR(10)` | no | `BUY`, `SELL`, `CASH_IN`, `CASH_OUT` |
| `status` | `VARCHAR(20)` | no | `NEW`, `FILLED`, `REJECTED`, `CANCELLED` |
| `datetime` | `TIMESTAMPTZ` | no | UTC (DEC-18); set by the server |
| `rejection_code` | `VARCHAR(40)` | yes | only when `REJECTED`: `INSUFFICIENT_FUNDS`, `INSUFFICIENT_SHARES`, `AMOUNT_BELOW_PRICE`, `NO_MARKET_PRICE` |
| `rejection_message` | `VARCHAR(500)` | yes | only when `REJECTED`; English, with the values at rejection time |
| `idempotency_key` | `VARCHAR(255)` | yes | client key; `NULL` only in seed rows |
| `request_fingerprint` | `CHAR(64)` | yes | hex SHA-256 of the normalized request; present iff `idempotency_key` is |

### Constraints on `orders`

| Name | Rule |
|---|---|
| `orders_side_check`, `orders_type_check`, `orders_status_check` | allowed values above |
| `orders_size_not_negative_check` | `size >= 0` (a `REJECTED` order with `AMOUNT_BELOW_PRICE` stores size 0) |
| `orders_whole_shares_check` | `BUY`/`SELL` sizes are whole numbers |
| `orders_rejection_only_when_rejected_check` | rejection fields only on `REJECTED` orders |
| `orders_rejection_code_check` | closed list of rejection codes |
| `orders_idempotency_pair_check` | key and fingerprint are both present or both absent |
| `orders_user_idempotency_key` | unique `(userid, idempotency_key)`: one key per user across all operations (DEC-23) |

## Indexes

| Name | Table | Definition | Used by |
|---|---|---|---|
| `marketdata_instrument_date_key` | marketdata | unique `(instrumentid, date)` | latest price (backward scan) |
| `instruments_ticker_key` | instruments | unique `(ticker)` | lookup by ticker |
| `instruments_ticker_normalized_key` | instruments | unique `(ticker_normalized)` | exact-ticker search, ordering |
| `instruments_ticker_normalized_trgm_idx` | instruments | GIN trigram on `ticker_normalized` | partial search |
| `instruments_name_normalized_trgm_idx` | instruments | GIN trigram on `name_normalized` | partial search |
| `orders_user_idempotency_key` | orders | unique `(userid, idempotency_key)` | idempotency lookup |
| `orders_user_status_instrument_idx` | orders | `(userid, status, instrumentid) INCLUDE (side, size, price, datetime)` | cash and positions |
| `orders_user_new_idx` | orders | `(userid) INCLUDE (instrumentid, side, size, price) WHERE status = 'NEW'` | reservations |
| `orders_user_history_idx` | orders | `(userid, datetime DESC, id DESC)` | history with cursor |

## Ledger formulas (reference SQL)

Used by the tests of this spec and as the starting point of the persistence adapters of specs 005–008.
`:userId` is a named parameter.

```sql
-- Cash from FILLED orders
SELECT COALESCE(SUM(CASE
           WHEN side = 'CASH_IN'  THEN  size
           WHEN side = 'CASH_OUT' THEN -size
           WHEN side = 'BUY'      THEN -size * price
           WHEN side = 'SELL'     THEN  size * price
       END), 0) AS cash
FROM orders
WHERE userid = :userId AND status = 'FILLED';

-- Cash reserved by NEW BUY orders
SELECT COALESCE(SUM(size * price), 0) AS reserved_cash
FROM orders
WHERE userid = :userId AND status = 'NEW' AND side = 'BUY';

-- Shares per instrument from FILLED orders
SELECT instrumentid,
       SUM(CASE WHEN side = 'BUY' THEN size ELSE -size END) AS quantity
FROM orders
WHERE userid = :userId AND status = 'FILLED' AND side IN ('BUY', 'SELL')
GROUP BY instrumentid;

-- Shares reserved by NEW SELL orders, per instrument
SELECT instrumentid, SUM(size) AS reserved_quantity
FROM orders
WHERE userid = :userId AND status = 'NEW' AND side = 'SELL'
GROUP BY instrumentid;

-- Latest market data of one instrument
SELECT close, previousclose, date
FROM marketdata
WHERE instrumentid = :instrumentId
ORDER BY date DESC
LIMIT 1;
```

Expected values for the seed (user 1): cash `753000.00`, reserved cash `125500.00`, available `627500.00`;
quantities PAMP (47) `40`, METR (54) `500`, BMA (31) `-10`; no NEW SELL orders.

## Schema changes implemented here

| Migration | Changes |
|---|---|
| `V1__original_schema_and_seed.sql` | verbatim `docs/referencia/database.sql` |
| `V2__integrity_constraints_and_utc_timestamps.sql` | CM-04 unique ticker; CM-10 NOT NULL and CHECK constraints, unique account number, unique market data per day; CM-11 `TIMESTAMPTZ` in UTC |
| `V3__orders_rejection_idempotency_and_money_precision.sql` | CM-01 rejection code and message; CM-02 idempotency key and fingerprint; CM-03 / CM-09 `NUMERIC(18,2)` |
| `V4__instrument_search.sql` | CM-05 normalized search columns and trigram indexes |
| `V5__order_indexes.sql` | CM-07 / CM-08 order indexes (CM-06 is covered by the unique constraint of V2) |
