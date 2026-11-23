# Tasks: Internal tools and volume verification (spec 009)

**Input**: `specs/009-internal-tools-volume/` (spec, plan, contract, feature, volume dataset)

**Before starting**: specs 001–008 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/009-internal-tools-volume`.

**Slices**: four PRs. Each: tests → skeletons → confirm failure → implement → full suite → PR size check.

---

## Slice 009-a — Plan guard (branch `009-internal-tools-volume-a`)

**Estimated size**: ~250 lines.

- [ ] T001 Check that every `Jdbc*` adapter exposes its SQL as package-private constants (and the search and history
  adapters their package-private SQL builder methods), as plans 004 to 008 require. Refactor only an adapter that
  does not, with no behavior change: all tests stay green unmodified.
- [ ] T002 Write `PlanGuardIT` (plan.md § Plan guard). It should pass immediately; if a query fails, **stop and
  report** (it means an index is missing or a query cannot use it).

## Slice 009-b — Order tools: delete and execute (branch `009-internal-tools-volume-b`)

**Estimated size**: ~380 lines.

- [ ] T003 [P] Write `DeleteOrderServiceTest`, `ExecuteOrderServiceTest`, `JdbcAdminOrderRepositoryIT`.
- [ ] T004 Skeletons of the use cases, ports, adapter, `OrderNotExecutableException`, `ORDER_NOT_EXECUTABLE`. **Fail**.
  Record.
- [ ] T005 Implement plan.md § Internal tools (delete, execute) and `InternalOrderToolsController` until green.
- [ ] T006 Switch CU-04.08 to the real tool (plan.md § CU-04.08 with the real tool); run `./mvnw verify` three times.

## Slice 009-c — Data tools, BDD and contract (branch `009-internal-tools-volume-c`)

**Estimated size**: ~390 lines (feature and contract copies excluded). Split into c1 (market data + users) and c2
(restore + BDD + contract) if needed.

- [ ] T007 [P] Write `UpdateMarketDataServiceTest`, `JdbcMarketDataWriterIT`, `JdbcSeedDataRestorerIT`, `JdbcUserWriterIT`.
- [ ] T008 Copy the feature and the contract; append `CU-I` to `scope.txt`; write `ScopeIsCompleteTest`.
- [ ] T009 [P] Write `InternalToolsSteps` and `InternalToolsControllerIT`.
- [ ] T010 Skeletons. Run `./mvnw verify`: **CU-I scenarios fail**. Record.
- [ ] T011 Implement the remaining tools until green (9 CU-I scenarios).
- [ ] T012 [P] `http/009-internal-tools.http`.

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
