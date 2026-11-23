# Feature Specification: Portfolio

**Feature Branch**: `006-portfolio-<slice>` (one branch per PR slice, see `tasks.md`)

**Created**: 2026-11-21

**Status**: Approved for implementation

**Input**: Challenge requirement "Portfolio"; R-13, R-15, R-16; DEC-01, DEC-01.1, DEC-02, DEC-02.1 (moving average,
revised 2026-11-21), DEC-10, DEC-11, DEC-17; RFC TEC-17, TEC-18; acceptance scenarios **CU-01.01 to CU-01.14**;
Gherkin `specs/006-portfolio/features/cu-01-portfolio.feature` (14 scenarios, already written).

## Context

A user sees the value of the account: total value, cash (total, reserved, available) and each position with its
market value, average cost, total return and daily change. Everything is derived from `orders` at request time
(TEC-17). An administrator can see any user's portfolio through an internal endpoint (demo only).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See my portfolio (Priority: P1)

**Acceptance Scenarios**: CU-01.01, CU-01.02, CU-01.04, CU-01.05, CU-01.07, CU-01.11.

### User Story 2 - Understand inconsistent or incomplete data (Priority: P1)

**Acceptance Scenarios**: CU-01.03 (negative position, DEC-11), CU-01.06 (no market data).

### User Story 3 - Average cost that reflects the shares I hold (Priority: P1)

**Acceptance Scenarios**: CU-01.12, CU-01.13, CU-01.14.

### User Story 4 - Only my portfolio; any portfolio for the administrator (Priority: P2)

**Acceptance Scenarios**: CU-01.08, CU-01.09, CU-01.10.

### Edge Cases

- A position with quantity 0 is not listed even if it has orders (CU-01.05).
- A position with only `NEW` orders and no `FILLED` ones is not listed (CU-01.04).
- A negative cash total (only reachable through internal tools) is shown and flagged with `NEGATIVE_CASH`.
- A position whose average cost cannot be determined (quantity ≠ 0 but no buy since the last reset — only with
  inconsistent data) has `averageCost` and `totalReturnPct` null.
- The administrator endpoint with `userId` 0 or negative → 400 `VALIDATION_ERROR`,
  `Request validation failed: 'userId' must be greater than 0.` (DEC-26).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `GET /api/v1/portfolio` and `GET /api/internal/users/{userId}/portfolio` MUST implement
  `contracts/openapi.yaml` exactly.
- **FR-002**: Values, rounding and warnings MUST follow plan.md § Portfolio rules.
- **FR-003**: The portfolio MUST be computed with a fixed number of queries per request, independent of the number
  of positions (no query per position).
- **FR-004**: The internal endpoint MUST follow spec 003 (`@ConditionalOnProperty`, admin filter).

### Key Entities

`Fill`, `Position`, `Portfolio`, `PortfolioWarning` (plan.md).

## Success Criteria *(mandatory)*

- **SC-001**: The 14 scenarios of the feature file pass.
- **SC-002**: `CU-01` is in the traceability scope; the published contract matches the routes.

## Assumptions

- Market data is static unless a scenario changes it with SQL (the price-change tool is spec 009).
