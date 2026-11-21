# Feature Specification: Database

**Feature Branch**: `002-database-<slice>` (one branch per PR slice, see `tasks.md`)

**Created**: 2026-11-21

**Status**: Approved for implementation

**Input**: Functional analysis § 8 (schema changes CM-01 to CM-11), DEC-07, DEC-12, DEC-13, DEC-18, DEC-23,
RFC TEC-12, TEC-16, TEC-17, TEC-20. Original schema and data: `docs/referencia/database.sql`.

## Context

The challenge provides a working schema with seed data (`database.sql`). This spec loads it **unchanged** as the
first Flyway migration and then applies, as separate and justified migrations, every schema change that the
functional and technical decisions require. It delivers no business logic: it guarantees that the data every
later spec builds on is correct, constrained and indexed, and verifies it with SQL before any Java logic exists.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The provided data loads unchanged (Priority: P1)

Whoever starts the application gets exactly the database the challenge provides: same tables, same rows.

**Why this priority**: every expected number in `docs/casos-de-uso.md` is computed from this data.

**Independent Test**: after migrations, row counts and the derived state of user 1 match the values below.

**Acceptance Scenarios**:

1. **Given** an empty database, **When** the application starts, **Then** Flyway applies migrations V1 to V5 in
   order and the database has 4 users, 66 instruments, 126 market data rows and 11 orders.
2. **Given** the migrated database, **When** the ledger of user 1 is computed with the reference queries of
   `plan.md`, **Then**: cash 753000.00, reserved cash 125500.00, available cash 627500.00; positions PAMP 40,
   METR 500, BMA −10.
3. **Given** the migrated database, **When** the latest market data of PAMP is read, **Then** close is 925.85,
   previous close 921.80 and date 2023-07-14.
4. **Given** the migrated database, **Then** PGR, IRCP and ARS have no market data, and users 2, 3 and 4 have no
   orders.
5. **Given** the file `src/main/resources/db/migration/V1__original_schema_and_seed.sql`, **Then** it is
   byte-identical to `docs/referencia/database.sql`.

---

### User Story 2 - The database rejects invalid data (Priority: P1)

Even if application code has a bug, the database refuses rows that break the business rules.

**Why this priority**: defense in depth for the money invariants (Constitution Principle I).

**Independent Test**: each invalid insert listed in `plan.md` § Constraint checks fails with the named
constraint; each valid insert succeeds.

**Acceptance Scenarios**:

1. **Given** the migrated database, **When** an instrument with an existing ticker is inserted, **Then** it fails
   (`instruments_ticker_key`).
2. **When** an order with an unknown side, type or status is inserted, **Then** it fails
   (`orders_side_check`, `orders_type_check`, `orders_status_check`).
3. **When** a `BUY` or `SELL` order with a fractional size is inserted, **Then** it fails
   (`orders_whole_shares_check`); a `CASH_IN` of 10.55 succeeds.
4. **When** a non-`REJECTED` order with a rejection code is inserted, **Then** it fails
   (`orders_rejection_only_when_rejected_check`).
5. **When** an order has an idempotency key but no fingerprint (or vice versa), **Then** it fails
   (`orders_idempotency_pair_check`).
6. **When** the same user reuses an idempotency key, **Then** it fails (`orders_user_idempotency_key`); a
   different user with the same key succeeds; any number of rows without key succeed.
7. **When** a `CASH_IN` of 99999999999.99 is inserted, **Then** it succeeds (money columns are
   `NUMERIC(18,2)`).

---

### User Story 3 - Timestamps are UTC and search columns are normalized (Priority: P2)

**Acceptance Scenarios**:

1. **Given** the migrated database, **When** the first seed order is read, **Then** its timestamp is
   `2023-07-12T12:11:20Z` regardless of the session time zone (stored as `TIMESTAMPTZ`, DEC-18).
2. **When** instruments are filtered by `name_normalized LIKE '%rio%'`, **Then** BHIP, MOLI, OEST and RICH are
   returned (accents removed, lower case).
3. **Then** `ticker_normalized` of PAMP is `pamp`.

---

### User Story 4 - Indexes for volume exist (Priority: P2)

**Acceptance Scenarios**:

1. **Given** the migrated database, **Then** the indexes listed in `data-model.md` § Indexes exist with those
   exact names.

### Edge Cases

- Multiplying two `NUMERIC(18,2)` values yields scale 4 (`753000.0000`); Java code must scale results to 2
  decimals (Constitution VIII). Tests compare with `BigDecimal.compareTo`, not `equals`.
- The seed `REJECTED` order has no rejection code: allowed by design (CM-01 applies to new rejections only).
- Seed rows have no idempotency key: allowed (`NULL` keys are never compared by the unique constraint).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `V1__original_schema_and_seed.sql` MUST be a byte-identical copy of `docs/referencia/database.sql`.
- **FR-002**: Migrations V2 to V5 MUST be copied **byte-identical** from `specs/002-database/migrations/`
  (they were verified against PostgreSQL 18.7).
- **FR-003**: No migration may be edited after it is committed (Constitution VI).
- **FR-004**: Every acceptance scenario above MUST have an integration test.

### Key Entities

See [data-model.md](data-model.md): `users`, `instruments`, `marketdata`, `orders`.

## Success Criteria *(mandatory)*

- **SC-001**: `./mvnw verify` green with all the tests of this spec.
- **SC-002**: `docker compose up --build` starts with the migrated, seeded database.

## Assumptions

- PostgreSQL contrib extensions `unaccent` and `pg_trgm` are available (they are in the official `postgres:18`
  image) and the database user may create them (true for the Compose and Testcontainers users).
