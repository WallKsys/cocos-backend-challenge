# Feature Specification: Consolidation (refactor without behavior change)

**Feature Branch**: `011-consolidation-<slice>` (one branch per PR slice, see `tasks.md`)

**Created**: 2026-11-26

**Status**: Implemented (2026-11-27)

**Input**: RFC TEC-38 (consolidation by table and by resource). An external audit of v1.0.0 counted 165 production
files for 4 tables and 15 endpoints, with a median of 13 lines per file. The owner accepted its points P1 to P7 with
the adjustments recorded in TEC-38, and rejected P8 (removing the inbound port interfaces).

## Context

Specs 001 to 010 were delivered one after another, in PRs of at most 400 lines. Each spec added its own ports, adapters,
use cases, controllers and configuration classes, and nothing merged the overlapping ones afterwards. As a result:

- the `users` table has three ports and three adapters (`AccountLock`, `AccountReader`, `UserWriter`), two of them
  identical except for `FOR UPDATE`;
- the `orders` table has six ports and six adapters, three of which repeat the same row mapping;
- `POST /api/v1/orders` and the other order endpoints live in two controllers;
- deposits and withdrawals have two inbound ports implemented by one service;
- five demo-only endpoints have five ports, five services and five controllers;
- bean wiring is spread over eleven configuration classes.

This spec merges those pieces by **table** (persistence), by **resource** (REST and inbound ports) and by **purpose**
(configuration), so that the codebase has fewer and more cohesive classes. Hexagonal architecture, the ArchUnit rules,
the explicit SQL and every guarantee of specs 001 to 010 stay the same.

This spec adds **no endpoint, no business rule, no SQL and no test scenario**. It has no CU scenarios: the existing
suite is its specification, and every slice must leave it green.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find the code by table and by resource (Priority: P1)

A developer who opens the project finds one persistence adapter per table, one inbound port per resource and one
controller per resource, instead of one class per spec that touched them.

**Independent Test**: after slice 011-k, `src/main/java` contains exactly the 103 files of plan.md § Target
structure (165 before).

### User Story 2 - Behavior does not change (Priority: P1)

A client of the API, an operator reading metrics and logs, and the database see exactly the same behavior before and
after every slice.

**Independent Test**: in every slice, `./mvnw verify` is green, the SQL texts of the persistence adapters are the same
as in the baseline, and the suite runs exactly the same test methods as the baseline (plan.md § Preservation
evidence).

### User Story 3 - The compiler checks the error mapping (Priority: P2)

A developer who adds a domain exception and forgets to map it to an API error code gets a compilation error, not a
plain 500 at runtime.

**Independent Test**: in slice 011-a, adding a seventh permitted subclass of `DomainException` without a `case` in
`ApiExceptionHandler` does not compile (evidence pasted in the PR, the class is not committed).

### Edge Cases

- A merge would make two SQL constants of the same adapter share a name (for example, two `FIND_BY_ID_SQL`): the
  constant is renamed, its **text** stays byte-identical.
- A merge would make two methods of the same port share a signature with different return types (for example,
  `findById(long)` of orders and of cash movements): plan.md fixes the new name; nothing else is renamed.
- A subclass of a mapped domain exception (the `not-found-subclass` probe of `ErrorHandlingIT`, spec 003) keeps the
  error code of its parent: the six permitted subclasses are `non-sealed`, not `final`.
- A hand-written test fake implements a port that grows: it gets the new methods as stubs that throw
  `UnsupportedOperationException`; its existing methods do not change.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Behavior MUST NOT change: routes, status codes, headers, JSON bodies, error codes and messages,
  validation order, SQL texts, transaction boundaries and isolation levels, the locking protocol (TEC-16), metrics
  (names, tags and which operations record them) and operation logs stay identical.
- **FR-002**: Persistence MUST have one adapter per table: `JdbcAccountRepository` (`users`),
  `JdbcInstrumentRepository` (`instruments`), `JdbcMarketDataRepository` (`marketdata`), `JdbcOrderLedgerRepository`
  (rows of `orders`) and `JdbcLedgerReader` (aggregations over `orders`, TEC-17), plus `JdbcSeedDataRestorer`
  (demo reset across tables).
- **FR-003**: Outbound ports MUST follow the adapters, except `AdminOrderRepository`, which stays a separate port
  (demo-only destructive operations, TEC-18) implemented by `JdbcOrderLedgerRepository`.
- **FR-004**: Inbound ports MUST be: `PlaceOrderUseCase`, `CancelOrderUseCase`, `OrderQuery`, `CashMovementUseCase`,
  `GetPortfolioQuery`, `SearchInstrumentsQuery` and `InternalToolsUseCase`. Their commands and results are records
  nested in their port.
- **FR-005**: REST MUST have one controller per resource (`OrderController`, `CashMovementController`,
  `PortfolioController`, `InstrumentSearchController`, `InternalToolsController`); a DTO used by one owner only is
  nested in it.
- **FR-006**: `DomainException` MUST be `sealed`; `ApiExceptionHandler` MUST map it with an exhaustive `switch`.
- **FR-007**: Placement, deposits and withdrawals MUST share `IdempotentMutationGuard` (lock with its wait metric,
  idempotency lookup, current time).
- **FR-008**: Bean wiring MUST live in four configuration classes: `ApplicationConfiguration`,
  `InternalToolsConfiguration`, `WebConfiguration` and `ObservabilityConfiguration`.
- **FR-009**: The ArchUnit rules, the traceability tests, the `.feature` files, the published contract and the Flyway
  migrations MUST NOT change.
- **FR-010**: Existing tests change only by the mechanical adjustments of plan.md § Test adjustments, and only in the
  files that the slice's tasks name. No test method is removed, and no expected value or assertion changes.

### Out of scope

- Removing the inbound port interfaces (audit P8): controllers keep depending on `application.port.in` only.
- Merging `Order` with `OrderRecord`: they have different invariants and JSON shapes.
- Merging `PlaceOrderService` with `CancelOrderService`.
- Merging the six exception classes into one: they stay, sealed.
- Any change to SQL, migrations, endpoints, messages, metrics or logs.

## Success Criteria *(mandatory)*

- **SC-001**: At the end of the spec, `src/main/java` contains exactly the 103 files of plan.md § Target structure (165
  at v1.0.0).
- **SC-002**: In every slice, `specs/011-consolidation/preservation.sh` reports no difference in SQL texts or executed
  test names against the baseline (plan.md § Preservation evidence).
- **SC-003**: In every slice, `./mvnw verify` is green locally and in CI. In slice 011-k, `./mvnw verify -Pvolume`
  passes as well.
- **SC-004**: In slice 011-a, a `DomainException` subclass without a mapping fails to compile.
