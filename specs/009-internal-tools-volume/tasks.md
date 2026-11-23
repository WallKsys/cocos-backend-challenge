# Tasks: Internal tools and volume verification (spec 009)

**Input**: `specs/009-internal-tools-volume/` (spec, plan, contract, feature, volume dataset)

**Before starting**: specs 001–008 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/009-internal-tools-volume`.

**Slices**: six PRs (009-a, 009-b, 009-c1, 009-c2, 009-c3, 009-d), in order. Each: tests → skeletons → confirm failure → implement → full suite → PR size check.

---

## Slice 009-a — Plan guard (branch `009-internal-tools-volume-a`)

**Estimated size**: ~250 lines.

- [ ] T001 Check that every `Jdbc*` adapter exposes its SQL as package-private constants (and the search and history
  adapters their package-private SQL builder methods), as plans 004 to 008 require. Refactor only an adapter that
  does not, with no behavior change: all tests stay green unmodified.
- [ ] T002 Write `PlanGuardIT` (plan.md § Plan guard). It should pass immediately; if a query fails, **stop and
  report** (it means an index is missing or a query cannot use it).

## Slice 009-b — Order tools: delete and execute, use cases only (branch `009-internal-tools-volume-b`)

**Estimated size**: ~330 lines. **No controller in this slice.** `PublishedContractMatchesRoutesIT` requires the
implemented routes and the published contract to be identical, and the spec 009 contract contains every internal
tool route. So all the internal controllers of spec 009 arrive together with that contract, in slice 009-c2.

- [ ] T003 [P] Write `DeleteOrderServiceTest`, `ExecuteOrderServiceTest`, `JdbcAdminOrderRepositoryIT`.
- [ ] T004 Skeletons of the use cases, ports, adapter, `OrderNotExecutableException` (extends `DomainException`
  directly) and `ORDER_NOT_EXECUTABLE`. **Fail**. Record.
- [ ] T005 Implement plan.md § Internal tools (delete, execute: use cases and persistence) until green.
- [ ] T006 In `CancelVersusExecutionIT`, add the test that runs `CancelOrderUseCase` and `ExecuteOrderUseCase`
  concurrently, 20 repetitions (plan.md § CU-04.08 with the real tool). Run `./mvnw verify` three times. The BDD step
  switches to the real endpoint in slice 009-c3.

## Slice 009-c — Data tools, endpoints, BDD and contract (three PRs)

The previous single slice (~390 lines, with the order tools controller left in 009-b) is split in three, so that the
contract and every internal route land together.

### Slice 009-c1 — Data tools: use cases (branch `009-internal-tools-volume-c1`)

**Estimated size**: ~300 lines.

- [ ] T007 [P] Write `UpdateMarketDataServiceTest`, `JdbcMarketDataWriterIT`, `JdbcSeedDataRestorerIT`, `JdbcUserWriterIT`.
- [ ] T007a Skeletons of the data tool use cases, ports and adapters (no controllers). **Fail**. Record.
- [ ] T007b Implement plan.md § Internal tools (market data, restore, create user) until green.

### Slice 009-c2 — Internal endpoints and contract (branch `009-internal-tools-volume-c2`)

**Estimated size**: ~300 lines (contract copy excluded).

- [ ] T008 Copy the contract: `cp specs/009-internal-tools-volume/contracts/openapi.yaml src/main/resources/static/openapi/openapi.yaml`.
- [ ] T009 [P] Write `InternalToolsControllerIT`: every internal tool route of spec 009 (status codes, bodies,
  `ORDER_NOT_EXECUTABLE`, ids 0 or negative → 400 DEC-26, without the admin role → 404).
- [ ] T010 Skeletons of `InternalOrderToolsController`, `InternalMarketDataController`, `InternalDataController` and
  their DTOs. Run `./mvnw verify`: **`InternalToolsControllerIT` and the route test fail**. Record.
- [ ] T011 Implement plan.md § REST until green.
- [ ] T012 [P] `http/009-internal-tools.http`.

### Slice 009-c3 — BDD and complete traceability (branch `009-internal-tools-volume-c3`)

**Estimated size**: ~300 lines (feature copy excluded).

- [ ] T012a Copy the feature; append `CU-I` to `scope.txt`; write `ScopeIsCompleteTest`.
- [ ] T012b Write `InternalToolsSteps`. Switch the step `the administrator forces its execution at the same time` to
  `POST /api/internal/orders/{id}/execute` with `X-Role: ADMIN` (plan.md § CU-04.08 with the real tool); delete
  `ForcedExecutionSimulator` if no test uses it any more. Run `./mvnw verify`: **the CU-I scenarios fail**. Record.
- [ ] T012c Implement the remaining glue until green (9 CU-I scenarios). Run `./mvnw verify` three times (CU-04.08).

## Slice 009-d — Volume profile (branch `009-internal-tools-volume-d`)

**Estimated size**: ~250 lines (the dataset copy is small).

- [ ] T013 Copy `volume-dataset.sql`; add the Failsafe `excludedGroups` and the `volume` profile to `pom.xml`.
- [ ] T014 Write `VolumeIT` (plan.md § Volume profile).
- [ ] T015 Run `./mvnw verify` (the volume test must **not** run) and `./mvnw verify -Pvolume` (it must run and pass).
  Paste the printed latency table in the PR. If a target is not met, **stop and report** with the table.

---

## Definition of done (spec 009)

- [ ] Slices merged; `./mvnw verify` green (including `PlanGuardIT` and `ScopeIsCompleteTest`).
- [ ] `./mvnw verify -Pvolume` green on the reference environment; latency table in the PR.
- [ ] 9 CU-I scenarios passing; CU-04.08 uses the real tool; the scope covers every CU group.
