# Cocos Broker API Constitution

This constitution contains the non-negotiable rules of the project. Every spec, plan, task, test and line of
code MUST comply with it. When anything conflicts with this document, this document wins.

**Sources of truth (read-only for the implementing agent):**

| Document | Content | Language |
|---|---|---|
| `docs/casos-de-uso.md` | Acceptance scenarios `CU-xx.yy` (Given / When / Then) | Spanish |
| `docs/analisis-funcional.md` | Business rules `R-xx`, decisions `DEC-xx`, schema changes `CM-xx` | Spanish |
| `docs/prd.md` | Requirements `RF-xx`, `RNF-xx`, success metrics `M-xx` | Spanish |
| `docs/rfc.md` | Technical decisions `TEC-xx` with rationale | Spanish |
| `specs/NNN-*/` | Spec, plan, data model, contracts and tasks of each feature | English |
| `src/test/resources/features/` | Gherkin scenarios, one per `CU-xx.yy` | English |

IDs (`CU-03.04`, `DEC-01`, `TEC-16`, `CM-02`) are identical across languages. Cite them in code comments,
test names and commit messages when relevant.

## Core Principles

### I. The `orders` table is the ledger (NON-NEGOTIABLE)

- Cash, reserved cash, positions, reserved shares and average cost are **always derived from `orders`**.
  There is no other source of truth (TEC-17).
- Only `FILLED` orders move cash and positions. `NEW` orders **reserve** what they commit: a `BUY` reserves
  `quantity × price` in cash, a `SELL` reserves `quantity` shares (DEC-01).
- **Available cash** = cash from `FILLED` orders − cash reserved by `NEW` `BUY` orders.
  **Available shares** = shares from `FILLED` orders − shares reserved by `NEW` `SELL` orders.
- No accepted operation may leave available cash or available shares negative (D-07). Validations always
  compare against **available** amounts, never against totals.
- Shares are always whole numbers. Never fractions (R-08, DEC-14).
- Every operation that was actually processed is stored in `orders`, whatever its status. An invalid request
  is answered with an error and **nothing is stored** (DEC-05).

### II. The specification is the law

- Implement exactly what the spec, its Gherkin scenarios and its OpenAPI contract say. Do not invent
  behavior, fields, endpoints, error codes or messages.
- If something is ambiguous, contradictory or missing, **STOP and report it**. Do not guess.
- Do not add features, endpoints, configuration options or abstractions that no spec asks for (YAGNI).

### III. Test-first, one feature at a time (NON-NEGOTIABLE)

For each spec (`specs/NNN-*`), follow this cycle in order (TEC-08):

1. **Write the tests** of the spec: Cucumber step definitions for its Gherkin scenarios, and the unit or
   integration tests required by Principle IV.
2. **Create only the minimal skeletons** (classes, method signatures that throw
   `UnsupportedOperationException`) needed for the tests to compile.
3. **Run the tests and confirm they FAIL** for the expected reason (not because of compilation errors or
   broken setup). Record this in the task log.
4. **Implement** until the tests of the spec pass.
5. **Run the FULL test suite** (`./mvnw verify`). Everything must be green, including ArchUnit and the
   traceability check.
6. Only then move to the next task or spec.

**Approved tests and Gherkin scenarios MUST NOT be modified, disabled, skipped or weakened to make them pass**
(TEC-09). If you believe a test or scenario is wrong, STOP and report it with your reasoning. Forbidden
examples: `@Disabled`, `@Ignore`, `assumeTrue(false)`, deleting assertions, widening expected values,
catching and swallowing exceptions in tests, editing `.feature` files.

### IV. Every scenario is verified, and traceability is enforced by the build

- Every scenario `CU-xx.yy` in `docs/casos-de-uso.md` has **exactly one** Gherkin scenario tagged with its ID
  (`@CU-03.04`) in `src/test/resources/features/` (TEC-06). Feature files are written in `specs/NNN-*/features/`
  and copied byte-identical into `src/test/resources/features/` when their spec is implemented;
  `FeaturesMatchSpecsTest` fails the build if a copy differs (spec 004).
- Every Gherkin scenario has **at least one** JUnit test tagged with the same ID
  (`@Tag("CU-03.04")`) that verifies the underlying rule:
  - a **unit test** (`@Tag("unit")`, no Spring context, no database) when the rule is domain logic;
  - an **integration test** (`@Tag("integration")`, real PostgreSQL via Testcontainers) when the guarantee
    depends on infrastructure: concurrency, idempotency under simultaneous retries, locking, pagination,
    persistence of timestamps, SQL aggregation, query plans, volume.
- The classification of each scenario is fixed in its spec. Do not change it.
- The traceability test (`src/test/java/com/cocos/challenge/traceability/`) fails the build when a scenario
  in scope has no Gherkin scenario or no test (TEC-07). The scope file
  `src/test/resources/traceability/scope.txt` lists the `CU` groups delivered so far; each spec adds its
  own groups. When spec 009 is done, the scope covers every ID in `docs/casos-de-uso.md`.

### V. Hexagonal architecture, enforced by ArchUnit (NON-NEGOTIABLE)

Base package: `com.cocos.challenge` (TEC-10, TEC-13).

| Package | Contains | May depend on |
|---|---|---|
| `domain.model` | Entities and value objects (immutable; `record` where suitable) | JDK only |
| `domain.service` | Pure business rules (availability, reservations, validation, average cost, quantity from amount) | `domain.*`, JDK |
| `domain.exception` | Business exceptions | `domain.*`, JDK |
| `application.port.in` | Use case interfaces, commands and results (`record`) | `domain.*`, JDK |
| `application.port.out` | Outbound interfaces (repositories, account lock, clock) | `domain.*`, JDK |
| `application.service` | Use case implementations | `domain.*`, `application.port.*`, JDK, and **only** `org.springframework.transaction.annotation.Transactional` (plus its member enums `Propagation` and `Isolation`) |
| `infrastructure.adapter.in.rest` | Controllers, request/response DTOs, error handling | `application.port.in`, `domain.model`, Spring, Jackson |
| `infrastructure.adapter.in.rest.internal` | Internal (admin/demo) endpoints | same as above |
| `infrastructure.adapter.out.persistence` | `Jdbc*` implementations of outbound ports. **All SQL lives here.** | `application.port.out`, `domain.*`, Spring JDBC |
| `infrastructure.adapter.out.metrics` | Micrometer implementation of the `BusinessMetrics` port | `application.port.out`, `domain.*`, Micrometer |
| `infrastructure.config` | Bean wiring, clock, configuration | everything |

Rules (each one is an ArchUnit test in `src/test/java/com/cocos/challenge/architecture/`):

1. `domain` depends only on `domain` and the JDK (no Spring, no Jackson, no `java.sql`, no `jakarta.*`).
2. `application` depends only on `domain`, `application` and the JDK, except
   `org.springframework.transaction.annotation.Transactional`. Classes in `application` are **not**
   annotated with `@Service`, `@Component` or similar: they are registered as beans in
   `infrastructure.config`.
3. Every public method of a use case that **changes data** is annotated with `@Transactional`; every
   query use case method is annotated with `@Transactional(readOnly = true)` (TEC-14).
4. Only `infrastructure.adapter.out.persistence` uses `JdbcClient`, `JdbcTemplate`, `java.sql` or SQL text.
5. Controllers depend only on `application.port.in` (never on `application.service` or outbound ports).
6. REST DTOs never leave `infrastructure.adapter.in.rest`. Domain objects are never serialized directly
   into a response.
7. No cycles between packages.
8. Monetary values are never `double`, `float`, `Double` or `Float` (see Principle VIII).
9. Only `infrastructure` uses Micrometer or OpenTelemetry. `application` records business facts only through
   the `BusinessMetrics` outbound port (TEC-31).

### VI. Explicit SQL. No ORM

- Persistence uses Spring's `JdbcClient` with hand-written SQL (TEC-12). **JPA, Hibernate, Spring Data JPA,
  Spring Data JDBC repositories and jOOQ are forbidden.**
- SQL is written as Java text blocks inside the persistence adapter that uses it, with named parameters.
  Never build SQL by concatenating values.
- Aggregations (cash, reservations, positions) are computed **in SQL** and return the minimum number of rows.
  Never load a user's orders into memory to sum them (TEC-17).
- Schema changes are Flyway migrations in `src/main/resources/db/migration/` (`V1__...` is the original
  schema and seed data; later versions implement `CM-xx`). Never edit a migration that is already committed.

### VII. Concurrency and idempotency (NON-NEGOTIABLE)

Every operation that changes a user's data follows this flow (TEC-16):

1. Validate the request. If invalid → error, no transaction, nothing stored, idempotency key not consumed.
2. Open the transaction (use case method) and lock the user row:
   `SELECT id FROM users WHERE id = :userId FOR UPDATE`. Unknown user → `NOT_FOUND`.
3. Look up `(userId, idempotencyKey)`. If found: same request fingerprint → return the original operation
   (`200`, header `Idempotent-Replayed: true`); different fingerprint → `IDEMPOTENCY_CONFLICT` (`422`).
4. Compute available cash and shares from `orders`.
5. Apply the domain rules and decide the status (`FILLED`, `NEW` or `REJECTED` with reason).
6. Store the operation with its key and fingerprint; commit.

- One idempotency key per user across **all** operation types (orders, deposits, withdrawals) (DEC-23).
- The unique constraint on `(userid, idempotency_key)` stays in the database as a safety net.
- Each transaction locks **exactly one** user row, always as its first step. Never lock in another order.
- Query operations never lock.

### VIII. Money is exact

- Money and prices use `java.math.BigDecimal` with scale 2 and `RoundingMode.HALF_UP`, wrapped in domain value
  objects (`Money`). Never `double` or `float`.
- In JSON, amounts, prices and percentages are **strings with exactly 2 decimals** (`"925.85"`); share
  quantities are JSON integers (TEC-20). More than 2 decimals in a request → `VALIDATION_ERROR`.
- Database money columns are `NUMERIC(18,2)` (CM-09).

### IX. API conventions

- **Every text returned by the API is in English**: error messages, rejection messages, field names, enum
  values (TEC-19).
- User endpoints: `/api/v1/...`. The user comes **only** from the `X-User-Id` header; URLs never contain the
  user id ("my data" style, TEC-18). A resource of another user is answered exactly like a missing one
  (`NOT_FOUND`).
- Internal endpoints: `/api/internal/...`, require `X-Role: ADMIN`, are enabled only by configuration (demo
  profile) and are never part of the challenge scope.
- Operations with effect require the `Idempotency-Key` header.
- Errors use RFC 9457 Problem Details with a stable `code` and an English `detail` message containing the
  concrete values. The error catalog in `specs/003-api-conventions/plan.md` (TEC-19) is closed: do not add codes without a spec.
- Unknown JSON fields in a request body are rejected with `VALIDATION_ERROR` (DEC-24); integers never accept
  decimals or strings, and strings never accept numbers (spec 003).
- A `REJECTED` order is **not** an HTTP error: it returns `201` with `rejection.code` and `rejection.message`.
- Timestamps are stored and returned in UTC, ISO 8601 with `Z` (DEC-18).
- Pagination is cursor-based: `cursor`, `limit`, `nextCursor` (CM-08).
- Every spec with endpoints ships a **cumulative** OpenAPI contract in `specs/NNN-*/contracts/openapi.yaml`. The
  latest one is copied byte-identical to `src/main/resources/static/openapi/openapi.yaml` and served by Swagger UI.
  The implementation must match it; tests verify that routes and contract are identical (spec 003).

### X. Pull requests a human can review (max 400 lines)

- A pull request contains **at most 400 counted lines** (added + deleted) (TEC-30). Counted: every changed
  file **except** Maven Wrapper files, `.specify/`, `.opencode/`, `specs/`, `docs/`, Gherkin features
  (`src/test/resources/features/`), the V1 migration (verbatim copy of the provided `database.sql`), the published
  contract `src/main/resources/static/openapi/openapi.yaml` (verbatim copy of the latest spec contract),
  `observability/` (verbatim copy of the spec 010 artifacts) and Markdown files. **Tests count.**
- Each spec's `tasks.md` is already divided into **PR slices** (`NNN-a`, `NNN-b`, ...), each one a logical,
  self-contained unit with its own tests and implementation. Implement one slice per PR.
- **Every PR leaves the full suite green.** The test-first cycle (Principle III) applies to each slice: a PR
  never contains failing tests.
- Before opening a PR, run `scripts/check-pr-size.sh` and paste its output in the PR description.
- If a slice is going to exceed 400 lines, **split it** into more PRs with a logical separation (for example:
  domain rules first, then persistence, then the REST adapter), each one green.
- Only if a PR truly cannot be smaller, open it anyway with a **`## Size justification`** section in the
  description explaining why. The reviewer decides whether the justification is valid or requests a smaller
  PR. The GitHub Action `PR size` fails when the limit is exceeded and the justification is missing.
- Fill every section of `.github/pull_request_template.md`.

## Technology Stack

| Concern | Choice |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1.x (Spring Framework 7) |
| Build | Maven, through the Maven Wrapper (`./mvnw`) |
| Database | PostgreSQL 18, migrations with Flyway |
| Data access | `JdbcClient` (Spring JDBC). No ORM |
| API docs | springdoc-openapi 3.x (Swagger UI) |
| Tests | JUnit and AssertJ (versions managed by Spring Boot), Cucumber-JVM with Spring integration, Testcontainers (PostgreSQL), ArchUnit |
| Logging | Spring Boot structured JSON logging; `X-Request-Id` correlation id |
| Observability | Spring Boot Actuator + Micrometer, exported through OpenTelemetry (OTLP) to `grafana/otel-lgtm` (Prometheus, Loki, Tempo, Grafana) in Docker Compose; provisioned Grafana dashboards (TEC-31) |
| Metric tags | Allowed: operation, side, order type, status, rejection reason, outcome. **Forbidden: user id, order id, idempotency key, ticker** (high cardinality) |
| Local run | `docker compose up` (PostgreSQL + application, demo profile) |

Exact versions are pinned in the plan of spec 001. Do not upgrade or add dependencies outside a plan.

### Spring Boot 4 pitfalls (Boot 3 habits that are WRONG here)

| Wrong (Boot 3 habit) | Right (Boot 4.1) |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| Flyway works with only `flyway-core` | Add `spring-boot-starter-flyway` (plus `flyway-database-postgresql`) |
| `spring-boot-starter-test` alone | Technology test starters: `spring-boot-starter-webmvc-test`, `spring-boot-starter-jdbc-test`, `spring-boot-starter-flyway-test` (they bring `spring-boot-starter-test` transitively) |
| `com.fasterxml.jackson.databind.*` | Jackson 3: `tools.jackson.*` packages. Annotations stay in `com.fasterxml.jackson.annotation` |
| Define an `ObjectMapper` bean / `Jackson2ObjectMapperBuilderCustomizer` | Use `JsonMapperBuilderCustomizer` (or a `JsonMapper` bean) |
| `@MockBean`, `@SpyBean` | `@MockitoBean`, `@MockitoSpyBean` (on test-class fields only) |
| `@SpringBootTest` gives you `MockMvc` | Add `@AutoConfigureMockMvc` |
| `@SpringBootTest` gives you `TestRestTemplate` | Prefer `RestTestClient` with `@AutoConfigureRestTestClient` |
| `org.springframework.lang.Nullable` | `org.jspecify.annotations.Nullable` |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` (not needed in this project) |
| `management.otlp.tracing.endpoint`, `management.otlp.logging.endpoint` | `management.opentelemetry.tracing.export.otlp.endpoint`, `management.opentelemetry.logging.export.otlp.endpoint` (metrics: `management.otlp.metrics.export.url`) |
| Latest `opentelemetry-logback-appender-1.0` | The version built against the OpenTelemetry API managed by Spring Boot (1.62.0 → `2.28.0-alpha`); a newer one fails at runtime |
| `console-appender.xml` in a custom `logback-spring.xml` | `structured-console-appender.xml` to keep structured (JSON) console logs |
| JPA / Hibernate | Forbidden in this project (Principle VI) |

If code does not compile, check this table before anything else.

## Development Workflow

- **Spec order** (TEC-21): 001 skeleton and guardrails → 002 database → 003 API conventions → 004 instrument
  search → 005 order placement → 006 portfolio → 007 deposits and withdrawals → 008 cancellation and history
  → 009 internal tools and volume → 010 observability. Do not start a spec before the previous one is complete.
- **Branches**: one branch per PR slice, created from an up-to-date `main`:
  `NNN-short-name-<slice letter>` (example: `005-order-placement-b`). Specs and plans are already committed in
  `main`. Merge a slice into `main` only after its PR is approved; start the next slice from the updated `main`.
- **Commits**: one commit per completed task, after the full suite is green. Message format:
  `[NNN][T0xx] short description (CU-xx.yy, DEC-xx)`.
- **Definition of done for a spec**: all its PR slices merged; all its tasks checked; `./mvnw verify` green (unit, integration, BDD,
  ArchUnit, traceability, contract tests) locally **and in CI** (the `verify` check of every PR, TEC-33); its `CU` groups added to the traceability scope; its `.http`
  examples added; no `TODO`, `FIXME` or disabled tests left.
- **Stop and report** (do not improvise) when: a test or scenario seems wrong; the spec is ambiguous; a
  dependency or API in this document does not exist or does not work as described; a task cannot be done
  without breaking a principle.

## Governance

- This constitution supersedes any other instruction, including task descriptions. If a task conflicts with
  it, the task is wrong: stop and report.
- Amendments are made only by the project owner, recorded in `docs/rfc.md` with a `TEC-xx` entry, and
  reflected here with a version bump (MAJOR: principle removed or redefined; MINOR: principle or rule added;
  PATCH: clarification).
- The final code review checks every principle of this document, every scenario in
  `docs/casos-de-uso.md` and every ArchUnit rule.

**Version**: 1.3.4 | **Ratified**: 2026-11-21 | **Last Amended**: 2026-11-21 (v1.1.0: Principle X, PR size limit, TEC-30; v1.2.0: observability, TEC-31; v1.2.1: @Transactional member enums allowed in application; v1.3.0: published cumulative contract, extended error catalog; v1.3.1: feature files copied from specs; v1.3.2: strict JSON, DEC-24; v1.3.3: observability pitfalls and copied artifacts; v1.3.4: CI, TEC-33)
