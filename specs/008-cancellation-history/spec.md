# Feature Specification: Order cancellation and history

**Feature Branch**: `008-cancellation-history-<slice>` (one branch per PR slice, see `tasks.md`)

**Created**: 2026-11-21

**Status**: Implemented (2026-11-25)

**Input**: R-10; DEC-01, DEC-15, DEC-16, DEC-17, DEC-18; RFC TEC-16, TEC-18, TEC-19; acceptance scenarios
**CU-04.01 to CU-04.08** and **CU-07.01 to CU-07.09**; Gherkin
`specs/008-cancellation-history/features/cu-04-order-cancellation.feature` and `cu-07-order-history.feature`
(17 scenarios).

## Context

Users cancel their own `NEW` orders (which releases what they reserved), list their orders and cash movements
newest first with cursor pagination and a status filter, and read a single order. The administrator lists the orders
of any user (internal, demo only). Domain extensions (level 2) except the administrator view (level 3).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cancel a pending order (Priority: P1)

**Acceptance Scenarios**: CU-04.01 to CU-04.08.

### User Story 2 - See my order history and a single order (Priority: P2)

**Acceptance Scenarios**: CU-07.01 to CU-07.05, CU-07.07 to CU-07.09.

### User Story 3 - The administrator sees any user's history (Priority: P3)

**Acceptance Scenarios**: CU-07.06.

### Edge Cases

- Cancelling a cash movement (`CASH_IN` / `CASH_OUT`, always `FILLED`) → 409 `ORDER_NOT_CANCELLABLE`.
- Cancelling for an unknown user (`X-User-Id: 999`) → 404 `User 999 was not found.`
- An invalid `status` filter → 400 `VALIDATION_ERROR`,
  `Request validation failed: 'status' must be one of NEW, FILLED, REJECTED, CANCELLED.`
- An invalid history cursor → 400 `VALIDATION_ERROR`, `Request validation failed: 'cursor' is invalid.`
- An `orderId` in the path that is not an integer (`GET /api/v1/orders/abc`, `POST /api/v1/orders/abc/cancel`) →
  400 `VALIDATION_ERROR`, `Request validation failed: 'orderId' has an invalid type or format.` (DEC-25).
- An `orderId` or `userId` in the path that is 0 or negative (`GET /api/v1/orders/0`,
  `POST /api/v1/orders/0/cancel`, `GET /api/internal/users/0/orders`) → 400 `VALIDATION_ERROR`,
  `Request validation failed: '<orderId|userId>' must be greater than 0.` (DEC-26).
- **CU-04.08** needs the forced-execution tool (I-02), which is delivered by spec 009. Here its step is implemented
  by a test helper that follows the same locking protocol the tool must follow (lock the user row, then update only
  if still `NEW`); spec 009 replaces the helper with a call to the real endpoint (plan.md § CU-04.08).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The endpoints MUST implement `contracts/openapi.yaml` exactly.
- **FR-002**: Cancellation MUST lock the user row first (Principle VII) and change the status only if it is still
  `NEW` (`UPDATE ... WHERE status = 'NEW'`), so that a concurrent execution and a cancellation never both apply.
- **FR-003**: Cancellation is idempotent by nature (DEC-15) and requires no `Idempotency-Key`.
- **FR-004**: History is ordered by `datetime` descending, then `id` descending; pages never repeat or skip rows.
- **FR-005**: An order of another user is answered exactly like a missing one (`Order <id> was not found.`).
- **FR-006**: Every cancellation is recorded with `BusinessMetrics.orderCancelled(alreadyCancelled)`.

### Key Entities

`OrderRecord` (ledger view of any `orders` row), `OrderHistoryCriteria`, `HistoryCursor`, `CancellationResult`.

## Success Criteria *(mandatory)*

- **SC-001**: The 17 scenarios pass; `CU-04` and `CU-07` are in the traceability scope.
- **SC-002**: PRD M-07 (cancellation part): 50 simultaneous cancellations of the same order release the reservation
  once (exactly one response with `alreadyCancelled: false`).
