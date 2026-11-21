# Feature Specification: Internal tools and volume verification

**Feature Branch**: `009-internal-tools-volume-<slice>` (one branch per PR slice, see `tasks.md`)

**Created**: 2026-11-21

**Status**: Approved for implementation

**Input**: Catalog level 3 (I-01 to I-05, DEC-03, DEC-16); PRD M-08 to M-11, RSK-01, PA-02, PA-03; RFC TEC-17,
TEC-18, TEC-24; acceptance scenarios **CU-I.01 to CU-I.09**; Gherkin
`specs/009-internal-tools-volume/features/cu-i-internal-tools.feature` (9 scenarios).

## Context

Two independent deliverables:

1. **Internal tools** for demos (level 3, **not part of the challenge**): delete an order, force the execution of a
   pending limit order, change a price, restore the seed data and create a test user. They exist only when internal
   tools are enabled, require `X-Role: ADMIN`, and may break invariants on purpose.
2. **Volume verification**: a separate Maven profile that loads 10,000,000 orders and 100,000 instruments and checks
   the latency targets of the PRD, plus a **plan guard** in the normal build that fails if a critical query cannot
   use an index.

It also closes two loose ends: CU-04.08 switches from its test simulator to the real forced-execution tool, and the
traceability scope becomes complete.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Demo tools (Priority: P3)

**Acceptance Scenarios**: CU-I.01 to CU-I.09.

### User Story 2 - Proof that the design scales (Priority: P2)

**Independent Test**: `./mvnw verify -Pvolume` loads the volume dataset and asserts p95/p99 latencies of M-08 to
M-11; it prints a table with the measured values.

### User Story 3 - Queries can never silently lose their indexes (Priority: P1)

**Independent Test**: `PlanGuardIT` (normal build) runs `EXPLAIN` on every critical query with sequential scans
disabled and fails if any of them still reads `orders`, `instruments` or `marketdata` sequentially.

### Edge Cases

- Every tool on an unknown order or instrument → 404 (`Order <id> was not found.` / `Instrument <id> was not
  found.`).
- Setting market data of ARS → 400 `Request validation failed: 'instrumentId' must reference a tradable instrument;
  ARS has no market data.`; `close` ≤ 0 or `previousClose` ≤ 0 → 400 `... must be greater than 0.`
- Forcing the execution of a MARKET order is impossible in practice (MARKET orders are never `NEW`); the rule is
  "only `NEW` orders", whatever the type.
- Creating a user without email or with an invalid email → 400 `VALIDATION_ERROR`.
- Restoring the seed data while operations are in flight: the restore waits for them (table locks) and runs alone.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The internal endpoints MUST implement `contracts/openapi.yaml` exactly, live in
  `infrastructure.adapter.in.rest.internal`, and be conditional and guarded as in spec 003.
- **FR-002**: Tools that change a user's orders (delete, execute) MUST lock that user's row first (Principle VII);
  execute changes the order only if it is still `NEW` (`UPDATE ... WHERE status = 'NEW'`).
- **FR-003**: New error code `ORDER_NOT_EXECUTABLE` (409): `Order <id> cannot be executed because its status is
  <STATUS>. Only NEW orders can be executed.`
- **FR-004**: The volume profile MUST NOT run in the default build.
- **FR-005**: The plan guard MUST cover every query listed in plan.md § Plan guard.
- **FR-006**: The traceability scope MUST contain every CU group of `docs/casos-de-uso.md` (`CU-01` to `CU-07`,
  `CU-I`), and a test MUST fail if a group is missing.

## Success Criteria *(mandatory)*

- **SC-001**: The 9 CU-I scenarios pass; CU-04.08 passes with the real tool.
- **SC-002**: `./mvnw verify -Pvolume` meets M-08 to M-11 on the reference environment (plan.md § Reference
  environment).
- **SC-003**: `PlanGuardIT` green in the default build.
