# Tasks: Order placement (spec 005)

**Input**: `specs/005-order-placement/` (spec, plan, contract, feature, migration V6)

**Before starting**: specs 001–004 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/005-order-placement`.

**Slices**: six planned slices, in order; 005-b, 005-c, 005-d and 005-e have predefined split points if they
exceed 400 counted lines. Each: tests → skeletons → confirm failure → implement → full suite → PR size check.

---

## Slice 005-a — Request validation (branch `005-order-placement-a`)

**Estimated size**: ~250 lines.

- [x] T001 Write `OrderRequestTest` (plan.md § Validation rules 1–8 in order, § Request fingerprint), tagged
  CU-03.22, CU-03.24, CU-03.25, CU-03.26, CU-03.27 as in plan.md § Tests.
- [x] T002 Skeletons of `OrderRequest`, `Rejection`, `OrderEvaluation`. Run unit tests: **fail**. Record it.
- [x] T003 Implement until green.

## Slice 005-b — Order evaluation (branch `005-order-placement-b`)

**Manual review**: required. If the slice is split, both halves get it. Focus: quantity rounded DOWN, real amount versus requested amount, the order of the rules, the exact rejection messages and the BMA −10 case.

**Estimated size**: ~380 lines. If it exceeds 400 counted lines (measure after the tests and skeletons), split it as follows, in this order; each
half follows the test-first cycle on its own and leaves the suite green:
- **005-b1:** `Account`, `Instrument`, `CashBalance`, `ShareBalance` and `OrderEvaluator` steps 1, 2, 3 and 5
  (reference price, quantity, total, final status), with the tests whose outcome they decide (`FILLED` / `NEW`
  by quantity and by amount, `NO_MARKET_PRICE`, `AMOUNT_BELOW_PRICE`).
- **005-b2:** step 4 (availability: `INSUFFICIENT_FUNDS`, `INSUFFICIENT_SHARES` and their messages, BMA −10) with
  its tests.

- [x] T004 Write `OrderEvaluatorTest`: one test per CU of plan.md § Tests row 1, with the exact numbers and messages
  of the feature file, plus rounding cases (`50000 / 925.85` → 54; `20000 / 950` → 21; amount < price → 0).
- [x] T005 Skeletons of `Account`, `Instrument`, `CashBalance`, `ShareBalance`, `OrderEvaluator`. **Fail**. Record.
- [x] T006 Implement plan.md § Order evaluation until green.

## Slice 005-b3 — Price band and maximum quantity (branch `005-order-placement-b3`)

**Manual review**: required. Focus: the band bounds (rounded inwards, both allowed), the rule order (band before quantity, maximum before `AMOUNT_BELOW_PRICE`), the exact messages and the boundary tests.

**Estimated size**: ~150 lines. Added on 2026-11-23 (DEC-29, DEC-30), after 005-b was merged. Domain only: the migration for the new codes is slice 005-c3.

- [x] T022 Write NEW methods in `OrderEvaluatorTest` (never change existing ones): CU-03.43, CU-03.44, CU-03.45 and
  CU-03.46, plus, without a CU tag, one cent inside and one cent outside each band bound and a SELL by amount with
  `QUANTITY_TOO_LARGE` (plan.md § Order evaluation and § Tests).
- [x] T023 Add `PRICE_OUT_OF_RANGE` and `QUANTITY_TOO_LARGE` to `RejectionReason`. Run the unit tests: **the new tests
  fail**. Record it.
- [x] T024 Implement the price band and the maximum quantity in `OrderEvaluator` until green.

## Slice 005-c — Persistence (branch `005-order-placement-c`)

**Manual review**: required. If the slice is split, both halves get it. Focus: the cash and share ledger SQL, the `FOR UPDATE` lock, the lightweight idempotency record lookup (a deposit with cents) and UTC timestamps.

**Estimated size**: ~390 lines. Split into c1 (lock + ledger + instrument + market data) and c2 (order repository +
V6) if needed.

**005-c1 completed**: lock, ledger, instrument and market-data adapters and their integration tests (T008–T010).
Finish the shared tasks in 005-c2 alongside T007.

- [ ] T007 Copy V6 (plan.md § Contract, features and HTTP examples). Extend `SeedDataIT` (spec 002) to expect
  migrations 1–6.
- [ ] T008 [P] Write `JdbcLedgerReaderIT`, `JdbcAccountLockIT`, `JdbcOrderRepositoryIT` (CU-03.33, 03.35, 03.37, 03.42).
- [ ] T009 Skeletons of the ports and `Jdbc*` adapters. **Fail**. Record.
- [ ] T010 Implement plan.md § Persistence until green.

## Slice 005-c3 — Migration V7 for the new rejection codes (branch `005-order-placement-c3`)

**Manual review**: required. Focus: V7 copied byte-identical, and the database accepts both new codes and still rejects an unknown one.

**Estimated size**: ~60 lines. Added on 2026-11-23 (DEC-29, DEC-30). It comes after 005-c, so migrations stay in version order.

**Not a half of 005-c.** 005-c is one slice, or the halves 005-c1 and 005-c2 when it is split (V6 is in 005-c, or in
005-c2). 005-c3 is an independent slice: start it only when every task of 005-c is checked on `main`.

- [ ] T025 Add NEW cases to `SchemaConstraintsIT` (spec 002): a `REJECTED` order with `PRICE_OUT_OF_RANGE` and one with
  `QUANTITY_TOO_LARGE` (quantity 0) succeed. Extend `SeedDataIT` to expect migration versions 1 to 7. Run
  `./mvnw verify`: **the new cases fail**. Record it.
- [ ] T026 Copy V7 byte-identical (plan.md § Contract, features and HTTP examples). Run `./mvnw verify` until green.

## Slice 005-d — Use case (branch `005-order-placement-d`)

**Manual review**: required. If the slice is split, both halves get it. Focus: the flow lock → idempotency → validation → evaluation → save; an invalid request does not consume the key; metrics.

**Estimated size**: ~350 lines. If it exceeds 400 counted lines (measure after the tests and skeletons), split it as follows, in this order; each
half follows the test-first cycle on its own and leaves the suite green:
- **005-d1:** the flow without the idempotency branch (lock, instrument checks, quote and balances, evaluation,
  save, metrics) and its tests.
- **005-d2:** step 2 of plan.md § Application (lightweight idempotency record, replay, conflict) and its tests.

- [ ] T011 Write `PlaceOrderServiceTest` with in-memory fakes of every outbound port and a fixed `Clock`
  (CU-03.20, 03.21, 03.28, 03.31–03.35, 03.41, 03.42; metrics calls of spec FR-005).
- [ ] T012 Skeletons of `PlaceOrderUseCase`, `PlaceOrderCommand`, `PlaceOrderResult`, `NewOrder`,
  `PlaceOrderService`, `OrderConfiguration`. **Fail**. Record.
- [ ] T013 Implement plan.md § Application until green; ArchUnit green (`@Transactional` on `place`).

## Slice 005-e — Endpoint, BDD and contract (branch `005-order-placement-e`)

**Estimated size**: ~390 lines (feature and contract copies excluded). If it exceeds 400 counted lines (measure after the tests and skeletons), split it as follows, in this order; each
half follows the test-first cycle on its own and leaves the suite green:
- **005-e1:** `OrderController`, `PlaceOrderRequestBody`, `OrderResponse`, the contract copy, `OrderControllerIT` and
  `http/005-order-placement.http`. The contract travels with the controller: `PublishedContractMatchesRoutesIT`
  needs the implemented routes and the published contract to be identical.
- **005-e2:** the feature copy, `CU-03` in `scope.txt`, `OrderSteps` and `TestClockConfiguration` (42 scenarios).

- [ ] T014 Copy the feature file and the contract; append `CU-03` to `scope.txt`.
- [ ] T015 [P] Write `OrderSteps` and `TestClockConfiguration` (plan.md § BDD steps).
- [ ] T016 [P] Write `OrderControllerIT` (CU-03.23, 03.29, 03.30, replay 200 + header, response shape).
- [ ] T017 Skeleton of `OrderController`, `PlaceOrderRequestBody`, `OrderResponse`. Run `./mvnw verify`: **CU-03
  scenarios and controller IT fail**. Record.
- [ ] T018 Implement plan.md § REST until green (42 scenarios).
- [ ] T019 [P] `http/005-order-placement.http`.

## Slice 005-f — Concurrency and idempotency under load (branch `005-order-placement-f`)

**Manual review**: required. If the slice is split, both halves get it. Focus: real simultaneity, tests that do not pass by chance, stable across three runs.

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
