# Tasks: Order cancellation and history (spec 008)

**Input**: `specs/008-cancellation-history/` (spec, plan, contract, features)

**Before starting**: specs 001–007 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/008-cancellation-history`.

**Slices**: three planned slices, each with a predefined split point if it exceeds 400 counted lines. Each: tests → skeletons → confirm failure → implement → full suite → PR size check.

---

## Slice 008-a — Domain and repository (branch `008-cancellation-history-a`)

**Estimated size**: ~380 lines. If it exceeds 400 counted lines (measure after the tests and skeletons), split it as follows, in this order; each
half follows the test-first cycle on its own and leaves the suite green:
- **008-a1:** `OrderRecord`, `HistoryCursor`, `OrderHistoryCriteria`, `OrderHistoryPage` and their unit tests.
- **008-a2:** `OrderLedgerRepository`, `JdbcOrderLedgerRepository` and `JdbcOrderLedgerRepositoryIT`.

- [x] T001 [P] Write `OrderRecordTest`, `HistoryCursorTest`, `OrderHistoryCriteriaTest`.
- [x] T002 [P] Write `JdbcOrderLedgerRepositoryIT` (CU-04.01–04.03, 04.06–04.07, 07.01, 07.02, 07.07, 07.08).
- [x] T003 Skeletons of the domain types, `OrderLedgerRepository`, `JdbcOrderLedgerRepository`. **Fail**. Record.
- [x] T004 Implement plan.md § History and single order (persistence) and `cancelIfNew` until green.

## Slice 008-b — Use cases and concurrency (branch `008-cancellation-history-b`)

**Manual review**: required. If the slice is split, both halves get it. Focus: `UPDATE ... WHERE status = 'NEW'` under the user lock, CU-04.08 and 50 simultaneous cancellations.

**Estimated size**: ~380 lines. If it exceeds 400 counted lines (measure after the tests and skeletons), split it as follows, in this order; each
half follows the test-first cycle on its own and leaves the suite green:
- **008-b1:** `GetOrderService`, `GetOrderHistoryService` and their tests.
- **008-b2:** `CancelOrderService`, `CancelOrderServiceTest`, `ForcedExecutionSimulator`, `CancelVersusExecutionIT`
  and `CancellationLoadIT` (run `./mvnw verify` three times).

- [x] T005 [P] Write `CancelOrderServiceTest`, `GetOrderServiceTest`, `GetOrderHistoryServiceTest`.
- [x] T006 [P] Write `support/ForcedExecutionSimulator`, `CancelVersusExecutionIT` (CU-04.08) and
  `CancellationLoadIT` (M-07).
- [x] T007 Skeletons of the ports, services and configuration. **Fail**. Record.
- [x] T008 Implement plan.md § Cancellation and the query services until green; run `./mvnw verify` three times to
  confirm the concurrency tests are stable.

## Slice 008-c — Endpoints, BDD and contract (branch `008-cancellation-history-c`)

**Estimated size**: ~380 lines (feature and contract copies excluded). If it exceeds 400 counted lines (measure after the tests and skeletons), split it as follows, in this order; each
half follows the test-first cycle on its own and leaves the suite green:
- **008-c1:** both controllers, the DTOs, the contract copy, `OrderQueryControllerIT`,
  `InternalOrderHistoryControllerIT` and `http/008-cancellation-history.http`.
- **008-c2:** the two feature copies, `CU-04` and `CU-07` in `scope.txt`, `CancellationSteps` and `OrderHistorySteps`.

- [x] T009 Copy the two features and the contract; append `CU-04` and `CU-07` to `scope.txt`.
- [x] T010 [P] Write `CancellationSteps` and `OrderHistorySteps` (plan.md § BDD steps).
- [x] T011 [P] Write `OrderQueryControllerIT` (edge cases of spec.md) and `InternalOrderHistoryControllerIT` (CU-07.06).
- [x] T012 Skeletons of the controllers and DTOs. Run `./mvnw verify`: **CU-04/CU-07 scenarios fail**. Record.
- [x] T013 Implement plan.md § REST until green (17 scenarios; contract route test with the new routes).
- [x] T014 [P] `http/008-cancellation-history.http`.

---

## Definition of done (spec 008)

- [x] Slices merged; `./mvnw verify` green; 17 scenarios passing; `CU-04` and `CU-07` in scope; M-07 (cancel) green.
