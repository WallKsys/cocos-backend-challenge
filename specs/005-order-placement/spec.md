# Feature Specification: Order placement

**Feature Branch**: `005-order-placement-<slice>` (one branch per PR slice, see `tasks.md`)

**Created**: 2026-11-21

**Status**: Approved for implementation

**Input**: Challenge requirement "Enviar una orden al mercado"; R-04 to R-09, R-12, R-17; DEC-01, DEC-05, DEC-06,
DEC-07, DEC-09, DEC-12, DEC-14, DEC-17, DEC-18, DEC-22, DEC-23, DEC-24; RFC TEC-14, TEC-16, TEC-17, TEC-19, TEC-20,
TEC-31; acceptance scenarios **CU-03.01 to CU-03.42**; Gherkin
`specs/005-order-placement/features/cu-03-order-placement.feature` (42 scenarios, already written).

## Context

The heart of the system. A user sends a BUY or SELL order, MARKET or LIMIT, by quantity or by amount. The order is
validated, evaluated against the user's **available** cash or shares (reservations of NEW orders excluded), and
stored with its resulting status. Everything happens in one transaction, under a lock of the user's row, with
idempotency by client key.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Buy and sell at market (Priority: P1)

**Acceptance Scenarios**: CU-03.01 to CU-03.12, CU-03.39.

### User Story 2 - Limit orders that reserve what they commit (Priority: P1)

**Acceptance Scenarios**: CU-03.13 to CU-03.19, CU-03.38, CU-03.40, CU-03.43 to CU-03.46 (price band and maximum
quantity, DEC-29 and DEC-30).

### User Story 3 - Invalid requests store nothing (Priority: P1)

**Acceptance Scenarios**: CU-03.20 to CU-03.30.

### User Story 4 - An order is executed at most once, and concurrent orders never overspend (Priority: P1)

**Acceptance Scenarios**: CU-03.31 to CU-03.36, CU-03.41, CU-03.42.

### User Story 5 - Orders are timestamped in UTC (Priority: P2)

**Acceptance Scenarios**: CU-03.37.

### Edge Cases

- Validation precedence: request format (Bean Validation and JSON parsing) → cross-field rules (domain) → user
  existence (lock) → idempotency → instrument existence and tradability → business evaluation. The first failing
  step produces the response.
- An invalid request (any 400/404 before the order is stored) never consumes the idempotency key (CU-03.35).
- A replay returns the stored order exactly as it was first returned, even if balances or prices changed since
  (CU-03.34).
- The idempotency key is unique per user across orders, deposits and withdrawals (CU-03.42).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `POST /api/v1/orders` MUST implement `contracts/openapi.yaml` exactly.
- **FR-002**: The evaluation rules, rejection reasons and messages MUST be exactly those of plan.md § Order
  evaluation.
- **FR-003**: Validation errors MUST use the exact details of plan.md § Validation.
- **FR-004**: The flow MUST follow Constitution Principle VII (lock → idempotency → evaluate → store).
- **FR-005**: Every processed order MUST be recorded with `BusinessMetrics.orderProcessed`; replays with
  `idempotentReplay`; conflicts with `idempotencyConflict`; the lock wait with `accountLockWaited`.
- **FR-006**: Migrations V6 and V7 MUST be copied byte-identical from `specs/005-order-placement/migrations/`.

### Key Entities

`OrderRequest`, `Order`, `Account`, `CashBalance`, `ShareBalance`, `Instrument`, `OrderEvaluation` (plan.md).

## Success Criteria *(mandatory)*

- **SC-001**: The 42 scenarios of the feature file pass.
- **SC-002**: PRD M-02, M-04, M-05 and M-06 are verified by integration tests (§ Tests in plan.md).
- **SC-003**: `CU-03` is in the traceability scope; the published contract matches the routes.

## Assumptions

- Market data is static; the last close is the latest `marketdata` row (spec 004 `MarketQuote`).
- Deposits do not exist yet as an endpoint (spec 007): scenarios that need one insert it with SQL.
