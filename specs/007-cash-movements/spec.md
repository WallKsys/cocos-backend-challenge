# Feature Specification: Deposits and withdrawals

**Feature Branch**: `007-cash-movements-<slice>` (one branch per PR slice, see `tasks.md`)

**Created**: 2026-11-21

**Status**: Approved for implementation

**Input**: R-09, R-11; DEC-04, DEC-05, DEC-07, DEC-09, DEC-12, DEC-13, DEC-17, DEC-23, DEC-24; RFC TEC-16, TEC-32;
acceptance scenarios **CU-05.01 to CU-05.08** and **CU-06.01 to CU-06.09**; Gherkin
`specs/007-cash-movements/features/cu-05-deposits.feature` and `cu-06-withdrawals.feature` (17 scenarios).

## Context

Users deposit pesos into their own account and withdraw pesos from their available cash. The challenge models
transfers as orders with side `CASH_IN` / `CASH_OUT` (R-11); they are stored in `orders` on the ARS instrument with
`price` 1 and `size` = amount, exactly like the seed data (DEC-04). These are domain extensions (level 2): not
endpoints requested by the challenge, but they exercise rules it defines, and they let users without balance operate.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Deposit pesos (Priority: P1)

**Acceptance Scenarios**: CU-05.01 to CU-05.08.

### User Story 2 - Withdraw pesos (Priority: P1)

**Acceptance Scenarios**: CU-06.01 to CU-06.09.

### Edge Cases

- A withdrawal is checked against **available** cash (cash − reserved), never against total cash (CU-06.02).
- A withdrawal without enough cash is stored as `REJECTED` (201); an invalid amount stores nothing (400) (CU-06.07).
- A deposit or withdrawal for an unknown user → 404 `NOT_FOUND`, `User <id> was not found.` (no CU; covered by an
  integration test).
- Deposits are never rejected for business reasons (there is no upper limit besides the column precision, enforced
  by `@DecimalString`: at most 16 integer digits).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `POST /api/v1/cash/deposits` and `POST /api/v1/cash/withdrawals` MUST implement
  `contracts/openapi.yaml` exactly.
- **FR-002**: The flow MUST follow Constitution Principle VII, sharing the idempotency lookup with order placement
  (plan.md § Shared idempotency lookup).
- **FR-003**: Every processed movement MUST be recorded with `BusinessMetrics.orderProcessed(side, MARKET, status,
  reason)`; replays and conflicts as in spec 005.

### Key Entities

`CashMovementRequest`, `CashMovement` (plan.md).

## Success Criteria *(mandatory)*

- **SC-001**: The 17 scenarios pass; `CU-05` and `CU-06` are in the traceability scope.
- **SC-002**: PRD M-07 (cash part): 50 simultaneous identical deposits with one key produce exactly one row.

## Assumptions

- The ARS instrument is found by ticker `ARS` and type `MONEDA` (id 66 in the seed; do not hard-code the id).
