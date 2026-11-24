# Tasks: Deposits and withdrawals (spec 007)

**Input**: `specs/007-cash-movements/` (spec, plan, contract, features)

**Before starting**: specs 001–006 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/007-cash-movements`.

**Slices**: three planned slices; 007-b and 007-c have predefined split points if they exceed 400 counted lines. Each: tests → skeletons → confirm failure → implement → full suite → PR size check.

---

## Slice 007-a — Domain and shared idempotency lookup (branch `007-cash-movements-a`)

**Manual review**: required. If the slice is split, both halves get it. Focus: the `IdempotencyLookup` refactor changes no behavior and modifies no spec 005 test.

**Estimated size**: ~300 lines.

- [x] T001 [P] Write `CashMovementRequestTest` and `CashMovementEvaluatorTest` (CU tags of plan.md § Tests).
- [x] T002 [P] Write `IdempotencyLookupTest` (never used → empty; same fingerprint → id + replay metric; different
  fingerprint → conflict metric + exception).
- [x] T003 Skeletons of `CashMovementRequest`, `CashMovement`, `CashMovementEvaluator`, `IdempotencyLookup`. **Fail**.
  Record.
- [x] T004 Implement until green.
- [x] T005 Refactor `PlaceOrderService` to use `IdempotencyLookup`. **All spec 005 tests must pass unmodified.**

## Slice 007-b — Persistence and use cases (branch `007-cash-movements-b`)

**Estimated size**: ~350 lines. If it exceeds 400 counted lines (measure after the tests and skeletons), split it as follows, in this order; each
half follows the test-first cycle on its own and leaves the suite green:
- **007-b1:** `CashMovementRepository`, `JdbcCashMovementRepository` and `JdbcCashMovementRepositoryIT`.
- **007-b2:** `CashMovementService`, its configuration and `CashMovementServiceTest`.

- [x] T006 [P] Write `JdbcCashMovementRepositoryIT` (CU-05.01, 05.02).
- [x] T007 [P] Write `CashMovementServiceTest` with fakes (CU-05.05, 05.07, 06.04, 06.06, metrics).
- [x] T008 Skeletons of the ports, `JdbcCashMovementRepository`, `CashMovementService`, configuration. **Fail**. Record.
- [x] T009 Implement plan.md § Application and § Persistence until green; ArchUnit green.

## Slice 007-c — Endpoints, BDD and contract (branch `007-cash-movements-c`)

**Estimated size**: ~350 lines (feature and contract copies excluded). If it exceeds 400 counted lines (measure after the tests and skeletons), split it as follows, in this order; each
half follows the test-first cycle on its own and leaves the suite green:
- **007-c1:** the controller, the DTOs, the contract copy, `CashMovementControllerIT`, `CashIdempotencyIT` and
  `http/007-cash-movements.http`.
- **007-c2:** the two feature copies, `CU-05` and `CU-06` in `scope.txt`, `CashMovementSteps` and T016.

- [ ] T010 Copy the two features and the contract; append `CU-05` and `CU-06` to `scope.txt`.
- [ ] T011 [P] Write `CashMovementSteps` (plan.md § BDD steps). Every request it sends first stores the current
  row count of `orders` as the shared baseline (see T016), so the reused `no order is stored` step checks the cash
  request.
- [ ] T016 Move the baseline of `no order is stored` to `ScenarioContext`, shared by both request helpers. In the
  existing `ScenarioContext`, add a `long` with a setter and a getter for the row count of `orders` before the last
  request. In the existing `OrderSteps`, `send` stores `orderCount()` there instead of in the private
  `ordersBeforeRequest` field (remove that field), and `noOrderStored` compares with the value in `ScenarioContext`.
  Reason: the baseline is captured only when `OrderSteps` sends a request, so after a cash request the step would
  compare with zero (seeded database) or with the count before an earlier `user N placed an order:`.
  These are the only changes allowed to those two existing files; no step text and no other method changes. The
  CU-03 scenarios pass at once with it; no red run is needed for this task. Say so in the PR.
- [ ] T012 [P] Write `CashMovementControllerIT` and `CashIdempotencyIT`.
- [ ] T013 Skeletons of the controller and DTOs. Run `./mvnw verify`: **CU-05/CU-06 scenarios fail**. Record.
- [ ] T014 Implement plan.md § REST until green (17 scenarios).
- [ ] T015 [P] `http/007-cash-movements.http`.

---

## Definition of done (spec 007)

- [ ] Slices merged; `./mvnw verify` green; 17 scenarios passing; `CU-05` and `CU-06` in scope; M-07 (cash) green.
