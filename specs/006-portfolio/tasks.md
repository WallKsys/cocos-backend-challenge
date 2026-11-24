# Tasks: Portfolio (spec 006)

**Input**: `specs/006-portfolio/` (spec, plan, contract, feature)

**Before starting**: specs 001–005 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/006-portfolio`.

**Slices**: three planned slices, each with a predefined split point if it exceeds 400 counted lines. Each: tests → skeletons → confirm failure → implement → full suite → PR size check.

---

## Slice 006-a — Domain calculations (branch `006-portfolio-a`)

**Manual review**: required. If the slice is split, both halves get it. Focus: the moving average, the reset at exactly 0 and returns computed from the unrounded average.

**Estimated size**: ~380 lines. If it exceeds 400 counted lines (measure after the tests and skeletons), split it as follows, in this order; each
half follows the test-first cycle on its own and leaves the suite green:
- **006-a1:** `Fill`, `AverageCostCalculator` and `AverageCostCalculatorTest`.
- **006-a2:** `PortfolioWarning`, `PortfolioWarningCode`, `Position`, `Portfolio`, `PositionTest` and `PortfolioTest`.

- [x] T001 [P] Write `AverageCostCalculatorTest` (CU-01.12, 01.13, 01.14 and the restart rule for a buy with
  quantity ≤ 0).
- [x] T002 [P] Write `PositionTest` (CU-01.02, 01.03, rounding from the unrounded average) and `PortfolioTest`
  (CU-01.01, 01.06, 01.07, `NEGATIVE_CASH`), with the exact warning messages of plan.md.
- [x] T003 Skeletons of `Fill`, `PortfolioWarning`, `PortfolioWarningCode`, `Position`, `Portfolio`,
  `AverageCostCalculator`. **Fail**. Record.
- [x] T004 Implement plan.md § Portfolio rules until green.

## Slice 006-b — Readers and use case (branch `006-portfolio-b`)

**Estimated size**: ~370 lines. If it exceeds 400 counted lines (measure after the tests and skeletons), split it as follows, in this order; each
half follows the test-first cycle on its own and leaves the suite green:
- **006-b1:** the readers (`JdbcAccountReader`, `JdbcPortfolioReader`, `latestQuotes`) and `JdbcPortfolioReaderIT`.
- **006-b2:** `GetPortfolioService`, `PortfolioConfiguration`, `GetPortfolioServiceTest` and `GetPortfolioServiceIT`.

- [ ] T005 [P] Write `JdbcPortfolioReaderIT` (CU-01.04, 01.05, 01.14) and a test for `latestQuotes`.
- [ ] T006 [P] Write `GetPortfolioServiceTest` (CU-01.08; also checks by reflection that `get` is read-only with
  `Isolation.REPEATABLE_READ`, plan.md § Application) and `GetPortfolioServiceIT` (CU-01.01, 01.07, 01.11, FR-003).
- [ ] T007 Skeletons of the ports, adapters, `GetPortfolioService`, `PortfolioConfiguration`. **Fail**. Record.
- [ ] T008 Implement plan.md § Application and § Persistence until green; ArchUnit green.

## Slice 006-c — Endpoints, BDD and contract (branch `006-portfolio-c`)

**Estimated size**: ~360 lines (feature and contract copies excluded). If it exceeds 400 counted lines (measure after the tests and skeletons), split it as follows, in this order; each
half follows the test-first cycle on its own and leaves the suite green:
- **006-c1:** both controllers, the DTOs, the contract copy, `PortfolioControllerIT`, `InternalPortfolioControllerIT`
  and `http/006-portfolio.http`.
- **006-c2:** the feature copy, `CU-01` in `scope.txt`, internal tools enabled in `CucumberSpringConfiguration` and
  `PortfolioSteps`.

- [ ] T009 Copy the feature and the contract; append `CU-01` to `scope.txt`; enable internal tools in
  `CucumberSpringConfiguration`.
- [ ] T010 [P] Write `PortfolioSteps` (plan.md § BDD).
- [ ] T011 [P] Write `PortfolioControllerIT` (CU-01.09) and `InternalPortfolioControllerIT` (CU-01.10).
- [ ] T012 Skeletons of the controllers and DTOs. Run `./mvnw verify`: **CU-01 scenarios fail**. Record.
- [ ] T013 Implement plan.md § REST until green (14 scenarios; contract route test with the new routes).
- [ ] T014 [P] `http/006-portfolio.http`.

---

## Definition of done (spec 006)

- [ ] Slices merged; `./mvnw verify` green on `main`; 14 CU-01 scenarios passing; `CU-01` in scope.
