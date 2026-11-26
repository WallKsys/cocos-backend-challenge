# Tasks: Observability (spec 010)

**Input**: `specs/010-observability/` (spec, plan, `observability/` artifacts)

**Before starting**: specs 001–009 merged. `export SPECIFY_FEATURE_DIRECTORY=specs/010-observability`.

**Slices**: four PRs (010-a, 010-b, 010-c1, 010-c2); 010-c was split after its first attempt stopped. Each: tests → skeletons/config → confirm failure → implement → full suite → PR size check.

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

## Slice 010-c — Operation logs and end-to-end telemetry test (two PRs: 010-c1, 010-c2)

The first attempt (branch `010-observability-c`) stopped at T007: the Loki check never found the `requestId` line,
because no production code logged during a successful request (plan.md § Operation logs). Its `ObservabilityIT`
already measured 351 lines, so the missing operation logs go first in a slice of their own. That branch is not
continued: before 010-c1, keep its uncommitted `ObservabilityIT.java` aside (`git stash push -u`) and restore it on
the 010-c2 branch, unchanged unless the logs check needs it.

### Slice 010-c1 — Operation logs, TEC-27 (branch `010-observability-c1`)

**Estimated size**: ~200 lines.

- [ ] T007a Write `OperationLogIT` (plan.md § Tests). Skeleton of `OperationLog` with the four methods empty, called
  from the controllers. Run: the five operation cases **fail** (no `Operation completed` line); case (6) passes.
  Record.
- [ ] T007b Implement plan.md § Operation logs (helper and the `logback-spring.xml` attribute) until green. The
  console lines keep the ECS format (spec 001 tests unchanged).

### Slice 010-c2 — End-to-end telemetry test (branch `010-observability-c2`, after 010-c1 is merged)

**Estimated size**: ~350 lines.

- [ ] T007 Write `ObservabilityIT` (plan.md § Tests). It should pass with slices a, b and c1; if a signal does not
  arrive, **stop and report** with what the stack returned. In the PR, paste the Loki line found for `obs-it-1`,
  with its `trace_id` and the operation-log pairs.
- [ ] T008 Run `./mvnw verify` three times to confirm it is stable (polling with generous timeouts, no fixed sleeps
  except between polls).

---

## Definition of done (spec 010)

- [ ] Slices merged; `./mvnw verify` green.
- [ ] `docker compose up --build` shows both dashboards with data after some traffic; a log line in Loki links to its
  trace in Tempo.
