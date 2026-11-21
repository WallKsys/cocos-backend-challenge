# Tasks: Deposits and withdrawals (spec 007)

**Input**: `specs/007-cash-movements/` (spec, plan, contract, features)

**Before starting**: specs 001–006 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/007-cash-movements`.

**Slices**: three PRs. Each: tests → skeletons → confirm failure → implement → full suite → PR size check.

---

## Slice 007-a — Domain and shared idempotency lookup (branch `007-cash-movements-a`)

**Estimated size**: ~300 lines.

- [ ] T001 [P] Write `CashMovementRequestTest` and `CashMovementEvaluatorTest` (CU tags of plan.md § Tests).
- [ ] T002 [P] Write `IdempotencyLookupTest` (never used → empty; same fingerprint → id + replay metric; different
  fingerprint → conflict metric + exception).
- [ ] T003 Skeletons of `CashMovementRequest`, `CashMovement`, `CashMovementEvaluator`, `IdempotencyLookup`. **Fail**.
  Record.
- [ ] T004 Implement until green.
- [ ] T005 Refactor `PlaceOrderService` to use `IdempotencyLookup`. **All spec 005 tests must pass unmodified.**

## Slice 007-b — Persistence and use cases (branch `007-cash-movements-b`)

**Estimated size**: ~350 lines.

- [ ] T006 [P] Write `JdbcCashMovementRepositoryIT` (CU-05.01, 05.02).
- [ ] T007 [P] Write `CashMovementServiceTest` with fakes (CU-05.05, 05.07, 06.04, 06.06, metrics).
- [ ] T008 Skeletons of the ports, `JdbcCashMovementRepository`, `CashMovementService`, configuration. **Fail**. Record.
- [ ] T009 Implement plan.md § Application and § Persistence until green; ArchUnit green.

## Slice 007-c — Endpoints, BDD and contract (branch `007-cash-movements-c`)

**Estimated size**: ~350 lines (feature and contract copies excluded).

- [ ] T010 Copy the two features and the contract; append `CU-05` and `CU-06` to `scope.txt`.
- [ ] T011 [P] Write `CashMovementSteps` (plan.md § BDD steps).
- [ ] T012 [P] Write `CashMovementControllerIT` and `CashIdempotencyIT`.
- [ ] T013 Skeletons of the controller and DTOs. Run `./mvnw verify`: **CU-05/CU-06 scenarios fail**. Record.
- [ ] T014 Implement plan.md § REST until green (17 scenarios).
- [ ] T015 [P] `http/007-cash-movements.http`.

---

## Definition of done (spec 007)

- [ ] Slices merged; `./mvnw verify` green; 17 scenarios passing; `CU-05` and `CU-06` in scope; M-07 (cash) green.
