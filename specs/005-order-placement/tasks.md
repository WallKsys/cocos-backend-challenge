# Tasks: Order placement (spec 005)

**Input**: `specs/005-order-placement/` (spec, plan, contract, feature, migration V6)

**Before starting**: specs 001–004 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/005-order-placement`.

**Slices**: six PRs, in order. Each: tests → skeletons → confirm failure → implement → full suite → PR size check.

---

## Slice 005-a — Request validation (branch `005-order-placement-a`)

**Estimated size**: ~250 lines.

- [ ] T001 Write `OrderRequestTest` (plan.md § Validation rules 1–8 in order, § Request fingerprint), tagged
  CU-03.22, CU-03.24, CU-03.25, CU-03.26, CU-03.27 as in plan.md § Tests.
- [ ] T002 Skeletons of `OrderRequest`, `Rejection`, `OrderEvaluation`. Run unit tests: **fail**. Record it.
- [ ] T003 Implement until green.

## Slice 005-b — Order evaluation (branch `005-order-placement-b`)

**Estimated size**: ~380 lines.

- [ ] T004 Write `OrderEvaluatorTest`: one test per CU of plan.md § Tests row 1, with the exact numbers and messages
  of the feature file, plus rounding cases (`50000 / 925.85` → 54; `20000 / 950` → 21; amount < price → 0).
- [ ] T005 Skeletons of `Account`, `Instrument`, `CashBalance`, `ShareBalance`, `OrderEvaluator`. **Fail**. Record.
- [ ] T006 Implement plan.md § Order evaluation until green.

## Slice 005-c — Persistence (branch `005-order-placement-c`)

**Estimated size**: ~390 lines. Split into c1 (lock + ledger + instrument + market data) and c2 (order repository +
V6) if needed.

- [ ] T007 Copy V6 (plan.md § Contract, features and HTTP examples). Extend `SeedDataIT` (spec 002) to expect
  migrations 1–6.
- [ ] T008 [P] Write `JdbcLedgerReaderIT`, `JdbcAccountLockIT`, `JdbcOrderRepositoryIT` (CU-03.33, 03.35, 03.37, 03.42).
- [ ] T009 Skeletons of the ports and `Jdbc*` adapters. **Fail**. Record.
- [ ] T010 Implement plan.md § Persistence until green.

## Slice 005-d — Use case (branch `005-order-placement-d`)

**Estimated size**: ~350 lines.

- [ ] T011 Write `PlaceOrderServiceTest` with in-memory fakes of every outbound port and a fixed `Clock`
  (CU-03.20, 03.21, 03.28, 03.31–03.35, 03.41, 03.42; metrics calls of spec FR-005).
- [ ] T012 Skeletons of `PlaceOrderUseCase`, `PlaceOrderCommand`, `PlaceOrderResult`, `NewOrder`,
  `PlaceOrderService`, `OrderConfiguration`. **Fail**. Record.
- [ ] T013 Implement plan.md § Application until green; ArchUnit green (`@Transactional` on `place`).

## Slice 005-e — Endpoint, BDD and contract (branch `005-order-placement-e`)

**Estimated size**: ~390 lines (feature and contract copies excluded).

- [ ] T014 Copy the feature file and the contract; append `CU-03` to `scope.txt`.
- [ ] T015 [P] Write `OrderSteps` and `TestClockConfiguration` (plan.md § BDD steps).
- [ ] T016 [P] Write `OrderControllerIT` (CU-03.23, 03.29, 03.30, replay 200 + header, response shape).
- [ ] T017 Skeleton of `OrderController`, `PlaceOrderRequestBody`, `OrderResponse`. Run `./mvnw verify`: **CU-03
  scenarios and controller IT fail**. Record.
- [ ] T018 Implement plan.md § REST until green (42 scenarios).
- [ ] T019 [P] `http/005-order-placement.http`.

## Slice 005-f — Concurrency and idempotency under load (branch `005-order-placement-f`)

**Estimated size**: ~250 lines.

- [ ] T020 Write `ConcurrentOrdersIT` (CU-03.36) and `IdempotencyLoadIT` (M-02, M-04, M-05, M-06). They should pass
  immediately if slices a–e are correct; **if any fails, that is a bug in the earlier slices: stop and report
  before changing production code**.
- [ ] T021 Run `./mvnw verify` three times in a row: the concurrency tests must be stable (no flakiness).

---

## Definition of done (spec 005)

- [ ] Slices merged; `./mvnw verify` green on `main`.
- [ ] 42 CU-03 scenarios passing; `CU-03` in scope.
- [ ] M-02, M-04, M-05, M-06 green and stable.
