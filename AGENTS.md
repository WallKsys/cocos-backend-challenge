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

- Use the Spec Kit commands in `.opencode/commands/`. Specs, plans and tasks are **already written**:
  your job is `/speckit.implement` on the current spec, task by task.
- Follow the test-first cycle of Constitution Principle III **for every task**:
  write tests → minimal skeletons → confirm the tests fail → implement → run the full suite → next task.
- One task at a time. Check it off in `tasks.md` only when the full suite is green.
- Commit after each completed task: `[NNN][T0xx] short description (CU-xx.yy, DEC-xx)`.
- One PR per **slice** of `tasks.md` (`NNN-a`, `NNN-b`, ...), on a branch `NNN-short-name-<letter>` created from
  an updated `main`. **Max 400 counted lines per PR** (Constitution Principle X): run
  `scripts/check-pr-size.sh` before opening the PR, paste its output, and fill the PR template. If a slice
  would exceed the limit, split it; only if impossible, add a `## Size justification` section.

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

- Never modify, disable, skip or weaken an existing test or a `.feature` file to make the build pass.
- Never use JPA, Hibernate, Spring Data JPA or any ORM. All SQL is hand-written with `JdbcClient` in
  `infrastructure.adapter.out.persistence`.
- Never use `double` or `float` for money.
- Never put Spring annotations in `domain`, or any Spring annotation other than `@Transactional` in
  `application`.
- Never invent endpoints, fields, error codes or messages that are not in the spec and its contract.
- Never write API messages in a language other than English.
- Never edit a Flyway migration that was already committed.
- Never add or upgrade dependencies that the plan does not list.

## When to stop and report instead of continuing

- A test or scenario looks wrong.
- The spec is ambiguous or contradicts the constitution.
- A dependency, class or API named in the plan does not exist or behaves differently.
- You cannot complete a task without breaking a rule above.

Explain what you found and what you propose, and wait for instructions.
