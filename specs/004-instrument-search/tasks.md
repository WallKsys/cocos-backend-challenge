# Tasks: Instrument search (spec 004)

**Input**: `specs/004-instrument-search/` and `src/test/resources/features/cu-02-instrument-search.feature`

**Before starting**: specs 001–003 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/004-instrument-search`.

**Slices**: three PRs, in order. Each: tests → skeletons → confirm failure → implement → full suite → PR size check.

---

## Slice 004-a — Domain rules (branch `004-instrument-search-a`)

**Estimated size**: ~330 lines.

- [ ] T001 [P] Write unit tests `PercentageTest`, `MarketQuoteTest`, `SearchTermTest`, `SearchCursorTest`,
  `InstrumentSearchCriteriaTest` (plan.md § Domain rules), with the CU tags of plan.md § Tests.
- [ ] T002 Create skeletons of the domain types and `SearchTermTooShortException`. Run
  `./mvnw test -Dgroups=unit`: **tests fail**. Record it.
- [ ] T003 Implement until green; `./mvnw verify` green.

**Checkpoint**: open PR `004-a`.

---

## Slice 004-b — Repository and service (branch `004-instrument-search-b`)

**Estimated size**: ~390 lines. If it exceeds 400, split into 004-b1 (service + its unit test) and 004-b2
(repository + its IT).

- [ ] T004 [P] Write `SearchInstrumentsServiceTest` with a fake `InstrumentSearchRepository`.
- [ ] T005 [P] Write `JdbcInstrumentSearchRepositoryIT` (`@IntegrationTest`), one test per row of plan.md § Tests that
  has an integration test, with the exact expected tickers of the feature file.
- [ ] T006 Create skeletons of `SearchInstrumentsQuery`, `InstrumentSearchRepository`, `SearchInstrumentsService`,
  `JdbcInstrumentSearchRepository`, `InstrumentSearchConfiguration`. Run: **tests fail**. Record it.
- [ ] T007 Implement plan.md § Application and § Persistence until green; ArchUnit green.

**Checkpoint**: open PR `004-b`.

---

## Slice 004-c — Endpoint, BDD and contract (branch `004-instrument-search-c`)

**Estimated size**: ~380 lines (the copied contract and the feature file are excluded). If it exceeds 400, split
into 004-c1 (BDD infrastructure, T008–T009) and 004-c2 (endpoint, steps, contract).

- [ ] T008 Create the BDD infrastructure of plan.md § BDD infrastructure (`SeedData`, `DatabaseHooks`,
  `ScenarioContext`, `CommonResponseSteps`, moving the response-status step out of `HealthSteps`). Run
  `./mvnw verify`: the smoke scenario still passes.
- [ ] T009 Copy the feature file and enable its traceability:
  `cp specs/004-instrument-search/features/cu-02-instrument-search.feature src/test/resources/features/`;
  append `CU-02` to `src/test/resources/traceability/scope.txt`; write `traceability/FeaturesMatchSpecsTest`
  (plan.md § Feature files are copied, never written) and confirm it passes with the copied file and fails if one
  byte of the copy is changed (then revert the change).
- [ ] T010 [P] Write `InstrumentSearchSteps` for `cu-02-instrument-search.feature`.
- [ ] T011 [P] Write `InstrumentSearchControllerIT` (CU-02.11, CU-02.13 and the spec.md edge cases).
- [ ] T012 Copy the contract: `cp specs/004-instrument-search/contracts/openapi.yaml src/main/resources/static/openapi/openapi.yaml`.
- [ ] T013 Create the skeleton of `InstrumentSearchController` and its DTOs. Run `./mvnw verify`: **the CU-02
  scenarios, the controller IT and the contract route test fail**. Record it.
- [ ] T014 Implement plan.md § REST and § Errors until green (BDD 15 scenarios, traceability with `CU-02`, contract).
- [ ] T015 [P] Create `http/004-instrument-search.http` (plan.md § HTTP examples).

**Checkpoint**: open PR `004-c`.

---

## Definition of done (spec 004)

- [ ] Slices merged; `./mvnw verify` green on `main`.
- [ ] Cucumber summary includes the 17 CU-02 scenarios (21 runs counting outline examples) passing.
- [ ] `CU-02` in the traceability scope.
