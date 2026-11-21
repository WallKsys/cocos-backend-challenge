# Tasks: Order cancellation and history (spec 008)

**Input**: `specs/008-cancellation-history/` (spec, plan, contract, features)

**Before starting**: specs 001–007 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/008-cancellation-history`.

**Slices**: three PRs. Each: tests → skeletons → confirm failure → implement → full suite → PR size check.

---

## Slice 008-a — Domain and repository (branch `008-cancellation-history-a`)

**Estimated size**: ~380 lines.

- [ ] T001 [P] Write `OrderRecordTest`, `HistoryCursorTest`, `OrderHistoryCriteriaTest`.
- [ ] T002 [P] Write `JdbcOrderLedgerRepositoryIT` (CU-04.01–04.03, 04.06–04.07, 07.01, 07.02, 07.07, 07.08).
- [ ] T003 Skeletons of the domain types, `OrderLedgerRepository`, `JdbcOrderLedgerRepository`. **Fail**. Record.
- [ ] T004 Implement plan.md § History and single order (persistence) and `cancelIfNew` until green.

## Slice 008-b — Use cases and concurrency (branch `008-cancellation-history-b`)

**Estimated size**: ~380 lines.

- [ ] T005 [P] Write `CancelOrderServiceTest`, `GetOrderServiceTest`, `GetOrderHistoryServiceTest`.
- [ ] T006 [P] Write `support/ForcedExecutionSimulator`, `CancelVersusExecutionIT` (CU-04.08) and
  `CancellationLoadIT` (M-07).
- [ ] T007 Skeletons of the ports, services and configuration. **Fail**. Record.
- [ ] T008 Implement plan.md § Cancellation and the query services until green; run `./mvnw verify` three times to
  confirm the concurrency tests are stable.

## Slice 008-c — Endpoints, BDD and contract (branch `008-cancellation-history-c`)

**Estimated size**: ~380 lines (feature and contract copies excluded).

- [ ] T009 Copy the two features and the contract; append `CU-04` and `CU-07` to `scope.txt`.
- [ ] T010 [P] Write `CancellationSteps` and `OrderHistorySteps` (plan.md § BDD steps).
- [ ] T011 [P] Write `OrderQueryControllerIT` (edge cases of spec.md) and `InternalOrderHistoryControllerIT` (CU-07.06).
- [ ] T012 Skeletons of the controllers and DTOs. Run `./mvnw verify`: **CU-04/CU-07 scenarios fail**. Record.
- [ ] T013 Implement plan.md § REST until green (17 scenarios; contract route test with the new routes).
- [ ] T014 [P] `http/008-cancellation-history.http`.

---

## Definition of done (spec 008)

- [ ] Slices merged; `./mvnw verify` green; 17 scenarios passing; `CU-04` and `CU-07` in scope; M-07 (cancel) green.
