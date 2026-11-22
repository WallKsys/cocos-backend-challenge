# Tasks: Database (spec 002)

**Input**: `specs/002-database/` (spec.md, plan.md, data-model.md, migrations/)

**Before starting**: spec 001 is merged. `export SPECIFY_FEATURE_DIRECTORY=specs/002-database`.

**Slices**: two PRs. Each starts from an up-to-date `main`, ends with `./mvnw verify` green and
`scripts/check-pr-size.sh` within 400 lines (V1 is excluded from the count).

---

## Slice 002-a — Original data and integrity constraints (branch `002-database-a`)

**Estimated size**: ~250 counted lines (V2 ~50, tests ~200).

### Tests first

- [x] T001 [P] Create `src/test/java/com/cocos/challenge/database/OriginalScriptIsUnchangedTest.java`
  (plan.md § Tests).
- [x] T002 [P] Create `database/SeedDataIT.java`, asserting migration versions **1 and 2** for this slice
  (slice 002-b extends the assertion to 1–5).
- [x] T003 [P] Create `database/LedgerReferenceQueriesIT.java` with the five reference queries of
  data-model.md § Ledger formulas and the expected seed values.
- [x] T004 [P] Create `database/SchemaConstraintsIT.java` with rows 1, 2, 3, 4 and 14 of plan.md § Constraint
  checks (the rows that depend on V2 only).
- [x] T005 Run `./mvnw verify`. **Expected: these tests fail** (no migrations, tables do not exist). Record it.

### Implementation

- [x] T006 Copy V1 and V2 exactly as described in plan.md § How to copy the migrations (only V1 and V2 in this
  slice). Delete `src/main/resources/db/migration/.gitkeep`.
- [x] T007 Run `./mvnw verify` until green. If a migration fails, **stop and report**: do not edit it.
- [x] T008 Run `docker compose up --build` and paste the `flyway_schema_history` query output in the PR.

**Checkpoint 002-a**: open PR `002-a`.

---

## Slice 002-b — New columns, search and indexes (branch `002-database-b`)

**Estimated size**: ~320 counted lines (V3–V5 ~80, tests ~240).

### Tests first

- [x] T009 Extend `SeedDataIT` to assert migration versions 1–5.
- [x] T010 [P] Add rows 5 to 13 of plan.md § Constraint checks to `SchemaConstraintsIT`.
- [x] T011 [P] Create `database/TimestampsAndSearchColumnsIT.java` (plan.md § Tests). Note: the UTC timestamp
  assertion depends on V2 (already merged) and passes immediately; the search-column assertions fail until V4.
- [x] T012 [P] Create `database/IndexesIT.java` with every index of data-model.md § Indexes.
- [x] T013 Run `./mvnw verify`. **Expected: the new tests fail** (columns, constraints and indexes from V3–V5 do
  not exist). Record it.

### Implementation

- [x] T014 Copy V3, V4 and V5 byte-identical (plan.md § How to copy the migrations).
- [x] T015 Run `./mvnw verify` until green. If a migration fails, stop and report.

**Checkpoint 002-b**: open PR `002-b`.

---

## Definition of done (spec 002)

- [ ] Slices 002-a and 002-b merged into `main`.
- [ ] `./mvnw verify` green; `docker compose up --build` starts with five applied migrations.
- [ ] No migration was edited after being copied.
