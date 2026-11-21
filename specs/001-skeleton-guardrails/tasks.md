# Tasks: Project skeleton and guardrails (spec 001)

**Input**: `specs/001-skeleton-guardrails/` (spec.md, plan.md, research.md, quickstart.md)

**Before starting**: read `.specify/memory/constitution.md` and `AGENTS.md`. Select this spec with
`export SPECIFY_FEATURE_DIRECTORY=specs/001-skeleton-guardrails`.

**Format**: `[ID] [P?] Description` — `[P]` = can be done in parallel with the previous task (different files).

**Slices**: four PRs, in order. Each slice starts from an up-to-date `main` on its own branch, ends with
`./mvnw verify` green, `scripts/check-pr-size.sh` within 400 lines, and a PR using the template.

---

## Slice 001-a — Buildable, runnable application (branch `001-skeleton-guardrails-a`)

**Goal**: `./mvnw verify` green with the first tests; `docker compose up --build` serves a healthy application.
**Estimated size**: ~330 counted lines (wrapper files are excluded).

### Setup

- [ ] T001 Delete `src/Main.java`.
- [ ] T002 Add the Maven Wrapper exactly as described in plan.md § Maven Wrapper (`mvnw`, `mvnw.cmd`,
  `.mvn/wrapper/maven-wrapper.properties`).
- [ ] T003 Create `pom.xml` **verbatim** from plan.md § pom.xml. Run `./mvnw -q dependency:resolve`; if anything
  fails to resolve, stop and report.
- [ ] T004 Create the empty folder `src/main/resources/db/migration/` with a `.gitkeep` file.

### Tests first

- [ ] T005 [P] Create `src/test/java/com/cocos/challenge/SmokeUnitTest.java` (`@Tag("unit")`): one trivial
  assertion proving unit tests run without Spring (for example `assertThat(1 + 1).isEqualTo(2)`). It is the
  canary for `./mvnw test -Dgroups=unit`.
- [ ] T006 Create `src/test/java/com/cocos/challenge/support/PostgresTestcontainersConfiguration.java` and
  `support/IntegrationTest.java` as described in plan.md § Testcontainers.
- [ ] T007 Create `src/test/java/com/cocos/challenge/HealthEndpointIT.java` (annotated with `@IntegrationTest`):
  `GET /actuator/health` returns 200 and a JSON body whose `status` is `UP` (spec US1 scenario 2).
- [ ] T008 Create `src/test/java/com/cocos/challenge/HealthDownWhenDatabaseUnavailableIT.java`
  (`@Tag("integration")`, **not** `@IntegrationTest`): starts its **own** PostgreSQL container (not the shared
  one), starts the application against it with `@SpringBootTest(webEnvironment = RANDOM_PORT)` and
  `@DynamicPropertySource`, stops that container, then asserts `GET /actuator/health` returns 503 with
  `status` `DOWN` (spec US1 scenario 3). Use `@DirtiesContext` so the broken context is not reused.
- [ ] T009 Run `./mvnw verify`. **Expected: compilation fails or the ITs fail** because the application class
  does not exist. Record the failure in the task log (Constitution III step 3).

### Implementation

- [ ] T010 Create `src/main/java/com/cocos/challenge/CocosBrokerApiApplication.java` (plan.md § Application
  entry point).
- [ ] T011 Create `src/main/resources/application.yml` **verbatim** from plan.md § Configuration.
- [ ] T012 Run `./mvnw verify` until green.
- [ ] T013 [P] Create `Dockerfile`, `.dockerignore` and `compose.yaml` **verbatim** from plan.md § Docker.
- [ ] T014 [P] Create `http/000-health.http` from plan.md § HTTP examples.
- [ ] T015 Run `docker compose up --build`, call the health endpoint, paste the result in the PR description,
  then `docker compose down`.
- [ ] T015b Create `.github/workflows/ci.yml` **verbatim** from plan.md § Continuous integration. Open the PR and
  confirm that the `verify` check runs and is green on GitHub (link it in the PR description).

**Checkpoint 001-a**: `./mvnw verify` green locally **and** the `verify` check green on the PR;
`./mvnw test -Dgroups=unit` runs only `SmokeUnitTest` without Docker; Compose works. Open PR `001-a`.

**After merging 001-a (repository owner, not the agent)**: add `verify` to the required status checks of `main`.

---

## Slice 001-b — Architecture rules (branch `001-skeleton-guardrails-b`)

**Goal**: the ArchUnit rules of the constitution are active and proven to fail when violated.
**Estimated size**: ~230 counted lines.

- [ ] T016 Create `src/test/java/com/cocos/challenge/architecture/ArchitectureTest.java` **verbatim** from
  plan.md § ArchUnit rules. If it does not compile against ArchUnit 1.5.1, stop and report.
- [ ] T017 Run `./mvnw test -Dgroups=unit`: every rule passes on the skeleton.
- [ ] T018 **Prove each rule fails** (spec SC-003). For each rule, create a temporary violating class under
  `src/main/java/com/cocos/challenge/...`, run `./mvnw test -Dtest=ArchitectureTest`, confirm that rule fails
  with its `because(...)` message, then **delete the violating class**. Minimum set of violations:
  1. `domain/Bad.java` importing `org.springframework.stereotype.Component` (rule 1 fails).
  2. `application/service/BadService.java` annotated with `@org.springframework.stereotype.Service` (rules 2 and
     2-component fail).
  3. `application/port/in/BadUseCase.java` (interface with `void run();`) and
     `application/service/BadUseCaseService.java` implementing it **without** `@Transactional` (rule 3 fails);
     then add `@Transactional(readOnly = true)` and confirm it still fails; then `@Transactional` and confirm
     it passes.
  4. `infrastructure/adapter/in/rest/BadController.java` with a `JdbcClient` field (rule 4 fails).
  5. `domain/BadMoney.java` with a `double` field (rule 8 fails).

  Paste the failure lines of each case in the PR description under "Test-first evidence". **No violating class
  may remain in the PR.**

**Checkpoint 001-b**: `./mvnw verify` green with `ArchitectureTest`. Open PR `001-b`.

---

## Slice 001-c — BDD harness (branch `001-skeleton-guardrails-c`)

**Goal**: Cucumber scenarios run against the application on a random port with a real PostgreSQL.
**Estimated size**: ~90 counted lines (the feature file is excluded).

- [ ] T019 Create `src/test/resources/junit-platform.properties` **verbatim** from plan.md § Cucumber.
- [ ] T020 Create `src/test/java/com/cocos/challenge/bdd/CucumberIT.java` and
  `bdd/CucumberSpringConfiguration.java` (plan.md § Cucumber).
- [ ] T021 Run `./mvnw verify`. **Expected: the smoke scenario fails as undefined** (no step definitions).
  Record it (Constitution III step 3).
- [ ] T022 Create `src/test/java/com/cocos/challenge/bdd/steps/HealthSteps.java` with the three steps of
  `src/test/resources/features/000-smoke.feature` (already written; do not edit it), using `RestTestClient`.
- [ ] T023 Run `./mvnw verify` until green. Confirm the Cucumber summary shows `1 Scenarios (1 passed)` and
  that the scenario ran **once** (not twice).

**Checkpoint 001-c**: open PR `001-c`.

---

## Slice 001-d — Traceability check (branch `001-skeleton-guardrails-d`)

**Goal**: the build fails when a scenario in scope lacks its Gherkin scenario or its tests (TEC-06, TEC-07).
**Estimated size**: ~380 counted lines. If it exceeds 400, split it into `001-d1` (checker + its tests) and
`001-d2` (`TraceabilityTest` wiring the real files).

### Tests first

- [ ] T024 Create `src/test/resources/traceability/scope.txt` **verbatim** from plan.md § Traceability.
- [ ] T025 Create `src/test/java/com/cocos/challenge/traceability/TraceabilityCheckerTest.java`
  (`@Tag("unit")`) with in-memory fixtures. One test per rule of plan.md § Violations to report (rules 1 to 10),
  plus:
  - a passing case: one ID in scope with exactly one scenario and one test tagged with the ID and `unit`;
  - an empty-scope case: passes while still reporting an unknown `@CU-99.99` tag (rule 2);
  - ID formats: `CU-02.05b` and `CU-I.01` are recognized;
  - repeatable tags: a test element with tags `{"CU-02.01", "integration"}` satisfies rules 8 and 9.
  These cover spec User Story 4, scenarios 1 to 6.
- [ ] T026 Create `traceability/TraceabilityChecker.java` with only the method signatures, throwing
  `UnsupportedOperationException`. Run `./mvnw test -Dgroups=unit`: **the checker tests fail**. Record it.

### Implementation

- [ ] T027 Implement `TraceabilityChecker` following plan.md § Parsing rules and § Violations to report.
- [ ] T028 Create `traceability/TraceabilityTest.java` (`@Tag("unit")`) reading the real files and test classes
  as described in plan.md § Traceability → Files. It must report **all** violations in one assertion message.
- [ ] T029 Run `./mvnw verify` until green.
- [ ] T030 Prove it works on the real files: temporarily add `CU-02` to `scope.txt`, run
  `./mvnw test -Dtest=TraceabilityTest` and confirm it fails listing every `CU-02.xx` ID as missing; revert
  `scope.txt`. Paste the first lines of the failure in the PR description.

**Checkpoint 001-d**: `./mvnw verify` green. Open PR `001-d`.

---

## Definition of done (spec 001)

- [ ] Slices 001-a, 001-b, 001-c and 001-d merged into `main`.
- [ ] `verify` (CI) is a required status check of `main`, together with `pr-size`.
- [ ] `./mvnw verify` green on `main`; `docker compose up --build` healthy (quickstart.md).
- [ ] Every ArchUnit rule was seen failing (evidence in PR 001-b).
- [ ] The traceability check was seen failing on real files (evidence in PR 001-d).
- [ ] No `TODO`, `FIXME`, `@Disabled` or temporary violating classes left.
