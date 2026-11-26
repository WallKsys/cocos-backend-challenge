# Feature Specification: Observability

**Feature Branch**: `010-observability-<slice>` (one branch per PR slice, see `tasks.md`)

**Created**: 2026-11-21

**Status**: Implemented (2026-11-25)

**Input**: RFC TEC-27 (structured logs, correlation id), TEC-31 (OpenTelemetry, business metrics, dashboards);
PRD RNF-04, RNF-09. The `BusinessMetrics` port and its Micrometer adapter already exist (spec 003) and every use
case already records its facts (specs 005, 007, 008).

## Context

Make the running system observable end to end: metrics, traces and logs exported with OpenTelemetry to a local
`grafana/otel-lgtm` stack started by `docker compose up`, with logs correlated to traces, and two provisioned
dashboards: application health and business. Business history comes from SQL over the ledger with a read-only
database user; real-time business rates come from the Micrometer counters.

This spec adds **no endpoint and no business rule**. It has no CU scenarios; its acceptance tests verify that
telemetry actually reaches the stack.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the health of the application (Priority: P1)

An operator opens Grafana and sees availability, request rate, error rate and p95/p99 latency per endpoint, JVM and
connection pool.

**Independent Test**: after `docker compose up` and some requests, the "Cocos · Application health" dashboard shows
data in every panel.

### User Story 2 - See the business (Priority: P1)

An operator sees, in real time, processed operations per minute, the share of FILLED / NEW / REJECTED orders,
rejections by reason, idempotent replays, idempotency conflicts and lock wait; and historically (from the ledger)
total transactions, success and rejection rates, rejections by reason and traded volume by side.

**Independent Test**: after placing a few orders, the "Cocos · Business" dashboard shows them in both the
real-time and the historical panels.

### User Story 3 - Follow one request from metric to logs (Priority: P2)

From a slow request or an error, an operator jumps to its trace in Tempo and from the trace to its log lines in
Loki, which carry the same `trace_id` and the `requestId`.

**Independent Test**: an integration test sends a request with `X-Request-Id: obs-it-1` and finds, in the stack, a
trace of that request and a log line with `requestId = obs-it-1` and the same `trace_id`.

### Edge Cases

- When the stack is not running (for example `./mvnw spring-boot:run` without Compose), the application starts and
  works normally; exporters fail silently in the background (no request fails because of telemetry).
- Metrics never carry high-cardinality tags (Constitution: user id, order id, idempotency key, ticker).
- Tests that do not need telemetry must not try to export it (export disabled by default in tests).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Metrics, traces and logs MUST be exported through OTLP/HTTP to the `lgtm` service of Docker Compose,
  using the Spring Boot 4.1 properties of plan.md § Configuration (not the deprecated Boot 3 names).
- **FR-002**: Logs MUST be bridged from Logback to OpenTelemetry with the appender of plan.md § Logs, keeping the
  structured console output, and MUST include the MDC key `requestId`.
- **FR-003**: Traces MUST be sampled at 100 % in the demo profile.
- **FR-004**: Grafana MUST provision a read-only PostgreSQL data source and the two dashboards of plan.md
  § Dashboards from files in the repository.
- **FR-005**: The application MUST keep working when the stack is unreachable.
- **FR-006** (TEC-27): Every client operation with effect (order placement, cancellation, deposit, withdrawal) that
  completes with a response MUST write one INFO log line with `orderId`, `userId`, idempotency key (when the
  operation has one) and result, as defined in plan.md § Operation logs.

## Success Criteria *(mandatory)*

- **SC-001**: `docker compose up --build` starts PostgreSQL, the application and `lgtm`; Grafana at
  `http://localhost:3000` shows both dashboards with data after running `http/005-order-placement.http`.
- **SC-002**: `OperationLogIT` proves that each operation of FR-006 writes its line with the request's `requestId`.
- **SC-003**: `ObservabilityIT` proves that metrics (with the expected names and tags), a trace and a correlated
  log line reach the stack.
