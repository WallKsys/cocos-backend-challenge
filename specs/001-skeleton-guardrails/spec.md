# Feature Specification: Project skeleton and guardrails

**Feature Branch**: `001-skeleton-guardrails-<slice>` (one branch per PR slice, see `tasks.md`)

**Created**: 2026-11-21

**Status**: Approved for implementation

**Input**: RFC decisions TEC-06, TEC-07, TEC-08, TEC-10, TEC-11, TEC-12, TEC-13, TEC-14, TEC-23, TEC-25, TEC-28, TEC-30
and Constitution v1.2.0 (Principles III, IV, V, VI, X).

## Context

This spec delivers **no business functionality**. It builds the empty, runnable project and the automatic
guardrails that every later spec relies on:

- the build, the application entry point and a health endpoint;
- the local run with `docker compose up`;
- the architecture rules (ArchUnit) that make the hexagonal layout impossible to break silently;
- the test harness (unit, integration with real PostgreSQL, and BDD with Cucumber);
- the traceability check that guarantees every acceptance scenario `CU-xx.yy` has its Gherkin scenario and
  its tests.

From the moment this spec is merged, any later code that breaks an architecture rule, or any scenario left
without tests, fails the build.

## User Scenarios & Testing *(mandatory)*

The "users" of this spec are the developers (human or AI agent) of the following specs, and the reviewer.

### User Story 1 - Build and run the empty application (Priority: P1)

A developer clones the repository, runs one command and gets a running application connected to PostgreSQL,
reporting itself healthy.

**Why this priority**: nothing else can be built or tested without it.

**Independent Test**: `./mvnw verify` is green; `docker compose up` starts PostgreSQL and the application, and
`GET /actuator/health` answers `{"status":"UP"}`.

**Acceptance Scenarios**:

1. **Given** a fresh clone with Java 25 and Docker installed, **When** the developer runs `./mvnw verify`,
   **Then** the build succeeds and all tests pass.
2. **Given** Docker is running, **When** the developer runs `docker compose up --build`, **Then** PostgreSQL and
   the application start, and `GET http://localhost:8080/actuator/health` returns HTTP 200 with
   `"status":"UP"`.
3. **Given** the application is running, **When** the database is unreachable, **Then** the health endpoint
   reports `DOWN` (HTTP 503).

---

### User Story 2 - Architecture rules break the build when violated (Priority: P1)

A developer (or agent) who puts a class in the wrong layer, uses a forbidden dependency or forgets
`@Transactional` on a use case gets a failing build that names the rule.

**Why this priority**: the implementing agent is not the reviewer; the rules are the main safety net (TEC-04).

**Independent Test**: run `ArchitectureTest`. It passes on the clean skeleton, and each rule fails when a
deliberately violating class is compiled (verified during development, see `tasks.md`, then the violating
class is deleted).

**Acceptance Scenarios**:

1. **Given** the skeleton, **When** `ArchitectureTest` runs, **Then** every rule passes (rules may have no
   classes to check yet; empty packages are allowed).
2. **Given** a class in `com.cocos.challenge.domain` that imports a Spring class, **When** the tests run,
   **Then** the rule "domain depends only on domain and the JDK" fails.
3. **Given** a class in `com.cocos.challenge.application.service` implementing a `...UseCase` interface whose
   public method lacks `@Transactional`, **When** the tests run, **Then** the transactional rule fails.
4. **Given** a class outside `infrastructure.adapter.out.persistence` (and `infrastructure.config`) that uses
   `JdbcClient`, **When** the tests run, **Then** the persistence rule fails.

---

### User Story 3 - Test harness for unit, integration and BDD tests (Priority: P1)

A developer can write a unit test (no Spring, no database), an integration test against a real PostgreSQL
started automatically by Testcontainers, and a Cucumber scenario that calls the running application over HTTP.

**Why this priority**: every later spec starts by writing tests (Constitution Principle III).

**Independent Test**: a smoke unit test, a smoke integration test and a smoke Cucumber scenario run and pass
with `./mvnw verify`.

**Acceptance Scenarios**:

1. **Given** a JUnit test tagged `unit`, **When** `./mvnw test -Dgroups=unit` runs, **Then** it runs without
   starting Spring or Docker.
2. **Given** an integration test (`*IT`, tagged `integration`), **When** `./mvnw verify` runs, **Then** a
   PostgreSQL container is started automatically and the Spring context connects to it.
3. **Given** the smoke feature `src/test/resources/features/000-smoke.feature`, **When** `./mvnw verify` runs,
   **Then** Cucumber executes it against the application started on a random port, and it passes.

---

### User Story 4 - Traceability check (Priority: P1)

The build verifies that every acceptance scenario in scope has exactly one Gherkin scenario and at least one
test, so no scenario can be forgotten (TEC-06, TEC-07).

**Why this priority**: it is the rule that turns "every scenario is verified" into something automatic.

**Independent Test**: `TraceabilityTest` passes with the empty scope of this spec, and its own unit tests
(over fixture files) prove that it detects each kind of violation.

**Acceptance Scenarios**:

1. **Given** the scope file lists no groups, **When** the check runs, **Then** it passes (and still validates
   that every `@CU-...` tag found in features exists in `docs/casos-de-uso.md`).
2. **Given** the scope lists `CU-02` and scenario `CU-02.01` has no Gherkin scenario, **When** the check runs,
   **Then** it fails naming `CU-02.01` and the missing item.
3. **Given** a Gherkin scenario tagged `@CU-02.01` but no JUnit test tagged `CU-02.01`, **When** the check runs
   with `CU-02` in scope, **Then** it fails naming the missing test.
4. **Given** two Gherkin scenarios tagged with the same ID, **When** the check runs, **Then** it fails naming
   the duplicate.
5. **Given** a tag `@CU-99.99` that does not exist in `docs/casos-de-uso.md`, **When** the check runs, **Then**
   it fails naming the unknown ID.
6. **Given** a test tagged `CU-02.01` that is tagged neither `unit` nor `integration`, **When** the check runs
   with `CU-02` in scope, **Then** it fails naming the test.

### Edge Cases

- Scenario IDs with a letter suffix (`CU-02.05b`) and internal-tool IDs (`CU-I.01`) are valid IDs.
- Lines in `docs/casos-de-uso.md` that are not table rows starting with `| CU-` are ignored.
- The scope file accepts blank lines and `#` comments.
- The smoke feature is tagged `@smoke`, carries no `@CU-` tag and is ignored by the traceability check.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project MUST build with `./mvnw verify` using Java 25, Spring Boot 4.1.3 and the exact
  dependencies listed in `plan.md`.
- **FR-002**: The application MUST expose `GET /actuator/health` (and only `health` and `info` actuator
  endpoints in this spec).
- **FR-003**: `docker compose up --build` MUST start PostgreSQL 18 and the application with the `demo` profile.
- **FR-004**: The package layout MUST match Constitution Principle V. Empty packages are created only when a
  class needs them (no placeholder classes).
- **FR-005**: `ArchitectureTest` MUST contain every rule listed in `plan.md`, exactly as written there.
- **FR-006**: Unit tests (`*Test`, tag `unit`) MUST run with Maven Surefire; integration tests and the Cucumber
  runner (`*IT`, tag `integration`) MUST run with Maven Failsafe during `verify`.
- **FR-007**: The traceability check MUST implement every rule in `plan.md` § Traceability and report all
  violations in a single failure message.
- **FR-008**: `src/Main.java` (IntelliJ sample) MUST be deleted.
- **FR-009**: A GitHub Actions workflow MUST run `./mvnw verify` on every pull request and push to `main`
  (plan.md § Continuous integration); its `verify` check becomes required for merging into `main`.

### Key Entities

None (no business data in this spec).

## Success Criteria *(mandatory)*

- **SC-001**: From a fresh clone, `docker compose up --build` reaches a healthy application in under 5 minutes
  (PRD M-15).
- **SC-002**: `./mvnw verify` is green.
- **SC-003**: Every ArchUnit rule has been seen failing at least once against a deliberately violating class
  during development (evidence in the PR description), then the violating class was removed.
- **SC-004**: The traceability check's own tests cover the six acceptance scenarios of User Story 4.

## Assumptions

- Docker is available locally and in CI (Testcontainers and Compose need it).
- Java 25 is installed (for example `sdk install java 25-tem`).
- No business tables exist yet: Flyway runs with no migrations in this spec (spec 002 adds them).
