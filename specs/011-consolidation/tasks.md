# Tasks: Consolidation (spec 011)

**Input**: `specs/011-consolidation/` (spec, plan, `preservation.sh`, `baseline/`)

**Before starting**: specs 001–010 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/011-consolidation`.

**Slices**: eleven planned slices (011-a to 011-k), in order; 011-b, 011-d, 011-g, 011-h and 011-k have a predefined
split if they exceed 400 counted lines. This spec is a **refactor without behavior change** (Constitution III,
v1.5.0): every task **passes at once**, so there is no red run. Each slice:

1. Commit the test adjustments that its tasks prescribe (plan.md § Test adjustments; only the files named here, only
   the letters named here).
2. Commit the production change (plan.md, the sections the task cites).
3. Run `./mvnw clean verify` until green, then `specs/011-consolidation/preservation.sh`: it must print `OK`. If it
   does not, or if a step seems to need a change of behavior, SQL or a test outside § Test adjustments, **stop and
   report**.
4. Run `scripts/check-pr-size.sh`. In the PR, "Test-first evidence" pastes the output of `preservation.sh` and the
   `Tests run` lines (plan.md § Preservation evidence), and "Test adjustments" lists each test file with its letters.

Commit messages: `[011][T0xx] short description (TEC-38)`.

---

## Slice 011-a — Sealed domain exceptions and domain records (branch `011-consolidation-a`)

**Estimated size**: ~200 lines.

- [x] T001 Adjust tests (passes at once): `OrderEvaluatorTest` (a), `PortfolioTest` (a), `PositionTest` (a),
  `GetPortfolioServiceIT` (a), `SearchInstrumentsServiceTest` (a, b: `InstrumentPage` → `CursorPage<...>`),
  `GetOrderHistoryServiceTest` (a, b: `OrderHistoryPage` → `CursorPage<...>`).
- [x] T002 Implement plan.md § Sealed domain exceptions and § Domain (`CursorPage`, nested `OrderEvaluation`,
  nested `PortfolioWarning` and `PortfolioWarningCode`; `RankedInstrument` waits for 011-c).
- [x] T003 SC-004 evidence: add a temporary `public final class ProbeException extends DomainException` to the
  `permits` list, run `./mvnw -q compile`, paste the compiler error (the `switch` of `ApiExceptionHandler` does not
  cover all values) in the PR, and remove it. Nothing of it is committed. Then step 3 of the slice protocol above.

## Slice 011-b — `users` (branch `011-consolidation-b`)

**Manual review**: required. If the slice is split, both halves get it. Focus: `lock` keeps `FOR UPDATE` and every
mutating flow still locks the same row first.

**Estimated size**: ~300 lines. Predefined split if it exceeds 400: **011-b1** (branch `011-consolidation-b1`)
replaces `AccountLock` and `AccountReader` (T004 and T005 for those files); **011-b2** (branch
`011-consolidation-b2`, after 011-b1 is merged) adds `create` and replaces `UserWriter`. T004 and T005 are checked
off in 011-b2.

- [x] T004 Adjust tests (passes at once): rename `JdbcAccountLockIT` → `JdbcAccountRepositoryLockIT` (g, b) and
  `JdbcUserWriterIT` → `JdbcAccountRepositoryCreateUserIT` (g); `PlanGuardIT` (b); `PlaceOrderServiceTest` (a, e);
  `CancelOrderServiceTest`, `CashMovementServiceTest`, `DeleteOrderServiceTest`, `ExecuteOrderServiceTest`,
  `GetOrderServiceTest`, `GetOrderHistoryServiceTest`, `GetPortfolioServiceTest`, `GetPortfolioServiceIT`,
  `CancelVersusExecutionIT` and `support/ForcedExecutionSimulator` (a, b).
- [x] T005 Implement plan.md § Persistence, `users`: `AccountRepository`, `JdbcAccountRepository`, the services that
  used the three old ports, and their beans (the `accountLock` bean becomes `accountRepository`; § Configuration).

## Slice 011-c — `instruments` (branch `011-consolidation-c`)

**Estimated size**: ~320 lines.

- [x] T006 Adjust tests (passes at once): rename `JdbcInstrumentSearchRepositoryIT` →
  `JdbcInstrumentRepositorySearchIT` (g, a, b); `PlanGuardIT` (b); `SearchInstrumentsServiceTest` (a, e);
  `PlaceOrderServiceTest` (e); `CashMovementServiceTest` (c, d, f: `findCashInstrument` is stubbed and verified on a
  new `InstrumentRepository` mock, added to the `inOrder(...)` that verifies it); `JdbcCashMovementRepositoryIT` (d).
- [x] T007 Implement plan.md § Persistence, `instruments` (with `RankedInstrument`, § Domain), the new
  `CashMovementService` parameter, and the beans.

## Slice 011-d — `marketdata` and ledger aggregations (branch `011-consolidation-d`)

**Estimated size**: ~340 lines. Predefined split if it exceeds 400: **011-d1** (branch `011-consolidation-d1`) is
T008 and T009 (market data); **011-d2** (branch `011-consolidation-d2`, after 011-d1 is merged) is T010 and T011
(ledger aggregations).

- [x] T008 Adjust tests (passes at once): rename `JdbcMarketDataWriterIT` → `JdbcMarketDataRepositoryUpsertIT`
  (g, a, b); `UpdateMarketDataServiceTest` (a, b); `PlaceOrderServiceTest` (e).
- [x] T009 Implement plan.md § Persistence, `marketdata`, `UpdateMarketDataService`, and the beans.
- [x] T010 Adjust tests (passes at once): rename `JdbcPortfolioReaderIT` → `JdbcLedgerReaderPortfolioIT` (g, a, b);
  `PlanGuardIT` (b); `PlaceOrderServiceTest` (e); `GetPortfolioServiceTest` (a, b, c, f: the `PortfolioReader` mock
  is replaced by the `LedgerReader` mock); `GetPortfolioServiceIT` (a, f: the `portfolios` spy is removed and its two
  verifications use `ledger`).
- [x] T011 Implement plan.md § Persistence, aggregations over `orders`, `GetPortfolioService`, and the beans.

## Slice 011-e — `orders` rows (1/2): placement (branch `011-consolidation-e`)

**Manual review**: required. Focus: idempotency record lookup and order insert keep their SQL and their mapping;
`PlaceOrderService` keeps its call order.

**Estimated size**: ~330 lines.

- [x] T012 Adjust tests (passes at once): rename `JdbcOrderRepositoryIT` → `JdbcOrderLedgerRepositoryPlacementIT`
  (g, a, b); `PlanGuardIT` (b); `PlaceOrderServiceTest` (a, e); `IdempotencyLookupTest` (a, b);
  `CashMovementServiceTest` (a, b); `JdbcSeedDataRestorerIT` (b); `JdbcAdminOrderRepositoryIT` (b).
- [x] T013 Implement plan.md § Persistence, rows of `orders`, the part of slice 011-e (constant renames of § Method
  and constant renames), `PlaceOrderService`, `IdempotencyLookup`, and the beans.

## Slice 011-f — `orders` rows (2/2): cash movements and admin operations (branch `011-consolidation-f`)

**Manual review**: required. Focus: `executeIfNew` and `delete` keep their guarded SQL; the shared row helpers do
not change any mapped value; one bean serves both ports.

**Estimated size**: ~320 lines.

- [x] T014 Adjust tests (passes at once): rename `JdbcCashMovementRepositoryIT` →
  `JdbcOrderLedgerRepositoryCashMovementIT` (g, a, b, d) and `JdbcAdminOrderRepositoryIT` →
  `JdbcOrderLedgerRepositoryAdminIT` (g, b); `CashMovementServiceTest` (a, b, d); `PlaceOrderServiceTest` (e).
- [x] T015 Implement plan.md § Persistence, rows of `orders`, the part of slice 011-f, including § Shared row helpers
  and the bean with the concrete return type.

## Slice 011-g — Public inbound ports and the idempotent mutation guard (branch `011-consolidation-g`)

**Manual review**: required. If the slice is split, both halves get it. Focus: placement and cash movements keep the
order lock → lock-wait metric → idempotency lookup → validation → evaluation → save, and no other operation starts
recording the lock wait.

**Estimated size**: ~400 lines. Predefined split if it exceeds 400: **011-g1** (branch `011-consolidation-g1`) is
T016 and T017; **011-g2** (branch `011-consolidation-g2`, after 011-g1 is merged) is T018 and T019.

- [x] T016 Adjust tests (passes at once): `PlaceOrderServiceTest`, `GetPortfolioServiceIT` and
  `CashMovementServiceTest` (a); rename `GetOrderServiceTest` → `OrderQueryServiceGetTest` (g, b) and
  `GetOrderHistoryServiceTest` → `OrderQueryServiceListTest` (g, b).
- [x] T017 Implement plan.md § Inbound ports and services for `PlaceOrderUseCase`, `CancelOrderUseCase`,
  `OrderQuery`/`OrderQueryService` and `CashMovementUseCase`; the controllers and `OperationLog` that use them; the
  beans (`orderQuery` replaces `getOrderQuery` and `getOrderHistoryQuery`; `cashMovementUseCase`, of type
  `CashMovementUseCase`, replaces `cashMovementService`).
- [ ] T018 Adjust tests (passes at once): rename `IdempotencyLookupTest` → `IdempotentMutationGuardTest` (g, b, c);
  `PlaceOrderServiceTest` (c); `CashMovementServiceTest` (c).
- [ ] T019 Implement plan.md § Idempotent mutation guard and the beans (`idempotentMutationGuard` replaces
  `idempotencyLookup`).

## Slice 011-h — Internal tools use case (branch `011-consolidation-h`)

**Manual review**: required. If the slice is split, both halves get it. Focus: `deleteOrder` and `executeOrder` keep
find owner → lock → guarded update, verbatim.

**Estimated size**: ~410 lines. Predefined split if it exceeds 400: **011-h1** (branch `011-consolidation-h1`) is
T020 and T021 (`createUser`, `restoreSeedData`, `updateMarketData`); **011-h2** (branch `011-consolidation-h2`, after
011-h1 is merged) is T022 and T023 (`deleteOrder`, `executeOrder`).

- [ ] T020 Adjust tests (passes at once): rename `UpdateMarketDataServiceTest` →
  `InternalToolsServiceUpdateMarketDataTest` (g, a, b, c, d); `JdbcAccountRepositoryCreateUserIT` (a, b, d);
  `JdbcSeedDataRestorerIT` (a, b, d).
- [ ] T021 Implement plan.md § Inbound ports and services, `InternalToolsUseCase` and `InternalToolsService` with
  `createUser`, `restoreSeedData` and `updateMarketData` (final constructor); the internal controllers that call them,
  `MarketDataView`, and the beans.
- [ ] T022 Adjust tests (passes at once): rename `DeleteOrderServiceTest` → `InternalToolsServiceDeleteOrderTest`
  (g, b, c, d) and `ExecuteOrderServiceTest` → `InternalToolsServiceExecuteOrderTest` (g, b, c, d);
  `CancelVersusExecutionIT` (a, b, d).
- [ ] T023 Implement `deleteOrder` and `executeOrder` in `InternalToolsUseCase` and `InternalToolsService`; the
  internal controller that calls them, and the beans.

## Slice 011-i — Internal tools controller (branch `011-consolidation-i`)

**Estimated size**: ~330 lines. No test file changes.

- [ ] T024 Implement plan.md § REST, `InternalToolsController` and its nested DTOs. `InternalToolsArchitectureTest`,
  `InternalToolsDisabledIT`, `PublishedContractMatchesRoutesIT` and the internal-tool ITs pass unmodified.

## Slice 011-j — Public controllers and DTOs (branch `011-consolidation-j`)

**Estimated size**: ~260 lines. No test file changes.

- [ ] T025 Implement plan.md § REST: `OrderController` absorbs `OrderQueryController`; the nested request bodies,
  `PortfolioResponse` and `InstrumentPageResponse` with their nested DTOs.

## Slice 011-k — Configuration (branch `011-consolidation-k`)

**Estimated size**: ~410 lines. No test file changes. Predefined split if it exceeds 400: **011-k1** (branch
`011-consolidation-k1`) is T026; **011-k2** (branch `011-consolidation-k2`, after 011-k1 is merged) is T027 and T028.

- [ ] T026 Implement plan.md § Configuration, `ApplicationConfiguration` (it replaces `ClockConfiguration`,
  `OrderConfiguration`, `OrderQueryConfiguration`, `CashMovementConfiguration`, `PortfolioConfiguration` and
  `InstrumentSearchConfiguration`).
- [ ] T027 Implement plan.md § Configuration, `WebConfiguration` (with the Jackson customizer) and
  `ObservabilityConfiguration`. Count the production files: exactly the 103 of plan.md § Target structure (SC-001);
  paste `find src/main/java -name '*.java' | wc -l` in the PR.
- [ ] T028 Run `./mvnw verify -Pvolume` and paste its latency table (SC-003).

---

## Definition of done (spec 011)

- [ ] Slices merged; `./mvnw verify` green; `preservation.sh` prints `OK` on `main`.
- [ ] `src/main/java` has the 103 files of plan.md § Target structure; the ArchUnit rules, traceability tests,
  features, contract and migrations are those of v1.0.0.
- [ ] `./mvnw verify -Pvolume` passes.
