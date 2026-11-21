# Implementation Plan: Database

**Branch**: `002-database-<slice>` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

## Summary

Add the provided `database.sql` as Flyway migration V1 (byte-identical) and the four verified migrations V2–V5
from `specs/002-database/migrations/`. Verify the result with integration tests that use SQL directly. No
production Java code is written in this spec.

## Technical Context

**Language/Version**: SQL (PostgreSQL 18), Java 25 for tests only

**Primary Dependencies**: Flyway (via `spring-boot-starter-flyway`, already in `pom.xml`)

**Storage**: PostgreSQL 18

**Testing**: integration tests with the shared Testcontainers PostgreSQL (`@IntegrationTest` from spec 001) and
one unit test

**Constraints**: migrations are copied byte-identical; never edit a committed migration

## Constitution Check

| Principle | How this spec complies |
|---|---|
| I Ledger | Reference ledger queries verified against the seed (data-model.md) |
| III Test-first | Tests are written first and fail because the migrations do not exist yet |
| VI Explicit SQL | Migrations are plain SQL; no ORM |
| VIII Money | `NUMERIC(18,2)` (CM-09); tests compare `BigDecimal` with `compareTo` |
| X PR size | Two slices; V1 is excluded from the count |

No violations.

## Project Structure

```text
src/main/resources/db/migration/
├── V1__original_schema_and_seed.sql                          # copy of docs/referencia/database.sql
├── V2__integrity_constraints_and_utc_timestamps.sql          # copy of specs/002-database/migrations/
├── V3__orders_rejection_idempotency_and_money_precision.sql  # copy
├── V4__instrument_search.sql                                 # copy
└── V5__order_indexes.sql                                     # copy
(delete src/main/resources/db/migration/.gitkeep)

src/test/java/com/cocos/challenge/database/
├── OriginalScriptIsUnchangedTest.java     # unit
├── SeedDataIT.java                        # integration
├── LedgerReferenceQueriesIT.java          # integration
├── SchemaConstraintsIT.java               # integration
├── TimestampsAndSearchColumnsIT.java      # integration
└── IndexesIT.java                         # integration
```

## How to copy the migrations

Copy with the shell, never by retyping, so the files stay byte-identical:

```bash
cp docs/referencia/database.sql src/main/resources/db/migration/V1__original_schema_and_seed.sql
cp specs/002-database/migrations/V2__*.sql specs/002-database/migrations/V3__*.sql \
   specs/002-database/migrations/V4__*.sql specs/002-database/migrations/V5__*.sql \
   src/main/resources/db/migration/
```

## Tests

All integration tests use `@IntegrationTest` (spec 001). Tests that **write** to the database are annotated with
Spring's `@Transactional` so every change is rolled back and the shared database stays at the seed state. They
use `JdbcClient` directly (tests are outside the scope of the ArchUnit rules). Compare money with
`isEqualByComparingTo` (AssertJ), never `isEqualTo`, because SQL products have scale 4.

| Test class | Kind | Verifies (spec scenario) |
|---|---|---|
| `OriginalScriptIsUnchangedTest` | unit, `@Tag("unit")` | V1 file bytes equal `docs/referencia/database.sql` bytes (US1-5) |
| `SeedDataIT` | integration | `flyway_schema_history` has versions 1–5 successful; counts 4 / 66 / 126 / 11; PGR, IRCP, ARS have no market data; users 2–4 have no orders (US1-1, US1-4) |
| `LedgerReferenceQueriesIT` | integration | the five reference queries of data-model.md return the expected values for user 1 (US1-2, US1-3) |
| `SchemaConstraintsIT` | integration, `@Transactional` | every row of § Constraint checks (US2-1 to US2-7) |
| `TimestampsAndSearchColumnsIT` | integration | first seed order is `2023-07-12T12:11:20Z` read as `OffsetDateTime`/`Instant` after `SET TIME ZONE 'America/Argentina/Buenos_Aires'`; `name_normalized LIKE '%rio%'` returns exactly BHIP, MOLI, OEST, RICH; PAMP's `ticker_normalized` is `pamp` (US3) |
| `IndexesIT` | integration | every index name of data-model.md § Indexes exists in `pg_indexes` (US4) |

No test of this spec carries a CU tag: these are infrastructure guarantees, not acceptance scenarios.

### Constraint checks

Each invalid insert must fail with a `DataIntegrityViolationException` (or the JDBC exception it wraps) whose
message contains the constraint name. Each valid insert must succeed. Use a 64-character string such as
`"a".repeat(64)` as fingerprint.

| # | Insert | Expected |
|---|---|---|
| 1 | instrument with ticker `PAMP` | fails: `instruments_ticker_key` |
| 2 | order with side `HOLD` | fails: `orders_side_check` |
| 3 | order with type `STOP` | fails: `orders_type_check` |
| 4 | order with status `PENDING` | fails: `orders_status_check` |
| 5 | `BUY` order with size `1.5` | fails: `orders_whole_shares_check` |
| 6 | `CASH_IN` of `10.55` on instrument 66 | succeeds |
| 7 | `FILLED` order with `rejection_code = 'INSUFFICIENT_FUNDS'` | fails: `orders_rejection_only_when_rejected_check` |
| 8 | `REJECTED` order with `rejection_code = 'SOMETHING_ELSE'` | fails: `orders_rejection_code_check` |
| 9 | order with `idempotency_key` and no fingerprint | fails: `orders_idempotency_pair_check` |
| 10 | user 1 key `k1`, then user 1 key `k1` again | second fails: `orders_user_idempotency_key` |
| 11 | user 1 key `k1`, then user 2 key `k1` | both succeed |
| 12 | two orders without key for the same user | both succeed |
| 13 | `CASH_IN` of `99999999999.99` | succeeds |
| 14 | order with `size = -1` | fails: `orders_size_not_negative_check` |

## Verification

```bash
./mvnw verify
docker compose up --build   # the application starts with the migrated database
docker compose exec postgres psql -U cocos -d cocos -c "select version, success from flyway_schema_history order by installed_rank"
```
