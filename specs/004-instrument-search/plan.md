# Implementation Plan: Instrument search

**Branch**: `004-instrument-search-<slice>` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

## Summary

`GET /api/v1/instruments` with general term, ticker and name filters, ranking, market data and cursor pagination.
Matching and ranking run in SQL over the normalized columns of spec 002 (trigram indexes). Validation rules and the
daily change calculation live in the domain. This spec also adds the shared BDD infrastructure that every later
business spec uses (scenario context, common steps, database reset).

## Technical Context

**Storage**: `instruments` (`ticker_normalized`, `name_normalized`, trigram indexes) and `marketdata` (latest row per
instrument) — see `specs/002-database/data-model.md`.

**Verified**: the SQL of § Persistence was run against PostgreSQL 18.7 with migrations V1–V5 and returns exactly the
results expected by every scenario of the feature file, including pagination with a cursor and literal `%` / `_`.

## Constitution Check

| Principle | How this spec complies |
|---|---|
| IV Traceability | `CU-02` added to the scope; every scenario has a Gherkin scenario and tests (§ Tests) |
| V Hexagonal | Rules in domain, query port in application, SQL in the persistence adapter, DTOs in REST |
| VI Explicit SQL | Hand-written SQL built from fixed fragments; values always as named parameters |
| VIII Money | `lastPrice` as `Money`, daily change as `Percentage`; strings in JSON |
| X PR size | Three slices |

## Project Structure

```text
src/main/java/com/cocos/challenge/
├── domain/model/
│   ├── InstrumentId.java              record InstrumentId(long value), value >= 1
│   ├── InstrumentType.java            enum ACCIONES, MONEDA
│   ├── Percentage.java                record Percentage(BigDecimal value), scale 2 HALF_UP
│   ├── MarketQuote.java               record MarketQuote(Money close, Money previousClose /* nullable */)
│   ├── InstrumentSummary.java         record (InstrumentId id, String ticker, String name, InstrumentType type, MarketQuote quote /* nullable */)
│   ├── SearchTerm.java                record SearchTerm(String text): trimmed, non-blank; isShort() = fewer than 3 characters
│   ├── SearchCursor.java              record SearchCursor(int rank, String ticker): encode() / decode(String)
│   ├── InstrumentSearchCriteria.java  record (SearchTerm q, SearchTerm ticker, SearchTerm name, int limit, SearchCursor cursor) — nullable parts
│   ├── RankedInstrument.java          record RankedInstrument(int rank, InstrumentSummary instrument)
│   └── InstrumentPage.java            record InstrumentPage(List<InstrumentSummary> items, SearchCursor nextCursor /* nullable */)
├── domain/exception/SearchTermTooShortException.java
├── application/port/in/SearchInstrumentsQuery.java
├── application/port/out/InstrumentSearchRepository.java
├── application/service/SearchInstrumentsService.java
└── infrastructure/
    ├── adapter/out/persistence/JdbcInstrumentSearchRepository.java
    ├── adapter/in/rest/instrument/InstrumentSearchController.java
    ├── adapter/in/rest/instrument/InstrumentResponse.java
    ├── adapter/in/rest/instrument/InstrumentPageResponse.java
    ├── adapter/in/rest/error/ApiExceptionHandler.java   (add one mapping, § Errors)
    └── config/InstrumentSearchConfiguration.java        (bean wiring)

src/main/resources/static/openapi/openapi.yaml           (copy of this spec's contract)

src/test/java/com/cocos/challenge/
├── domain/model/{PercentageTest, MarketQuoteTest, SearchTermTest, SearchCursorTest, InstrumentSearchCriteriaTest}.java
├── application/service/SearchInstrumentsServiceTest.java          (fake repository)
├── infrastructure/adapter/out/persistence/JdbcInstrumentSearchRepositoryIT.java
├── infrastructure/adapter/in/rest/instrument/InstrumentSearchControllerIT.java
├── support/SeedData.java                                          (§ BDD infrastructure)
└── bdd/
    ├── support/ScenarioContext.java
    ├── support/DatabaseHooks.java
    ├── steps/CommonResponseSteps.java
    └── steps/InstrumentSearchSteps.java
src/test/resources/traceability/scope.txt                          (append CU-02)
src/test/resources/features/cu-02-instrument-search.feature        (copy of specs/004-instrument-search/features/)
src/test/java/com/cocos/challenge/traceability/FeaturesMatchSpecsTest.java
```

## Domain rules

| Type | Rules |
|---|---|
| `Percentage` | Normalized to scale 2 HALF_UP in the canonical constructor. |
| `MarketQuote.dailyChange()` | `Optional<Percentage>`: empty when `previousClose` is null or zero; otherwise `(close − previousClose) / previousClose × 100`, computed with `MathContext.DECIMAL128` and then rounded HALF_UP to 2 decimals. PAMP: `(925.85 − 921.80) / 921.80 × 100 = 0.4393…` → `0.44`. |
| `SearchTerm.of(String raw)` | Returns `null` when `raw` is null or blank. Otherwise trims. Rejects more than 100 characters with `InvalidRequestException("Request validation failed: '<param>' must have at most 100 characters.")` (the factory receives the parameter name). `isShort()` ⇔ fewer than 3 characters (`codePointCount`). |
| `InstrumentSearchCriteria.of(String q, String ticker, String name, Integer limit, String cursor)` | Builds the three `SearchTerm`s. If `name` is short → `SearchTermTooShortException`. `limit`: null → 20; outside 1–100 → `InvalidRequestException("Request validation failed: 'limit' must be between 1 and 100.")`. `cursor`: null/blank → none; else `SearchCursor.decode`. |
| `SearchCursor` | `encode()` = Base64 URL-safe without padding of `"v1:" + rank + ":" + ticker` (UTF-8). `decode` reverses it; anything that is not valid Base64, does not start with `v1:`, has a non-integer rank outside 0–2, or an empty ticker → `InvalidRequestException("Request validation failed: 'cursor' is invalid.")`. |
| `SearchTermTooShortException` | Message: `The name filter requires at least 3 characters.` |

## Application

```java
public interface SearchInstrumentsQuery {
    InstrumentPage search(InstrumentSearchCriteria criteria);
}

public interface InstrumentSearchRepository {
    /** Returns at most maxResults instruments matching the criteria, after the criteria's cursor, ordered by (rank, ticker). */
    List<RankedInstrument> search(InstrumentSearchCriteria criteria, int maxResults);
}
```

`SearchInstrumentsService.search` (`@Transactional(readOnly = true)`): call the repository with `limit + 1`; return
the first `limit` items; if the repository returned more than `limit`, `nextCursor` = `SearchCursor(rank, ticker)` of
the **last returned** item; otherwise null.

## Persistence

`JdbcInstrumentSearchRepository` builds the SQL from **fixed fragments** chosen by which criteria are present;
values always travel as named parameters. LIKE values are escaped in Java before binding:
`text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")`.

| Fragment | When | SQL |
|---|---|---|
| base filter | always | `i.type <> 'MONEDA'` |
| `q` short | `q` present, short | `i.ticker_normalized = lower(immutable_unaccent(:q))` |
| `q` long | `q` present, not short | `(i.ticker_normalized LIKE '%' \|\| lower(immutable_unaccent(:qLike)) \|\| '%' ESCAPE '\' OR i.name_normalized LIKE '%' \|\| lower(immutable_unaccent(:qLike)) \|\| '%' ESCAPE '\')` |
| `ticker` short | present, short | `i.ticker_normalized = lower(immutable_unaccent(:ticker))` |
| `ticker` long | present, not short | `i.ticker_normalized LIKE '%' \|\| lower(immutable_unaccent(:tickerLike)) \|\| '%' ESCAPE '\'` |
| `name` | present (never short here) | `i.name_normalized LIKE '%' \|\| lower(immutable_unaccent(:nameLike)) \|\| '%' ESCAPE '\'` |
| rank | `q` present | `CASE WHEN i.ticker_normalized = lower(immutable_unaccent(:q)) THEN 0 WHEN i.ticker_normalized LIKE lower(immutable_unaccent(:qLike)) \|\| '%' ESCAPE '\' THEN 1 ELSE 2 END` |
| rank | `q` absent | `0` |
| cursor | cursor present | `WHERE (c.rank, c.ticker COLLATE "C") > (:cursorRank, :cursorTicker COLLATE "C")` |

Full statement (fragments in angle brackets):

```sql
SELECT c.id, c.ticker, c.name, c.type, c.rank, md.close, md.previousclose
FROM (
    SELECT c.*
    FROM (
        SELECT i.id, i.ticker, i.name, i.type, <rank> AS rank
        FROM instruments i
        WHERE <base filter> [AND <q fragment>] [AND <ticker fragment>] [AND <name fragment>]
    ) c
    [<cursor fragment>]
    ORDER BY c.rank, c.ticker COLLATE "C"
    LIMIT :maxResults
) c
LEFT JOIN LATERAL (
    SELECT m.close, m.previousclose
    FROM marketdata m
    WHERE m.instrumentid = c.id
    ORDER BY m.date DESC
    LIMIT 1
) md ON TRUE
ORDER BY c.rank, c.ticker COLLATE "C"
```

In a Java text block, the `ESCAPE '\'` must be written as `ESCAPE '\\'`.

## REST

`InstrumentSearchController` — `GET /api/v1/instruments` with `@RequestParam(required = false)` `q`, `ticker`,
`name`, `cursor` (String) and `limit` (`Integer`). It builds `InstrumentSearchCriteria.of(...)`, calls
`SearchInstrumentsQuery`, and maps to `InstrumentPageResponse(List<InstrumentResponse> items, String nextCursor)`
with `InstrumentResponse(long id, String ticker, String name, String type, String lastPrice, String dailyChangePct)`
(money and percentage via `ApiDecimals.format`; `null` when absent). A non-integer `limit` produces Spring's
`MethodArgumentTypeMismatchException` → `VALIDATION_ERROR` (spec 003). No `@RequesterId`.

## Errors

Add to `ApiExceptionHandler` (spec 003): `SearchTermTooShortException` → `SEARCH_TERM_TOO_SHORT` (400), detail = the
exception message. This is the only change to spec 003 code.

## BDD infrastructure (shared by all later specs)

- `support/SeedData.reset(JdbcClient)` (test sources): restores the database to the seed state in one transaction:
  first `SET LOCAL TIME ZONE 'UTC'` (**required**: the seed timestamps have no zone, and the PostgreSQL JDBC driver
  sets the session time zone to the JVM default, which on a developer machine may be
  `America/Argentina/Buenos_Aires` — without this line every seed timestamp would be shifted by 3 hours), then
  `TRUNCATE orders, marketdata, instruments, users RESTART IDENTITY CASCADE`, then executes, in file order, every
  statement that starts with `INSERT` read from the classpath resource
  `db/migration/V1__original_schema_and_seed.sql` (split the file on `;`). No copy of the seed data is kept anywhere
  else.
- `bdd/support/DatabaseHooks`: Cucumber `@Before` hook that calls `SeedData.reset` before **every** scenario, so
  scenarios never depend on each other.
- `bdd/support/ScenarioContext`: `@ScenarioScope` Spring bean holding the last HTTP response (status, headers,
  body as JSON) for the steps of one scenario.
- `bdd/steps/CommonResponseSteps`: generic steps shared by all features: `the response status is {int}`,
  `the error code is {string}`, `the error detail is {string}`. **Move** the step `the response status is {int}` from
  `HealthSteps` (spec 001) here, using `ScenarioContext`; Cucumber forbids duplicate step definitions. This is a
  refactor of glue code: no `.feature` file changes and the smoke scenario must still pass.
- `bdd/steps/InstrumentSearchSteps`: the search steps of the feature file. `Given an instrument with ticker {string}
  and name {string} exists` inserts an `ACCIONES` instrument with `JdbcClient` (removed by the next reset).

## Feature files are copied, never written

Gherkin files are written in the spec folder (`specs/NNN-*/features/`) and copied **byte-identical** into
`src/test/resources/features/` when their spec is implemented — not before, because Cucumber would run them without
step definitions and break the build of earlier specs.

`traceability/FeaturesMatchSpecsTest` (unit, created in this spec, used by every later spec): for every `.feature`
file in `src/test/resources/features/` that contains a `@CU-` tag, a file with the same name must exist in some
`specs/*/features/` folder and both must be byte-identical. `000-smoke.feature` (no CU tag) is exempt. This makes
editing a scenario to make it pass impossible without the build failing.

## Tests

Every test carries `@Tag("<CU id>")` plus `@Tag("unit")` or `@Tag("integration")`. Matching, ranking, accents,
exclusion of cash and pagination happen in SQL, so their guarantee is an **integration** test of the repository.

| CU | Unit test | Integration test |
|---|---|---|
| CU-02.01, .02, .03, .04, .05, .06, .08, .09, .14, .15 | — | `JdbcInstrumentSearchRepositoryIT` (one test per CU, exact tickers in order) |
| CU-02.05b | `InstrumentSearchCriteriaTest` (`q` of 1–2 chars is short) | `JdbcInstrumentSearchRepositoryIT` (empty result) |
| CU-02.05c | — | `JdbcInstrumentSearchRepositoryIT` (inserts TS and TSLA inside a `@Transactional` test) |
| CU-02.07 | — | `JdbcInstrumentSearchRepositoryIT` (no criteria: first 20 by ticker) |
| CU-02.10 | `MarketQuoteTest` / mapping of absent quote | `JdbcInstrumentSearchRepositoryIT` (PGR without quote) |
| CU-02.11 | `MarketQuoteTest` (PAMP daily change 0.44) | `InstrumentSearchControllerIT` (JSON fields and string formats) |
| CU-02.12 | `SearchInstrumentsServiceTest` (next cursor = last returned item; null on last page) | `JdbcInstrumentSearchRepositoryIT` (pages 20/20/20/5, no repeats) |
| CU-02.13 | `InstrumentSearchCriteriaTest` (`SearchTermTooShortException` for "b" and "ba") | `InstrumentSearchControllerIT` (400 `SEARCH_TERM_TOO_SHORT`) |

Also (no CU tag): `PercentageTest`, `SearchTermTest`, `SearchCursorTest`, and `InstrumentSearchControllerIT` cases for
the edge cases of spec.md (literal `%`, blank parameters, length > 100, invalid `limit`, invalid cursor).

## Contract

Copy `specs/004-instrument-search/contracts/openapi.yaml` over `src/main/resources/static/openapi/openapi.yaml`
(byte-identical). `PublishedContractMatchesRoutesIT` (spec 003) must now find exactly one route:
`GET /api/v1/instruments`.

## HTTP examples

`http/004-instrument-search.http`: searches for `banco`, `pamp`, `PGR`, no criteria, the next page using the returned
cursor, and `name=ba` (error).
