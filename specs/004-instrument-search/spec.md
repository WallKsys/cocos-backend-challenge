# Feature Specification: Instrument search

**Feature Branch**: `004-instrument-search-<slice>` (one branch per PR slice, see `tasks.md`)

**Created**: 2026-11-21

**Status**: Implemented (2026-11-25)

**Input**: Challenge requirement "Buscar activos"; DEC-08, DEC-19, DEC-21; acceptance scenarios **CU-02.01 to
CU-02.15** (`docs/casos-de-uso.md`); Gherkin `specs/004-instrument-search/features/cu-02-instrument-search.feature`
(already written — copied byte-identical into `src/test/resources/features/`, never edited).

## Context

Users search the market to find instruments to trade, by ticker and/or name. This is the first business endpoint.
It is read-only and public market data: it needs no `X-User-Id` and no idempotency key.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find instruments by text (Priority: P1)

A user types part of a ticker or a name and gets the matching instruments, best matches first.

**Independent Test**: `GET /api/v1/instruments?q=...` returns the expected tickers in the expected order.

**Acceptance Scenarios**: CU-02.01, CU-02.02, CU-02.03, CU-02.04, CU-02.05, CU-02.05b, CU-02.05c, CU-02.08,
CU-02.09 (see the feature file for exact data).

### User Story 2 - Narrow the search with filters (Priority: P2)

**Acceptance Scenarios**: CU-02.06, CU-02.13, CU-02.14, CU-02.15.

### User Story 3 - See market data in each result (Priority: P1)

**Acceptance Scenarios**: CU-02.10, CU-02.11.

### User Story 4 - Browse the whole market page by page (Priority: P2)

**Acceptance Scenarios**: CU-02.07, CU-02.12.

### Edge Cases

- `%`, `_` and `\` typed by the user are literal characters, never wildcards (a search for `50%` does not match
  everything containing `50`).
- Blank parameters (`q=` or `q=%20%20`) are treated as absent.
- A parameter longer than 100 characters → 400 `VALIDATION_ERROR`.
- `limit` outside 1–100 or not an integer → 400 `VALIDATION_ERROR`.
- A cursor that cannot be decoded → 400 `VALIDATION_ERROR` with detail
  `Request validation failed: 'cursor' is invalid.`
- An instrument with market data but without `previousclose` (or with `previousclose = 0`) has a last price and a
  `null` daily change.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `GET /api/v1/instruments` MUST implement the contract `contracts/openapi.yaml` exactly.
- **FR-002**: Matching rules (DEC-08, DEC-21):
  - `q` with 3+ characters: ticker **or** name contains `q`; with 1–2 characters: ticker equals `q`.
  - `ticker` with 3+ characters: ticker contains it; with 1–2 characters: ticker equals it.
  - `name` with 3+ characters: name contains it; with 1–2 characters: 400 `SEARCH_TERM_TOO_SHORT`, detail
    `The name filter requires at least 3 characters.`
  - All present criteria must match. Case and accents are ignored. `MONEDA` instruments are never returned.
  - Lengths are measured after trimming, in characters.
- **FR-003**: Order: when `q` is present, rank 0 = ticker equals `q`, rank 1 = ticker starts with `q`, rank 2 = any
  other match; without `q` every result has rank 0. Within a rank, by ticker in binary (`C` collation) order.
- **FR-004**: Each result has `id`, `ticker`, `name`, `type`, `lastPrice` (latest `close`) and `dailyChangePct`
  (`(close − previousClose) / previousClose × 100`, rounded HALF_UP to 2 decimals); both `null` without market data.
- **FR-005**: Pagination by cursor; default `limit` 20, maximum 100; `nextCursor` is `null` on the last page; a page
  never repeats or skips an instrument.
- **FR-006**: The endpoint needs no `X-User-Id` and no `Idempotency-Key`.

### Key Entities

`InstrumentSummary`, `MarketQuote`, `Percentage`, `InstrumentSearchCriteria`, `SearchCursor`, `InstrumentPage`
(see plan.md).

## Success Criteria *(mandatory)*

- **SC-001**: The 17 scenarios of `cu-02-instrument-search.feature` pass (21 runs counting outline examples).
- **SC-002**: `CU-02` is in the traceability scope and the traceability check passes.
- **SC-003**: The published contract equals this spec's contract and matches the application routes.

## Assumptions

- Market data is static (seed); the "latest" market data of an instrument is the row with the greatest `date`.
