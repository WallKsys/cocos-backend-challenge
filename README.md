# Cocos Broker API

[Leer en español](README.es.md)

[![CI](https://github.com/WallKsys/cocos-backend-challenge/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/WallKsys/cocos-backend-challenge/actions/workflows/ci.yml)
[![PR size](https://github.com/WallKsys/cocos-backend-challenge/actions/workflows/pr-size.yml/badge.svg)](https://github.com/WallKsys/cocos-backend-challenge/actions/workflows/pr-size.yml)
![Java 25](https://img.shields.io/badge/Java-25-blue)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)
![PostgreSQL 18](https://img.shields.io/badge/PostgreSQL-18-336791)

REST API for an investment broker covering the user's **portfolio**, **instrument search**, **buy and sell orders**
(`MARKET` and `LIMIT`, by quantity or amount), deposits, withdrawals, cancellation, and history.
It implements the [Cocos backend challenge](docs/referencia/backend-challenge.md).

---

## Contents

- [Status](#status)
- [What it solves](#what-it-solves)
- [Quick start](#quick-start)
- [API](#api)
- [API conventions](#api-conventions)
- [Architecture](#architecture)
- [Data model](#data-model)
- [Quality and testing](#quality-and-testing)
- [Observability](#observability)
- [Configuration](#configuration)
- [Assumptions and limitations](#assumptions-and-limitations)
- [Engineering process](#engineering-process)
- [Documentation](#documentation)
- [Repository structure](#repository-structure)

---

## Status

All ten challenge features (*specs*) have been implemented on `main` since v1.0.0, each delivered through small,
reviewed pull requests. Spec 011 followed in v1.1.0 and consolidated classes without changing behavior
([TEC-38](docs/rfc.md)). It reduced the number of production files from 165 to 103 while retaining the same SQL and
running the same tests as v1.0.0.

| # | Feature | Status |
|---|---|---|
| 001 | Service skeleton and quality guardrails (build, CI, ArchUnit, BDD, traceability) | ✅ Complete |
| 002 | Database schema and seed data from the challenge, plus documented migrations | ✅ Complete |
| 003 | API conventions (errors, identity, amounts, idempotency) | ✅ Complete |
| 004 | Instrument search | ✅ Complete |
| 005 | Order placement | ✅ Complete |
| 006 | Portfolio | ✅ Complete |
| 007 | Deposits and withdrawals | ✅ Complete |
| 008 | Cancellation and history | ✅ Complete |
| 009 | Internal demo tools and volume tests | ✅ Complete |
| 010 | Observability (metrics, traces, logs, and dashboards) | ✅ Complete |
| 011 | Consolidation of persistence by table and of ports and controllers by resource, with no behavior change | ✅ Complete |

---

## What it solves

The challenge asks for three endpoints. The underlying problem is **keeping the books accurate**. A broker must
prevent negative balances, duplicate purchases after network retries, and exposure of another user's data.
The design provides five guarantees.

| Guarantee | How it works |
|---|---|
| **One source of truth** | The `orders` table is the ledger. Cash, positions, and reservations are always calculated from it |
| **Users can trade only what they have** | Pending orders **reserve** the cash or shares they commit, and validation uses the *available* balance |
| **An operation runs only once** | The client supplies an idempotency key (`Idempotency-Key`) that is stored with the order and protected by a unique constraint |
| **Concurrent orders cannot spend the same balance** | Each operation takes a pessimistic lock on the user's row within its transaction |
| **User isolation by design** | Public URLs contain no user identifier. The API takes the caller's identity solely from the `X-User-Id` header |

---

## Quick start

**Requirements** — Java 25 and Docker.

```bash
git clone https://github.com/WallKsys/cocos-backend-challenge.git
cd cocos-backend-challenge

docker compose up --build        # PostgreSQL + API (demo profile) with the challenge data
```

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Grafana | http://localhost:3000 |

These example requests use sample user `1`. The challenge data loads automatically.

```bash
# Portfolio
curl -s http://localhost:8080/api/v1/portfolio -H 'X-User-Id: 1'

# Search instruments
curl -s "http://localhost:8080/api/v1/instruments?q=banco"

# Buy PAMP by amount in pesos (54 shares at 925.85, with the remainder still available)
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' -H 'X-User-Id: 1' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"instrumentId": 47, "side": "BUY", "type": "MARKET", "amount": "50000.00"}'
```

The [`http/`](http/) directory contains sample requests for the IntelliJ HTTP client, with internal tools in a
separate file.

---

## API

The service publishes the full OpenAPI 3.1 contract at `/openapi/openapi.yaml` and makes it browsable in Swagger UI.
[OpenAPI contract](specs/009-internal-tools-volume/contracts/openapi.yaml)

### Endpoints

| Method | Route | Description |
|---|---|---|
| `GET` | `/api/v1/portfolio` | Total value, cash (total, reserved, available), and positions with value, average cost, total return, and daily change |
| `GET` | `/api/v1/instruments` | Paginated search by general term, ticker, and/or name, ignoring case and accents |
| `POST` | `/api/v1/orders` | Places a `BUY` or `SELL` order of type `MARKET` or `LIMIT`, by whole-share quantity or amount |
| `GET` | `/api/v1/orders` | Order and cash movement history, newest first, with an optional status filter |
| `GET` | `/api/v1/orders/{orderId}` | Retrieves one of the user's orders |
| `POST` | `/api/v1/orders/{orderId}/cancel` | Cancels a `NEW` order and releases its reservation |
| `POST` | `/api/v1/cash/deposits` | Deposits pesos |
| `POST` | `/api/v1/cash/withdrawals` | Withdraws pesos against the available balance |

### Internal tools (demo only)

These tools are outside the challenge scope. They require both the `demo` profile and `X-Role: ADMIN`.
If either condition is missing, the service returns `404`, just as it does for an unknown route.

| Method | Route | Description |
|---|---|---|
| `GET` | `/api/internal/users/{userId}/portfolio` | Retrieves any user's portfolio |
| `GET` | `/api/internal/users/{userId}/orders` | Retrieves any user's history |
| `POST` | `/api/internal/orders/{orderId}/execute` | Forces a pending `LIMIT` order to execute at its limit price |
| `DELETE` | `/api/internal/orders/{orderId}` | Deletes an order |
| `PUT` | `/api/internal/instruments/{instrumentId}/market-data` | Changes an instrument's last price |
| `POST` | `/api/internal/reset` | Restores the original challenge data |
| `POST` | `/api/internal/users` | Creates a test user |

### Order statuses

```text
                 ┌─ Valid MARKET ──────────────────────────────► FILLED
  POST /orders ──┼─ Valid LIMIT ──► NEW ──┬─ cancel ───────────► CANCELLED
                 │                        └─ execute (demo) ───► FILLED
                 └─ Insufficient balance or amount, no price ──► REJECTED (with reason)

  Invalid request (format, fields, unknown instrument) → 400, nothing stored.
```

---

## API conventions

| Topic | Convention |
|---|---|
| Identity | `X-User-Id` header. Authentication is outside the challenge scope. See [limitations](#assumptions-and-limitations) |
| Idempotency | The `Idempotency-Key` header is required for orders, deposits, and withdrawals. Cancellation is inherently idempotent and takes no key. Repeating the same request with the same key returns the original result (`200` with `Idempotent-Replayed: true`). Reusing the key with different content returns `422 IDEMPOTENCY_CONFLICT` |
| Amounts and prices | Decimal strings with exactly 2 decimal places in responses (`"925.85"`) and up to 2 in requests (`"925.8"`). Share quantities are integers. Floating-point values are excluded |
| Dates | ISO 8601 in UTC (`2026-11-21T14:36:00Z`). Clients handle local time conversion |
| Errors | [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details with a stable `code` and an English message containing the concrete values |
| Language | All API text is in English |
| Strict JSON | Unknown fields and type coercions (`1.5` as a quantity or `10` as a string) return `VALIDATION_ERROR` |
| Pagination | Opaque cursors (`cursor`, `limit`, `nextCursor`) |
| Correlation | The service generates an `X-Request-Id` header if absent and includes it in the response and each log line |

A rejected order is stored and returned with HTTP `201` and a rejection reason.

```json
{
  "orderId": 13,
  "status": "REJECTED",
  "quantity": 1000,
  "price": "925.85",
  "rejection": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "Insufficient available cash in account 10001: required ARS 925850.00, available ARS 627500.00."
  }
}
```

An invalid request returns an error and leaves no record in the database.

```json
{
  "type": "https://api.cocos.example/errors/validation-error",
  "title": "Validation error",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "detail": "Request validation failed: 'price' is required for LIMIT orders.",
  "instance": "/api/v1/orders"
}
```

---

## Architecture

The service uses **hexagonal architecture** (ports and adapters). Dependencies point inward, and
[ArchUnit](https://www.archunit.org/) verifies them on every build.

```text
              ┌─────────────────────── infrastructure ───────────────────────┐
  HTTP ─────► │ adapter/in/rest  (controllers, DTOs, RFC 9457 errors)          │
              │         │                                                    │
              │         ▼                                                    │
              │ ┌────────────────────── application ───────────────────────┐ │
              │ │ use cases · inbound and outbound ports                  │ │
              │ │ ┌─────────────────────── domain ───────────────────────┐ │ │
              │ │ │ business rules in plain Java                         │ │ │
              │ │ └──────────────────────────────────────────────────────┘ │ │
              │ └─────────────────────────────┬────────────────────────────┘ │
              │                               ▼                              │
              │ adapter/out/persistence (Jdbc*, explicit SQL)                │──► PostgreSQL
              │ adapter/out/metrics (Micrometer) · config                    │
              └──────────────────────────────────────────────────────────────┘
```

| Layer | Contains | May depend on |
|---|---|---|
| `domain` | Model and rules for availability, reservations, order evaluation, and average cost | JDK only |
| `application` | Use cases, inbound and outbound ports | `domain` and `@Transactional` |
| `infrastructure` | REST, persistence with `JdbcClient`, metrics, and configuration | All of the above and Spring |

The [RFC](docs/rfc.md) documents the technical decisions and alternatives considered.

- **Explicit SQL without an ORM** (`JdbcClient`). Account locking, atomic idempotency, and ledger aggregations are
  written and tested as they run (TEC-12).
- **Per-user pessimistic locking** (`SELECT ... FOR UPDATE`). Concurrent requests from the same user wait for
  the lock while requests from different users proceed in parallel (TEC-16).
- **Balances calculated on demand from `orders`**, using covering indexes. In a benchmark with 10 million orders,
  the portfolio for a user with 10,000 orders took about 15 ms of SQL execution time (TEC-17).
- **Contract first**. The OpenAPI contract is written before the code, and a test requires the routes to match
  the contract (TEC-22).

---

## Data model

The schema and data supplied with the challenge are loaded verbatim in Flyway migration `V1`. Subsequent changes
are recorded in separate migrations with documented rationale.

| Migration | Changes |
|---|---|
| `V1` | Byte-for-byte copy of the challenge's `database.sql` |
| `V2` | Integrity constraints, unique ticker, and `TIMESTAMPTZ` dates (UTC) |
| `V3` | Rejection reason, idempotency key and request fingerprint, and `NUMERIC(18,2)` amounts |
| `V4` | Normalized, accent-free columns and trigram indexes for search |
| `V5` | Indexes for balances, reservations, and cursor-based history |
| `V6` | Requested amount for orders placed by amount, so identical retries can replay the original result |
| `V7` | Two rejection reasons for prices outside the ±20% band and quantities that are too large |
| `V8` | Partial index for listing tradable instruments by ticker when no search criteria are provided |

See the [complete schema](specs/002-database/data-model.md) and the rationale for each change in the
[functional analysis, §8](docs/analisis-funcional.md).

---

## Quality and testing

```bash
./mvnw verify                    # full suite: unit, integration, BDD, ArchUnit, traceability
./mvnw test -Dgroups=unit        # unit tests only (fast, no Docker)
./mvnw verify -Pvolume           # volume tests: 10M orders, p95/p99 by endpoint (spec 009)
```

| Level | Tool | What it verifies |
|---|---|---|
| Unit | JUnit + AssertJ | Domain rules without Spring or a database |
| Integration | Testcontainers (real PostgreSQL 18) | SQL, locks, idempotency during concurrent retries, and pagination |
| Acceptance | Cucumber (BDD) | **120 [use case scenarios](docs/casos-de-uso.md)** with values calculated from the challenge data |
| Architecture | ArchUnit | Layers, dependencies, `@Transactional`, and bans on ORM and `double` for money |
| Traceability | Custom test | The build fails if any scenario lacks a corresponding Gherkin scenario or at least one test |
| Query plans | `EXPLAIN` during the build | The build fails if a critical query stops using its indexes |
| Volume | `-Pvolume` profile | PRD p95 targets for portfolio (< 200 ms), history (< 100 ms), order placement (< 150 ms), and search (< 100 ms) |

At the v1.0.0 release, the suite had 275 unit tests, 258 integration tests, and 139 BDD executions across 120
scenarios. The suite has nearly as many integration tests as unit tests because much of the logic deliberately
lives in SQL and the database, including balances calculated from `orders`, search, locking, and idempotency.
Each rule is tested at the lowest level that can verify it, with unit tests for domain rules. The full suite
takes less than 2 minutes. The rationale and the alternative considered are documented in the [RFC, TEC-37](docs/rfc.md).

The latest `-Pvolume` benchmark used 10,000,011 orders and 100,066 instruments in PostgreSQL 18, with 500 measured
HTTP requests per metric. [PR #99](https://github.com/WallKsys/cocos-backend-challenge/pull/99)

| Metric | Operation | p95 | p95 target | p99 | p99 target |
|---|---|---|---|---|---|
| M-08 | `GET /api/v1/portfolio` | 23.6 ms | < 200 ms | 28.4 ms | < 400 ms |
| M-09 | `GET /api/v1/orders?limit=20` | 3.2 ms | < 100 ms | 5.0 ms | < 200 ms |
| M-10 | `POST /api/v1/orders` (`MARKET` buy) | 8.5 ms | < 150 ms | 10.9 ms | < 300 ms |
| M-11 | `GET /api/v1/instruments?q=...` | 24.6 ms | < 100 ms | 29.5 ms | < 200 ms |

GitHub Actions runs the following checks on each pull request.

- **`verify`** runs the full suite and is required to merge.
- **`pr-size`** enforces a limit of 400 reviewable lines per PR unless justified and is required to merge.
- **`review`** checks the changes against the specs, the plan, and the project rules.

---

## Observability

The service exports metrics, traces, and logs through **OpenTelemetry** to a local Grafana stack
(`grafana/otel-lgtm`) started by `docker compose up`.

- **Cocos · Application health** shows traffic, 4xx/5xx errors, p95/p99 latency by endpoint, JVM metrics,
  connections, and CPU usage.
- **Cocos · Business** shows operations per minute, orders by status, rejections by reason, idempotent retries,
  conflicts, and lock wait times. It also shows historical totals calculated from the ledger with a
  read-only database user.
- Each log entry carries `requestId` and `trace_id`, so you can follow a log entry in Loki to the request trace
  in Tempo.
- Each operation that changes data (order placement, cancellation, deposit, or withdrawal) emits an
  `Operation completed` log entry with the user, order, idempotency key, outcome, and rejection reason.
- `ObservabilityIT` starts the entire stack during the build and verifies that metrics, traces, and logs arrive
  and can be correlated.

Console logs use structured JSON in ECS format.

---

## Configuration

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/cocos` | PostgreSQL JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `cocos` / `cocos` | Database credentials |
| `SPRING_PROFILES_ACTIVE` | — | `demo` enables internal tools and telemetry export |
| `OTLP_BASE_URL` | `http://localhost:4318` | OTLP/HTTP destination for metrics, traces, and logs (`demo` profile) |

| Port | Service |
|---|---|
| 8080 | API |
| 5432 | PostgreSQL |
| 3000 | Grafana |

---

## Assumptions and limitations

- **No authentication** (outside the challenge scope). Callers declare their identity with `X-User-Id`, and the
  service accepts it as supplied. The implementation and tests enforce user isolation. In production, identity
  would come from a token and the URLs could stay the same.
- **Static prices.** Prices come from the challenge's `marketdata` table (latest date 2023-07-14). A `LIMIT`
  order stays `NEW` until it is canceled or executed with the demo tool.
- **Inconsistent source data.** User 1 has a **−10 BMA** position because they sold more shares than they bought.
  The portfolio preserves the original value and flags it as inconsistent.
- **No commissions.** Amounts are rounded to 2 decimal places using *half-up* rounding.
- **Malformed HTTP requests**, such as a control character in a header, are rejected by Tomcat before reaching the
  application. Tomcat returns its own 400 response outside the API's error format.
- **Language.** The challenge suggests Node.js. This implementation deliberately uses **Java 25 + Spring Boot 4.1**.
  The functional analysis and API contract are language independent. [PRD §1.1](docs/prd.md), [RFC TEC-01](docs/rfc.md)

---

## Engineering process

The project follows **spec-driven development** using [GitHub Spec Kit](https://github.com/github/spec-kit).

1. **Functional analysis** of the challenge, covering explicit rules, ambiguities, and 31 product decisions with
   their rationale.
2. **PRD** with goals and success metrics, and **RFC** with 37 technical decisions and the alternatives considered.
3. **Project constitution** with mandatory rules for architecture, money, concurrency, testing, and PR size.
4. **One spec per feature**, with a plan, OpenAPI contract, Gherkin scenarios, and tasks divided into reviewable PRs.
5. **Test-first implementation** by an AI coding agent that follows the specs, with automated and manual review
   of each PR. Approved tests are protected against changes intended to make them pass.

The rules index records each rule the agent follows, where and why it was decided, and whether it is enforced
during the build, merge, or review. [Rules and guardrails](docs/reglas-y-barreras.md)

---

## Documentation

The documentation in `docs/` is also published as a searchable site. It is rebuilt on each merge to `main`.
To preview it locally, run `pip install -r requirements-docs.txt && zensical serve`.
[Documentation site](https://wallksys.github.io/cocos-backend-challenge/)

| Document | Contents |
|---|---|
| [Functional analysis](docs/analisis-funcional.md) | Domain, challenge rules, ambiguities, and product decisions (DEC-xx) |
| [Use cases](docs/casos-de-uso.md) | 120 acceptance scenarios (given / when / then) with real values |
| [PRD](docs/prd.md) | Goals, scope, requirements, and success metrics |
| [RFC](docs/rfc.md) | Technical decisions (TEC-xx) and alternatives considered |
| [Constitution](.specify/memory/constitution.md) | Mandatory engineering rules |
| [Rules and guardrails](docs/reglas-y-barreras.md) | Index of rules, where they were decided, and how they are enforced |
| [Specs](specs/) | Spec, plan, contract, scenarios, and tasks for each feature |
| [Original challenge](docs/referencia/backend-challenge.md) | Challenge brief and supplied `database.sql` |

---

## Repository structure

```text
.
├── src/main/java/com/cocos/challenge/    service code (domain · application · infrastructure)
├── src/main/resources/                  configuration and Flyway migrations
├── src/test/                            unit, integration, BDD (features), and architecture tests
├── http/                                sample request collection
├── observability/                       Grafana dashboards and provisioning
├── specs/                               one directory per feature
├── docs/                                analysis, use cases, PRD, RFC, and challenge material
├── .specify/                            Spec Kit constitution, templates, and scripts
├── .github/                             workflows (CI, PR size, review) and PR template
├── compose.yaml · Dockerfile            local runtime
└── pom.xml · mvnw                       build (Maven)
```
