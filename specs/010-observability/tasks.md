# Tasks: Observability (spec 010)

**Input**: `specs/010-observability/` (spec, plan, `observability/` artifacts)

**Before starting**: specs 001–009 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/010-observability`.

**Slices**: three PRs. Each: tests → skeletons/config → confirm failure → implement → full suite → PR size check.

---

## Slice 010-a — Export and log bridge (branch `010-observability-a`)

**Estimated size**: ~250 lines.

- [x] T001 Write `TelemetryUnavailableIT` (plan.md § Tests). It passes today (no telemetry yet); keep it as a
  regression guard.
- [x] T002 Add the dependencies of plan.md § Dependencies (exact versions), the configuration of § Configuration,
  `logback-spring.xml` of § Logs and `OpenTelemetryLoggingConfiguration`.
- [x] T003 Run `./mvnw verify`: everything green, console logs still JSON (check one line in the test output), and
  no exporter errors in the logs of normal tests (export is off by default).

## Slice 010-b — Grafana artifacts and Compose (branch `010-observability-b`)

**Estimated size**: ~250 lines (copied `observability/` excluded).

- [x] T004 Write `DashboardsTest` and `DashboardSqlIT`. Run: **they fail** (files not copied yet). Record it.
- [x] T005 Copy `specs/010-observability/observability/` to `observability/` (`cp -R`); replace `compose.yaml`
  (plan.md § Docker Compose).
- [x] T006 Run until green. Run `docker compose down -v && docker compose up --build`, execute
  `http/005-order-placement.http`, open `http://localhost:3000` → folder "Cocos": both dashboards with data. Paste
  screenshots or panel values in the PR.

## Slice 010-c — End-to-end telemetry test (branch `010-observability-c`)

**Estimated size**: ~250 lines.

- [ ] T007 Write `ObservabilityIT` (plan.md § Tests). It should pass with slices a and b; if a signal does not
  arrive, **stop and report** with what the stack returned.
- [ ] T008 Run `./mvnw verify` three times to confirm it is stable (polling with generous timeouts, no fixed sleeps
  except between polls).

---

## Definition of done (spec 010)

- [ ] Slices merged; `./mvnw verify` green.
- [ ] `docker compose up --build` shows both dashboards with data after some traffic; a log line in Loki links to its
  trace in Tempo.
