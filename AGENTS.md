# AGENTS.md

Instructions for any AI coding agent working in this repository.

## What this project is

A broker API (portfolio, instrument search, buy/sell orders, deposits, withdrawals) built in Java 25 and
Spring Boot 4.1, following spec-driven development with GitHub Spec Kit, BDD (Cucumber) and architecture rules
enforced by ArchUnit.

## Read this first (mandatory, in this order)

1. `.specify/memory/constitution.md` — the non-negotiable rules. **It wins over everything else.**
2. The spec you are working on: `specs/NNN-*/spec.md`, `plan.md`, `data-model.md`, `contracts/`, `tasks.md`.
3. The Gherkin scenarios of that spec in `src/test/resources/features/`.
4. Only when a spec cites them: `docs/casos-de-uso.md`, `docs/analisis-funcional.md`, `docs/rfc.md`
   (written in Spanish; IDs such as `CU-03.04`, `DEC-01`, `TEC-16` are the link between documents).

## How to work

- Specs, plans and tasks are **already written**: your job is to implement the current spec, task by task, as the
  Slice protocol below describes. The Spec Kit commands are in `.opencode/commands/` (for example
  `speckit.implement.md`); if your agent does not load them as commands, read them as plain files.
- Select the spec you are implementing before running Spec Kit commands, for example
  `export SPECIFY_FEATURE_DIRECTORY=specs/001-skeleton-guardrails`. Implement specs strictly in numeric order.
- Follow the test-first cycle of Constitution Principle III **for every task**:
  write tests → minimal skeletons → confirm the tests fail → implement → run the full suite → next task.
- One task at a time. Check it off in `tasks.md` only when the full suite is green.
- Commit after each completed task: `[NNN][T0xx] short description (CU-xx.yy, DEC-xx)`.
- One PR per **slice** of `tasks.md` (`NNN-a`, `NNN-b`, ...), on a branch `NNN-short-name-<letter>` created from
  an updated `main`. **Max 400 counted lines per PR** (Constitution Principle X): run
  `scripts/check-pr-size.sh` before opening the PR, paste its output, and fill the PR template. If a slice
  would exceed the limit, split it; only if impossible, add a `## Size justification` section.

## Slice protocol (no other prompt is needed)

The owner only says what to do, in one line: "Implement the next slice", "Implement slice 005-b", "The spec was fixed
in PR #N, continue" or "Address the review of PR #N". Everything else is in this section and in the spec.

### 1. Pick the slice

- Run `git checkout main && git pull`.
- **Next slice:** in numeric order of specs, then of slices (`004-b`, `004-c`, `005-a`, ...), the first slice whose
  tasks are not all checked in its `tasks.md` on `main`. A slice with a predefined split (`NNN-x1` / `NNN-x2` in its
  `tasks.md`) whose first half is already merged continues with the second half.
- Run `gh pr list --state open`. If a PR of that slice, or of an earlier one, is still open, **stop**: say that it is
  waiting for review or merge. Never start a slice on top of one that is not merged.
- `export SPECIFY_FEATURE_DIRECTORY=specs/NNN-...` and read that spec's `spec.md`, `plan.md`, `data-model.md` (if
  present), `contracts/` and the slice section of `tasks.md`, **including its notes**, before writing anything.

### 2. Implement

1. Create the branch named in `tasks.md` (`NNN-short-name-<letter>`) from the updated `main`.
2. **Commit 1, tests:** new test classes, or NEW methods in existing classes. Never change an existing test method,
   unless the slice's `tasks.md` prescribes that change for that file (Constitution III): then make exactly that
   change in this commit and explain it in the PR.
3. **Commit 2, skeletons:** they compile and keep the rest of the application working (for example, a filter
   skeleton passes the request through). Run `./mvnw verify` and confirm that the new tests fail **for the expected
   reason**; keep that output for the PR. A task that `tasks.md` says "passes at once" needs no red run: say so in
   the PR instead.
4. **Measure** with `scripts/check-pr-size.sh`. If the slice will exceed 400 counted lines and `tasks.md` defines a
   split, implement only the first half now, without stopping. If no split is defined, stop and report.
5. **Commit 3, implementation:** run `./mvnw verify` until green. Then check off the tasks of the slice. In a split
   slice, each half checks off the tasks it completes, and a task shared by both halves is checked in the second.
6. If a test must change after the red run, explain why in the PR under "Test adjustments after the red run".

### 3. Before opening the PR

- Read `.github/claude/pr-review.md` and check the PR against its table of checks yourself; fix anything it would
  flag.
- Fill every section of the PR template with real outputs: the red run, `scripts/check-pr-size.sh` and the final
  `./mvnw verify` summary.
- Open the PR and turn on auto-merge: `gh pr merge <number> --auto --squash`. It merges by itself once CI, the
  automatic review and, for slices marked `**Manual review**: required`, the owner's manual review have passed.
  Then stop. Do not start the next slice until the owner asks for it.

### 4. After a stop and report

When the owner says that the spec was fixed in PR #N: run `git fetch && git merge origin/main` on your branch, read
the cited sections again and continue the slice from where you stopped. Branch history that was never pushed may be
rebuilt to keep the order tests → skeletons → implementation.

### 5. Addressing a review

When asked to address the review of PR #N: read every review and comment
(`gh pr view N --comments` and `gh api repos/{owner}/{repo}/pulls/N/reviews`). Apply the requested changes with the
same order: new tests first, with their red output, then the fix in a separate commit. Push to the same branch and
update the PR description with the new red output and the new size check.

## Commands

```bash
./mvnw verify                 # full suite: unit, integration, BDD, ArchUnit, traceability, contracts
./mvnw test -Dgroups=unit     # unit tests only (fast)
./mvnw verify -Pvolume        # volume tests (spec 009 only; slow)
scripts/check-pr-size.sh      # PR size against main (limit 400 counted lines)
docker compose up             # run the application locally with demo data
```

Integration tests need Docker running (Testcontainers).

## Never do this

- Never modify, disable, skip or weaken an existing test or a `.feature` file to make the build pass. The only
  exception is a change that the slice's `tasks.md` prescribes for a named test file (Constitution III).
- Never edit anything under `specs/`. The only allowed change is checking off the tasks of your slice in its
  `tasks.md` (`- [ ]` → `- [x]`): no notes, no rewording, no other checkboxes. Anything you want to explain goes in
  the PR description; if the spec itself looks wrong, stop and report.
- Never use JPA, Hibernate, Spring Data JPA or any ORM. All SQL is hand-written with `JdbcClient` in
  `infrastructure.adapter.out.persistence`.
- Never use `double` or `float` for money.
- Never put Spring annotations in `domain`, or any Spring annotation other than `@Transactional` in
  `application`.
- Never invent endpoints, fields, error codes or messages that are not in the spec and its contract.
- Never write API messages in a language other than English.
- Never edit a Flyway migration that was already committed.
- Never add or upgrade dependencies that the plan does not list.
- Never edit `docs/` or `README.md`: the documentation for people is written by the spec author (TEC-05), who also
  updates the rules index when a slice delivers a planned barrier.
- Never edit `.github/claude/`, `.github/workflows/` (except creating `ci.yml` in slice 001-a) or
  `scripts/check-pr-size.sh`: they are the review and verification guardrails.
- Never merge a PR yourself (only `gh pr merge --auto`), never add or remove PR labels (in particular
  `manual-review-approved`, which only the owner's reviewer adds), and never change repository settings or branch
  protection.

## When to stop and report instead of continuing

- A test or scenario looks wrong.
- The spec is ambiguous or contradicts the constitution.
- A dependency, class or API named in the plan does not exist or behaves differently.
- You cannot complete a task without breaking a rule above.

Explain what you found and what you propose, and wait for instructions.
