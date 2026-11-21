# Tasks: API conventions (spec 003)

**Input**: `specs/003-api-conventions/` (spec.md, plan.md, contracts/openapi.yaml)

**Before starting**: specs 001 and 002 are merged. `export SPECIFY_FEATURE_DIRECTORY=specs/003-api-conventions`.

**Slices**: five PRs, in order. Each starts from an up-to-date `main`, follows tests → skeletons → confirm failure
→ implement → full suite, and ends with `scripts/check-pr-size.sh` within 400 lines.

---

## Slice 003-a — Domain building blocks (branch `003-api-conventions-a`)

**Estimated size**: ~300 lines.

- [ ] T001 [P] Write unit tests `UserIdTest`, `MoneyTest`, `RequestFingerprintTest` (plan.md § Domain building
  blocks), including the SHA-256 test vector and the `Money` normalization/rounding cases (`"925.855"` → `925.86`,
  `"925.8"` equals `"925.80"`, `times` rounding HALF_UP).
- [ ] T002 Create skeletons of `UserId`, `Money`, `RequestFingerprint`, the four enums and the five domain
  exceptions (plan.md § Domain exceptions). Run `./mvnw test -Dgroups=unit`: **tests fail**. Record it.
- [ ] T003 Implement until green. Run `./mvnw verify` (ArchUnit must still pass: domain is pure Java).

**Checkpoint**: open PR `003-a`.

---

## Slice 003-b — Error handling (branch `003-api-conventions-b`)

**Estimated size**: ~380 lines. If it exceeds 400, split into 003-b1 (catalog, domain exceptions, Spring MVC
exceptions) and 003-b2 (validation errors, Jackson coercion, locale). Tests always ship in the same PR as the code
they verify.

- [ ] T004 Create `src/test/java/com/cocos/challenge/conventions/ProbeControllers.java` with the endpoints needed
  by this slice (`/validate`, `/throw/{kind}`) — plan.md § Probe endpoints.
- [ ] T005 Write `ErrorHandlingIT` covering spec US1 scenarios 1–11 (status, `application/problem+json`, every
  field of the Problem, exact `detail` texts, `errors` list, no internal message on 500, English with
  `Accept-Language: es`).
- [ ] T006 Create skeletons of `ApiErrorCode`, `ApiException`, `ApiExceptionHandler`, `JacksonConfiguration`.
  Run `./mvnw verify`: **ErrorHandlingIT fails**. Record it.
- [ ] T007 Implement plan.md § Error catalog, § Exception mapping, § Validation and § Jackson. Add the locale
  configuration of plan.md § Configuration.
- [ ] T008 Run `./mvnw verify` until green.

**Checkpoint**: open PR `003-b`.

---

## Slice 003-c — Identity, admin guard, idempotency key, correlation id (branch `003-api-conventions-c`)

**Estimated size**: ~380 lines.

- [ ] T009 Extend `ProbeControllers` with `/user`, `/idempotent`, `/replayed`, `/log` and `InternalProbeController`.
- [ ] T010 [P] Write `IdentityAndAdminIT` (US2, US3 scenarios 2–3), `InternalToolsDisabledIT` (US3 scenario 1),
  `IdempotencyKeyIT` (US4), `RequestIdIT` (US6).
- [ ] T011 [P] Write `InternalToolsArchitectureTest` (plan.md § Identity and admin).
- [ ] T012 Create skeletons of `RequesterId`, `RequesterIdArgumentResolver`, `IdempotencyKeyHeader`,
  `IdempotencyKeyArgumentResolver`, `IdempotentResponses`, `RequestIdFilter`, `AdminRoleInterceptor`,
  `WebConfiguration`, `ClockConfiguration`. Run `./mvnw verify`: **the new tests fail**. Record it.
- [ ] T013 Implement until green.

**Checkpoint**: open PR `003-c`.

---

## Slice 003-d — Formats and business metrics (branch `003-api-conventions-d`)

**Estimated size**: ~300 lines.

- [ ] T014 [P] Write `ApiDecimalsTest`, `DecimalStringValidatorTest` and `FormatsIT` (US5; add `/format` to the
  probes).
- [ ] T015 [P] Write `MicrometerBusinessMetricsTest` with a `SimpleMeterRegistry` (US7).
- [ ] T016 Create skeletons of `ApiDecimals`, `DecimalString`, `DecimalStringValidator`, `BusinessMetrics`,
  `MicrometerBusinessMetrics`, `MetricsConfiguration`. Run tests: **they fail**. Record it.
- [ ] T017 Implement plan.md § Formats and § Business metrics until green.

**Checkpoint**: open PR `003-d`.

---

## Slice 003-e — Published contract and Swagger UI (branch `003-api-conventions-e`)

**Estimated size**: ~150 counted lines (the copied `openapi.yaml` is excluded from the count).

- [ ] T018 Write `PublishedContractIsLatestSpecContractTest`, `PublishedContractMatchesRoutesIT`, and an IT that
  checks `GET /openapi/openapi.yaml` (200, YAML containing `openapi: 3.1.0`) and `GET /swagger-ui/index.html` (200).
  Run: **they fail**. Record it.
- [ ] T019 Add the springdoc dependency (plan.md § Configuration), the `springdoc.swagger-ui.url` property, and copy
  the contract with `cp specs/003-api-conventions/contracts/openapi.yaml src/main/resources/static/openapi/openapi.yaml`.
- [ ] T020 Run `./mvnw verify` until green. Open `http://localhost:8080/swagger-ui/index.html` with
  `docker compose up --build` and paste a screenshot or the page title in the PR.

**Checkpoint**: open PR `003-e`.

---

## Definition of done (spec 003)

- [ ] Slices 003-a to 003-e merged into `main`.
- [ ] `./mvnw verify` green; ArchUnit (both files) green.
- [ ] Every error response of the probes matches plan.md § Error catalog exactly.
