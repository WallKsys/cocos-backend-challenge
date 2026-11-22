# Tasks: API conventions (spec 003)

**Input**: `specs/003-api-conventions/` (spec.md, plan.md, contracts/openapi.yaml)

**Before starting**: specs 001 and 002 are merged. `export SPECIFY_FEATURE_DIRECTORY=specs/003-api-conventions`.

**Slices**: five planned slices, in order; 003-b and 003-c were each split in two after measuring their size, so
seven PRs. Each starts from an up-to-date `main`, follows tests → skeletons → confirm failure
→ implement → full suite, and ends with `scripts/check-pr-size.sh` within 400 lines.

---

## Slice 003-a — Domain building blocks (branch `003-api-conventions-a`)

**Estimated size**: ~300 lines.

- [x] T001 [P] Write unit tests `UserIdTest`, `MoneyTest`, `RequestFingerprintTest` (plan.md § Domain building
  blocks), including the SHA-256 test vector and the `Money` normalization/rounding cases (`"925.855"` → `925.86`,
  `"925.8"` equals `"925.80"`, `times` rounding HALF_UP).
- [x] T002 Create skeletons of `UserId`, `Money`, `RequestFingerprint`, the four enums and the five domain
  exceptions (plan.md § Domain exceptions). Run `./mvnw test -Dgroups=unit`: **tests fail**. Record it.
- [x] T003 Implement until green. Run `./mvnw verify` (ArchUnit must still pass: domain is pure Java).

**Checkpoint**: open PR `003-a`.

---

## Slice 003-b — Error handling (branch `003-api-conventions-b`)

**Estimated size**: ~380 lines. If it exceeds 400, split into 003-b1 (catalog, domain exceptions, Spring MVC
exceptions) and 003-b2 (validation errors, Jackson coercion, locale). Tests always ship in the same PR as the code
they verify.

- [x] T004 Create `src/test/java/com/cocos/challenge/conventions/ProbeControllers.java` with the endpoints needed
  by this slice (`/validate`, `/throw/{kind}`) — plan.md § Probe endpoints.
- [x] T005 Write `ErrorHandlingIT` covering spec US1 scenarios 1–11 (status, `application/problem+json`, every
  field of the Problem, exact `detail` texts, `errors` list, no internal message on 500, English with
  `Accept-Language: es`).
- [x] T006 Create skeletons of `ApiErrorCode`, `ApiException`, `ApiExceptionHandler`, `JacksonConfiguration`.
  Run `./mvnw verify`: **ErrorHandlingIT fails**. Record it.
- [x] T007 Implement plan.md § Error catalog, § Exception mapping, § Validation and § Jackson. Add the locale
  configuration of plan.md § Configuration.
- [x] T008 Run `./mvnw verify` until green.

**Checkpoint**: open PR `003-b`.

---

## Slice 003-c — split into 003-c1 and 003-c2

The first attempt at 003-c measured **496 counted lines with tests and skeletons alone**, before any
implementation (the estimate was ~380). It is split along the user stories, which share no test and no probe:

- **003-c1:** identity (US2) and the admin guard (US3).
- **003-c2:** the idempotency key (US4) and the correlation id (US6).

The tests and skeletons of the first attempt are on the local branch `003-api-conventions-c` (commits `0c0e5b6`
tests, `3d0fb84` skeletons; never pushed). **Reuse them**: bring each file into the new branch with
`git checkout <commit> -- <path>` instead of retyping it. For the two files shared by both halves
(`ProbeControllers`, `WebConfiguration`), keep only what the slice needs. Keep `003-api-conventions-c` until 003-c2
is merged; it is the source for both halves.

## Slice 003-c1 — Identity and admin guard (branch `003-api-conventions-c1`)

**Estimated size**: ~340 counted lines (tests and skeletons measured at ~270, plus the implementation).

- [x] T009 Extend `ProbeControllers` with `/user` and `InternalProbeController` only.
- [x] T010 [P] Write `IdentityAndAdminIT` (US2, US3 scenarios 2–3) and `InternalToolsDisabledIT` (US3 scenario 1),
  with the shared helper `ProblemResponses`.
- [x] T011 [P] Write `InternalToolsArchitectureTest` (plan.md § Identity and admin).
- [x] T012 Create skeletons of `RequesterId`, `RequesterIdArgumentResolver`, `AdminRoleInterceptor` and
  `WebConfiguration` (registering only what this slice needs). Run `./mvnw verify`: **the new tests fail**.
  Record it.
- [x] T013 Implement until green.

**Checkpoint**: open PR `003-c1`.

---

## Slice 003-c2 — Idempotency key and correlation id (branch `003-api-conventions-c2`)

**Estimated size**: ~310 counted lines (tests and skeletons measured at ~230, plus the implementation).

**Before starting**: 003-c1 is merged.

- [ ] T013a Extend `ProbeControllers` with `/idempotent`, `/replayed` and `/log`.
- [ ] T013b [P] Write `IdempotencyKeyIT` (US4) and `RequestIdIT` (US6), reusing `ProblemResponses` from 003-c1
  and restoring its `X-Request-Id` header assertion (US6 coverage: the assertion was intentionally left out
  of the helper in 003-c1, where `RequestIdFilter` does not exist yet).
- [ ] T013c Create skeletons of `IdempotencyKeyHeader`, `IdempotencyKeyArgumentResolver`, `IdempotentResponses`,
  `RequestIdFilter` and `ClockConfiguration`, and register the idempotency key resolver in `WebConfiguration`.
  Run `./mvnw verify`: **the new tests fail**. Record it.
- [ ] T013d Implement until green.

**Checkpoint**: open PR `003-c2`.

---

## Slice 003-d — Formats and business metrics (branch `003-api-conventions-d`)

**Estimated size**: ~330 lines.

- [ ] T014 [P] Write `ApiDecimalsTest`, `DecimalStringValidatorTest` and `FormatsIT` (US5; add `/format` to the
  probes).
- [ ] T015 [P] Write `MicrometerBusinessMetricsTest` with a `SimpleMeterRegistry` (US7).
- [ ] T015a [P] Add the `/required-param` probe (plan.md § Probe endpoints) and two NEW methods to `ErrorHandlingIT`
  for US1 scenario 12 (DEC-25): `count` missing and `count=abc`. Assert status, `application/problem+json`, code,
  exact `detail` and that `errors` is null. Do not change existing methods. **These tests pass at once**: slice
  003-b2 already implemented both mappings and this only adds their missing coverage. Say so in the PR instead of
  showing a red run.
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
