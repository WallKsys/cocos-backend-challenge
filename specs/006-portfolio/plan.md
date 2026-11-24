# Implementation Plan: Portfolio

**Branch**: `006-portfolio-<slice>` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

## Summary

`GET /api/v1/portfolio` (user) and `GET /api/internal/users/{userId}/portfolio` (administrator, demo only). The
persistence adapters run four queries (account, cash, open positions, fills of the open positions) plus one batch
query for prices; pure domain code computes average costs, values, returns and warnings.

## Verified facts

- The queries of § Persistence were run against PostgreSQL 18.7 (migrations V1–V6): they return BMA −10, METR 500,
  PAMP 40 with 0 reserved, the fills in execution order, and the latest quotes.
- A simulation of § Portfolio rules over the seed reproduces every number of the feature file: total 889756.00;
  BMA −15028.00 / 1540.00 / −2.42 / −1.15; METR 114750.00 / 250.00 / −8.20 / −1.08; PAMP 37034.00 / 930.00 / −0.45 /
  0.44; CU-01.12 average cost 929.17 (moving average); CU-01.14 average cost 200.00 by execution time versus 133.33
  if processed by insertion order.

## Constitution Check

| Principle | How this spec complies |
|---|---|
| I Ledger | Everything derived from `orders`; total includes reserved cash (DEC-01.1) |
| V Hexagonal | Calculations in `domain`; query use case in `application`; SQL in persistence |
| VI Explicit SQL | Aggregations in SQL; a fixed number of queries (FR-003) |
| VIII Money | `Money`/`Percentage`; returns computed from the unrounded average cost |
| X PR size | Three slices |

## Project Structure

```text
src/main/java/com/cocos/challenge/
├── domain/model/
│   ├── Fill.java                 record Fill(OrderSide side, long quantity, Money price)   (BUY/SELL only)
│   ├── PortfolioWarning.java     record PortfolioWarning(PortfolioWarningCode code, String message)
│   ├── PortfolioWarningCode.java enum NEGATIVE_POSITION, MISSING_MARKET_DATA, TOTAL_EXCLUDES_UNPRICED, NEGATIVE_CASH
│   ├── Position.java             § Portfolio rules
│   └── Portfolio.java            § Portfolio rules
├── domain/service/AverageCostCalculator.java
├── application/port/in/GetPortfolioQuery.java          Portfolio get(UserId userId)
├── application/port/out/
│   ├── AccountReader.java        Optional<Account> find(UserId)            (no lock)
│   ├── PortfolioReader.java      List<OpenPosition> openPositions(UserId); Map<InstrumentId, List<Fill>> fills(UserId, Set<InstrumentId>)
│   ├── OpenPosition.java         record (InstrumentId id, String ticker, String name, long quantity, long reservedQuantity)
│   └── MarketDataRepository.java add Map<InstrumentId, MarketQuote> latestQuotes(Set<InstrumentId>)
├── application/service/GetPortfolioService.java        @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
└── infrastructure/
    ├── adapter/out/persistence/{JdbcAccountReader, JdbcPortfolioReader}.java (+ latestQuotes in JdbcMarketDataRepository)
    ├── adapter/in/rest/portfolio/{PortfolioController, PortfolioResponse, PositionResponse, CashResponse, WarningResponse}.java
    ├── adapter/in/rest/internal/InternalPortfolioController.java
    └── config/PortfolioConfiguration.java
```

## Portfolio rules

**Average cost** — `AverageCostCalculator.averageCost(List<Fill> fillsInExecutionOrder)` → `Optional<BigDecimal>`
(unrounded, `MathContext.DECIMAL128`). Start with quantity 0 and no average. For each fill:
- BUY with current quantity > 0 and an average: `average = (quantity × average + q × price) / (quantity + q)`.
- BUY with current quantity ≤ 0 or no average: `average = price` (restart).
- SELL: `quantity −= q`; the average does not change; if the quantity becomes **exactly 0**, there is no average.

**Position** — built from `OpenPosition`, its optional `MarketQuote` and its optional average cost:

| Field | Rule |
|---|---|
| `lastPrice` | quote close; null without quote |
| `marketValue` | `lastPrice × quantity` (`Money.times`); null without quote |
| `averageCost` | average rounded HALF_UP to 2 decimals; null when absent |
| `totalReturnPct` | `(close − average) / average × 100` with the **unrounded** average, rounded HALF_UP to 2; null without quote, without average, or average = 0 |
| `dailyChangePct` | `MarketQuote.dailyChange()` (spec 004) |
| `warnings` | `NEGATIVE_POSITION` if quantity < 0; `MISSING_MARKET_DATA` if no quote |

**Portfolio**:

| Field | Rule |
|---|---|
| `cash` | `CashBalance` (spec 005): total, reserved, available = total − reserved |
| `totalValue` | `cash.total + Σ marketValue` of positions **with** a quote |
| `positions` | quantity ≠ 0, ordered by ticker (binary order) |
| `warnings` | `TOTAL_EXCLUDES_UNPRICED` if any position has no quote (tickers in position order, joined by `, `); `NEGATIVE_CASH` if `cash.total` < 0 |

**Warning messages** (English, exact):

| Code | Message |
|---|---|
| `NEGATIVE_POSITION` | `Inconsistent position: the quantity of <TICKER> is negative (<quantity>); the recorded orders sell more shares than were bought.` |
| `MISSING_MARKET_DATA` | `No market data is available for <TICKER>; its price, value and returns are unknown.` |
| `TOTAL_EXCLUDES_UNPRICED` | `The total value excludes positions without market data: <TICKER>[, <TICKER>...].` |
| `NEGATIVE_CASH` | `Inconsistent account: the cash balance is negative (ARS <total>).` |

## Application

`GetPortfolioService.get(UserId)` — `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`, so
all reads see one snapshot. `readOnly` alone is not enough: under PostgreSQL's default READ COMMITTED every statement
takes its own snapshot, and an order committed between steps 2 and 3 would mix the cash from before it with the
positions from after it. A read-only REPEATABLE READ transaction never fails with a serialization error, so no retry
is needed. Steps:
1. `accountReader.find(userId)` or `NotFoundException("User <id> was not found.")`.
2. `cash = ledgerReader.cashBalance(userId)` (spec 005).
3. `open = portfolioReader.openPositions(userId)`; `ids` = their instrument ids.
4. If `ids` is not empty: `fills = portfolioReader.fills(userId, ids)`, `quotes = marketData.latestQuotes(ids)`.
5. Build `Position`s and the `Portfolio`.

## Persistence

Every SQL statement of this section is a **package-private `static final String` constant** of its adapter (named
after the query, for example `CASH_BALANCE_SQL`). The plan guard of spec 009 (`PlanGuardIT`, same package) runs
`EXPLAIN` on these constants, so they must be the exact statements the adapter executes.

`JdbcAccountReader.find`: `SELECT id, accountnumber FROM users WHERE id = :userId` (no lock).

`JdbcPortfolioReader.openPositions`:

```sql
SELECT o.instrumentid, i.ticker, i.name,
       SUM(CASE WHEN o.status = 'FILLED' AND o.side = 'BUY'  THEN o.size
                WHEN o.status = 'FILLED' AND o.side = 'SELL' THEN -o.size ELSE 0 END) AS quantity,
       SUM(CASE WHEN o.status = 'NEW' AND o.side = 'SELL' THEN o.size ELSE 0 END) AS reserved
FROM orders o
JOIN instruments i ON i.id = o.instrumentid
WHERE o.userid = :userId AND o.status IN ('FILLED', 'NEW') AND o.side IN ('BUY', 'SELL')
GROUP BY o.instrumentid, i.ticker, i.name
HAVING SUM(CASE WHEN o.status = 'FILLED' AND o.side = 'BUY'  THEN o.size
                WHEN o.status = 'FILLED' AND o.side = 'SELL' THEN -o.size ELSE 0 END) <> 0
ORDER BY i.ticker COLLATE "C"
```

`JdbcPortfolioReader.fills` (collection parameter expanded by `JdbcClient`):

```sql
SELECT instrumentid, side, size, price
FROM orders
WHERE userid = :userId AND status = 'FILLED' AND side IN ('BUY', 'SELL') AND instrumentid IN (:instrumentIds)
ORDER BY instrumentid, datetime, id
```

`JdbcMarketDataRepository.latestQuotes`:

```sql
SELECT DISTINCT ON (instrumentid) instrumentid, close, previousclose
FROM marketdata
WHERE instrumentid IN (:instrumentIds)
ORDER BY instrumentid, date DESC
```

## REST

- `PortfolioController` — `GET /api/v1/portfolio` with `@RequesterId UserId`.
- `InternalPortfolioController` (`rest.internal`, `@ConditionalOnProperty(name = "cocos.internal-tools.enabled",
  havingValue = "true")`) — `GET /api/internal/users/{userId}/portfolio`; the path variable is `@Positive int userId`, converted
  with `new UserId(...)`. 0 or negative → 400 `VALIDATION_ERROR` `Request validation failed: 'userId' must be greater
  than 0.` (DEC-26, Bean Validation on the path variable); not an integer or out of the `int` range →
  `Request validation failed: 'userId' has an invalid type or format.` (DEC-25). Same response mapping as the user
  endpoint.
- Response DTOs follow the contract schema `Portfolio` and hold only JDK types (`String`, numbers, `boolean`, `Instant`) and other DTOs, never domain types (enums
  via `name()`; Constitution V.6); money and percentages via `ApiDecimals.format`; `warnings` is
  always an array (empty when none).

## BDD

- `CucumberSpringConfiguration`: add the property `cocos.internal-tools.enabled=true` (administrator scenarios from
  this spec on). `InternalToolsDisabledIT` (spec 003) keeps its own context with the default value.
- `bdd/steps/PortfolioSteps.java` implements the steps of the feature file:
  - `the portfolio positions are:` compares the **whole** list, in order, column by column (`-` = null);
  - `user N has these filled orders:` inserts `FILLED` `MARKET` rows with SQL **in table order** (ascending ids),
    instrument by ticker, `datetime` from the ISO instant (bind as `OffsetDateTime`), `idempotency_key` and
    `request_fingerprint` null;
  - `the last price of "<TICKER>" is "<price>"`: `UPDATE marketdata SET close = :price` on the latest row of that
    instrument;
  - `user N requests the URL "<path>"`: `GET` with `X-User-Id: N`;
  - `the administrator requests the portfolio of user N`: `GET /api/internal/users/N/portfolio` with `X-Role: ADMIN`.
- `user N placed an order:` is the step from spec 005 (`OrderSteps`); reuse it, do not duplicate it.

## Tests

| CU | Unit | Integration |
|---|---|---|
| 01.01 | `PortfolioTest` (seed numbers, total) | `GetPortfolioServiceIT` (seed through the real adapters) |
| 01.02 | `PositionTest` (PAMP returns) | — |
| 01.03 | `PositionTest` (negative quantity warning, value included) | — |
| 01.04 | — | `JdbcPortfolioReaderIT` (LOMA with only NEW orders is not an open position) |
| 01.05 | — | `JdbcPortfolioReaderIT` (quantity 0 excluded) |
| 01.06 | `PortfolioTest` (no quote: nulls, excluded from total, both warnings) | — |
| 01.07 | `PortfolioTest` (empty) | `GetPortfolioServiceIT` (user 2) |
| 01.08 | `GetPortfolioServiceTest` (unknown user → `NotFoundException`; `get` is annotated `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`, checked by reflection) | — |
| 01.09 | — | `PortfolioControllerIT` (`/api/v1/users/1/portfolio` → 404; user 2 gets their own) |
| 01.10 | — | `InternalPortfolioControllerIT` (admin 200; without role 404; also, without CU tag, `userId` 0 → 400 DEC-26) |
| 01.11 | — | `GetPortfolioServiceIT` (after placing an order through `PlaceOrderUseCase`) |
| 01.12, 01.13 | `AverageCostCalculatorTest` | — |
| 01.14 | `AverageCostCalculatorTest` (order matters) | `JdbcPortfolioReaderIT` (fills ordered by `datetime`, then `id`) |

Also (no CU tag): `GetPortfolioServiceIT` asserts that the number of SQL statements per request is constant
(FR-003). Use `@MockitoSpyBean` on the `AccountReader`, `LedgerReader`, `PortfolioReader` and `MarketDataRepository`
beans and, for user 1 (three open positions), verify that `find`, `cashBalance`, `openPositions`, `fills` and
`latestQuotes` are each called exactly once. Do not add a `DataSource` proxy library.

## Contract, features and HTTP examples

- `cp specs/006-portfolio/contracts/openapi.yaml src/main/resources/static/openapi/openapi.yaml`
- `cp specs/006-portfolio/features/cu-01-portfolio.feature src/test/resources/features/`
- Append `CU-01` to `src/test/resources/traceability/scope.txt`.
- `http/006-portfolio.http`: user 1, user 2, unknown user, administrator (with and without role).
