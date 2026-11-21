# Implementation Plan: Observability

**Branch**: `010-observability-<slice>` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

## Summary

Export metrics, traces and logs with OpenTelemetry (OTLP/HTTP) to `grafana/otel-lgtm` in Docker Compose; bridge
Logback to OpenTelemetry while keeping structured console logs; provision a read-only PostgreSQL data source and two
dashboards. Every configuration value below was verified end to end on 2026-11-21.

## Verified facts (Spring Boot 4.1.3, OpenTelemetry 1.62.0, grafana/otel-lgtm 0.41.1 with Grafana 13.3.1)

- `spring-boot-starter-opentelemetry` brings `micrometer-registry-otlp`, `micrometer-tracing-bridge-otel` and
  `opentelemetry-exporter-otlp`. **Spring Boot 4.1 property names** (the Boot 3 names `management.otlp.tracing.*` and
  `management.otlp.logging.*` are deprecated):

  | Signal | Endpoint property | Switch |
  |---|---|---|
  | Metrics | `management.otlp.metrics.export.url` | `management.otlp.metrics.export.enabled` |
  | Traces | `management.opentelemetry.tracing.export.otlp.endpoint` | `management.tracing.export.otlp.enabled` |
  | Logs | `management.opentelemetry.logging.export.otlp.endpoint` | `management.logging.export.otlp.enabled` |

- **Spring Boot does not bridge Logback to OpenTelemetry.** The bridge is
  `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` and **its version must match the OpenTelemetry
  API managed by Spring Boot (1.62.0): use `2.28.0-alpha`**. The latest version (2.34.0-alpha) is built against
  1.68.0 and fails at runtime with `ClassNotFoundException: io.opentelemetry.api.incubator.common.ExtendedAttributeKey`,
  which made every logging call fail and requests hang.
- With a custom `logback-spring.xml`, structured console logs require
  `org/springframework/boot/logging/logback/structured-console-appender.xml` (with `console-appender.xml` the output
  is plain text). Verified output: `{"@timestamp":...,"log":{"level":"INFO",...},"message":...,"traceId":...,
  "spanId":...,"requestId":...,"ecs":{"version":"8.11"}}`.
- Names as stored in Prometheus (OTLP translation): `cocos.orders.processed` → `cocos_orders_processed_total` with
  labels `side`, `type`, `status`, `rejection_reason` plus `job` and `service_name` = `cocos-broker-api`;
  `cocos.account.lock.wait` → `cocos_account_lock_wait_milliseconds_bucket|_count|_sum`; HTTP server →
  `http_server_requests_milliseconds_bucket|_count|_sum` with labels `method`, `uri` (template), `status`, `outcome`,
  `error`, `exception`; JVM → `jvm_memory_used_bytes`; CPU → `process_cpu_usage`.
- Logs reach Loki with structured metadata `requestId`, `trace_id`, `span_id`, `service_name`; the trace with the
  same id reaches Tempo (span `http get /probe` in the probe).
- `spring-boot-testcontainers` + `testcontainers-grafana` (`org.testcontainers.grafana.LgtmStackContainer`) with
  `@ServiceConnection` configure the three OTLP exporters automatically.
- Image `grafana/otel-lgtm:0.41.1`: Grafana on 3000 (anonymous admin), OTLP gRPC 4317 / HTTP 4318, Prometheus 9090,
  Tempo 3200; built-in data sources with uids `prometheus`, `tempo`, `loki`; provisioning directory
  `/otel-lgtm/grafana/conf/provisioning/`. The PostgreSQL plugin id is `grafana-postgresql-datasource`.
- Mounting the files of `observability/` provisions both dashboards in folder "Cocos"; the `cocos-ledger` data
  source reports `Database Connection OK` and a panel query through Grafana returns BUY 202300 / SELL 55300.
- The read-only role can `SELECT` every table and gets `permission denied` on writes.

## Files

Copy byte-identical from `specs/010-observability/observability/` to the repository root folder `observability/`
(they are configuration artifacts, excluded from the PR size count like the other copied spec artifacts):

```text
observability/
├── postgres/01-grafana-reader.sql
└── grafana/
    ├── provisioning/datasources/cocos-datasources.yaml
    ├── provisioning/dashboards/cocos-dashboards.yaml
    └── dashboards/{cocos-health.json, cocos-business.json}
```

`scripts/check-pr-size.sh` already excludes `observability/**` (added with this spec).

## Dependencies (`pom.xml`)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
<dependency>
    <!-- Must match the OpenTelemetry API version managed by Spring Boot 4.1.3 (1.62.0). Do not upgrade alone. -->
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-appender-1.0</artifactId>
    <version>2.28.0-alpha</version>
</dependency>

<!-- test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-grafana</artifactId>
    <scope>test</scope>
</dependency>
```

## Configuration

`application.yml`, default document — telemetry export **off** by default (tests and local runs without the stack):

```yaml
management:
  otlp:
    metrics:
      export:
        enabled: false
        step: 10s
  tracing:
    sampling:
      probability: 1.0
    export:
      otlp:
        enabled: false
  logging:
    export:
      otlp:
        enabled: false
  metrics:
    distribution:
      percentiles-histogram:
        "[http.server.requests]": true
        "[cocos.account.lock.wait]": true
```

Demo profile document — export **on**, endpoints from `OTLP_BASE_URL`:

```yaml
management:
  otlp:
    metrics:
      export:
        enabled: true
        url: ${OTLP_BASE_URL:http://localhost:4318}/v1/metrics
  tracing:
    export:
      otlp:
        enabled: true
  logging:
    export:
      otlp:
        enabled: true
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: ${OTLP_BASE_URL:http://localhost:4318}/v1/traces
    logging:
      export:
        otlp:
          endpoint: ${OTLP_BASE_URL:http://localhost:4318}/v1/logs
```

Merge these keys into the existing `management:` blocks of each document (do not create a second `management:` key in
the same document).

## Logs

`src/main/resources/logback-spring.xml` — write exactly:

```xml
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/structured-console-appender.xml"/>
    <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
        <captureMdcAttributes>requestId</captureMdcAttributes>
    </appender>
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="OTEL"/>
    </root>
</configuration>
```

`infrastructure/config/OpenTelemetryLoggingConfiguration.java`: a bean of type `InitializingBean` that calls
`OpenTelemetryAppender.install(openTelemetry)` with the `OpenTelemetry` bean provided by Spring Boot.

`logging.structured.format.console: ecs` (spec 001) stays as is.

## Docker Compose

Replace `compose.yaml` with:

```yaml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: cocos
      POSTGRES_USER: cocos
      POSTGRES_PASSWORD: cocos
    ports:
      - "5432:5432"
    volumes:
      - ./observability/postgres/01-grafana-reader.sql:/docker-entrypoint-initdb.d/01-grafana-reader.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U cocos -d cocos"]
      interval: 2s
      timeout: 3s
      retries: 30

  lgtm:
    image: grafana/otel-lgtm:0.41.1
    ports:
      - "3000:3000"
    volumes:
      - ./observability/grafana/provisioning/datasources/cocos-datasources.yaml:/otel-lgtm/grafana/conf/provisioning/datasources/cocos-datasources.yaml:ro
      - ./observability/grafana/provisioning/dashboards/cocos-dashboards.yaml:/otel-lgtm/grafana/conf/provisioning/dashboards/cocos-dashboards.yaml:ro
      - ./observability/grafana/dashboards:/otel-lgtm/cocos-dashboards:ro

  app:
    build: .
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/cocos
      DB_USER: cocos
      DB_PASSWORD: cocos
      SPRING_PROFILES_ACTIVE: demo
      OTLP_BASE_URL: http://lgtm:4318
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      lgtm:
        condition: service_started
```

## Dashboards

Already written (do not edit; copy). `cocos-health` (14 panels: requests per second, 5xx and 4xx rates, p95, traffic
and status per endpoint, p95/p99 per endpoint, JVM heap, database connections, CPU) and `cocos-business` (18 panels:
real time from the counters — operations per minute by side, orders by status, rejected share, rejections by reason,
replays, conflicts, cancellations, lock wait p95/p99; historical from the ledger — total transactions, success and
rejection rate of trades, orders by status, rejections by reason, traded volume by side, deposits and withdrawals,
orders per day). Every Prometheus query filters `service_name="cocos-broker-api"`; every SQL query uses the
`cocos-ledger` data source.

## Tests

| Test | Kind | Verifies |
|---|---|---|
| `DashboardsTest` | unit | both JSON files parse; uids `cocos-health` / `cocos-business`; every Prometheus `expr` contains `service_name="cocos-broker-api"`; every SQL target uses data source uid `cocos-ledger`; no query mentions `userid`, `idempotency`, `ticker` or `orderId` labels; the files in `observability/` are byte-identical to `specs/010-observability/observability/` |
| `DashboardSqlIT` | integration | every `rawSql` of `cocos-business.json` (with `$__timeFilter(datetime)` replaced by `TRUE`) runs on the seed without error; seed values: total 11; success 55.56; rejection 11.11; `NOT_RECORDED` 1; BUY 202300.00 / SELL 55300.00; CASH_IN 1000000.00 / CASH_OUT 100000.00; statuses CANCELLED 1, FILLED 7, NEW 2, REJECTED 1 |
| `ObservabilityIT` | integration, own context | `LgtmStackContainer("grafana/otel-lgtm:0.41.1")` with `@ServiceConnection` + the shared PostgreSQL; properties: the three `enabled` switches `true`, `management.otlp.metrics.export.step=2s`. Sends `POST /api/v1/orders` (user 1, BUY MARKET 1 PAMP) with `X-Request-Id: obs-it-1`, then polls (up to 60 s): **metrics** — Prometheus series `cocos_orders_processed_total{side="BUY",type="MARKET",status="FILLED"}` exists and its label names are exactly `__name__, job, service_name, side, type, status, rejection_reason`; `http_server_requests_milliseconds_count{uri="/api/v1/orders"}` and `hikaricp_connections_active` exist; every Prometheus `expr` of both dashboards returns `status: success`. **Logs** — Loki has a line of `service_name="cocos-broker-api"` with `requestId="obs-it-1"` and a `trace_id`. **Traces** — Tempo `GET /api/traces/<that trace_id>` returns spans of the request |
| `TelemetryUnavailableIT` | integration, own context | the three switches `true` and every endpoint pointing to `http://127.0.0.1:1` (nothing listening): `POST /api/v1/orders` still answers 201 and `GET /api/v1/portfolio` 200 (FR-005) |

Use an HTTP client configured for **HTTP/1.1 with timeouts** to query the stack from tests (the probe hung with
the JDK client's default HTTP/2 negotiation and no timeout).

## Constitution updates (done by the spec author)

The pitfalls table gains: Boot 3 OTLP property names → Boot 4.1 names; the Logback appender version rule.
