# Implementation Plan: Internal tools and volume verification

**Branch**: `009-internal-tools-volume-<slice>` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

## Verified facts (2026-11-21, PostgreSQL 18.7 in Docker on an Apple Silicon laptop)

- `volume/volume-dataset.sql` loads **10,000,011 orders, 100,066 instruments, 200,126 market data rows and 1,004
  users in about 2 minutes** (database size 2.7 GB). A first version matched the seed ticker `VALO` with
  `LIKE 'V%'`; the file now uses `ticker ~ '^V[0-9]{6}$'`.
- SQL time per query for a user with 10,000 orders (warm cache):

  | Query | Time | Plan |
  |---|---|---|
  | cash balance | ~3 ms | index-only scan on `orders_user_status_instrument_idx` |
  | open positions (45 instruments) | ~6 ms | index-only scan on `orders_user_status_instrument_idx` |
  | fills of open positions (~8,000 rows) | ~5 ms | index scan |
  | latest quotes ×50 | ~0.5–2 ms | `marketdata_instrument_date_key` |
  | history page / with filter and cursor | ~0.2–1.3 ms | `orders_user_history_idx` |
  | idempotency lookup | ~0.2 ms | index |
  | partial search over 100k (worst case: matches every name) | ~14–27 ms | trigram bitmap scans |
  | search without criteria (first page) | ~11 ms | sort of 100k tickers |

  The SQL part of the portfolio is about 15 ms against a 200 ms p95 target: **RSK-01 does not materialize** and
  pre-computed positions are not needed (TEC-17 criterion not met).
- With `SET enable_seqscan = off`, every query of § Plan guard uses an index on the seed database.

## Internal tools

Use cases (all in `application`, `@Transactional`, names ending in `UseCase`):

| Use case | Flow |
|---|---|
| `DeleteOrderUseCase.delete(long orderId)` | `owner = adminOrders.findOwner(orderId)` or 404 `Order <id> was not found.`; lock the owner (`AccountLock`); `adminOrders.delete(orderId)` |
| `ExecuteOrderUseCase.execute(long orderId)` | find owner or 404; lock the owner; `adminOrders.executeIfNew(orderId)`; if 0 rows → re-read the status and throw `OrderNotExecutableException(orderId, status)`; return the `OrderRecord` (spec 008 `OrderLedgerRepository.findRecord` by id, any user — add `findRecordById(long)` for admin use) |
| `UpdateMarketDataUseCase.update(InstrumentId, Money close, Money previousCloseOrNull)` | instrument or 404 `Instrument <id> was not found.`; `MONEDA` → 400 (spec.md edge case); close/previousClose > 0 or 400; upsert (§ Persistence) |
| `RestoreSeedDataUseCase.restore()` | `seedDataRestorer.restore()` |
| `CreateUserUseCase.create(String email)` | `userWriter.create(email)` → `(id, email, accountNumber)` |

`executeIfNew`: `UPDATE orders SET status = 'FILLED' WHERE id = :orderId AND status = 'NEW'` — the stored `price` is
already the limit price, so the order is FILLED at its limit price and the ledger converts the reservation into a
debit (BUY) or credit (SELL) automatically. `datetime` is not changed (single timestamp column; documented
simplification). No availability check: the reservation already guaranteed it, and the tools may break invariants
on purpose (DEC-03).

New domain exception `OrderNotExecutableException(long orderId, OrderStatus status)`, message
`Order <id> cannot be executed because its status is <STATUS>. Only NEW orders can be executed.`, mapped by
`ApiExceptionHandler` to `ORDER_NOT_EXECUTABLE` (409, title `Order not executable`, slug `order-not-executable`).
Add the code to `ApiErrorCode`.

### Persistence

- `findOwner`: `SELECT userid FROM orders WHERE id = :orderId`.
- `delete`: `DELETE FROM orders WHERE id = :orderId`.
- Market data upsert: if the instrument has rows, `UPDATE marketdata SET close = :close, previousclose =
  COALESCE(:previousClose, previousclose) WHERE id = (SELECT id FROM marketdata WHERE instrumentid = :id ORDER BY
  date DESC LIMIT 1)`; otherwise `INSERT INTO marketdata (instrumentid, date, close, previousclose) VALUES (:id, :date, :close,
  :previousClose)`, with `:date` = `LocalDate.now(clock)` (the clock is UTC).
- `JdbcSeedDataRestorer.restore` (production code): in one transaction, `SET LOCAL TIME ZONE 'UTC'`, `TRUNCATE
  orders, marketdata, instruments, users RESTART IDENTITY CASCADE`, then every `INSERT` statement of the classpath
  resource `db/migration/V1__original_schema_and_seed.sql`, in order (the same approach as the test helper
  `SeedData` of spec 004; keep both).
- `JdbcUserWriter.create`: `INSERT INTO users (email, accountnumber) VALUES (:email, (SELECT
  (COALESCE(MAX(accountnumber::bigint), 10000) + 1)::text FROM users WHERE accountnumber ~ '^[0-9]+$')) RETURNING id,
  email, accountnumber`.

### REST (`infrastructure.adapter.in.rest.internal`, all `@ConditionalOnProperty`)

| Endpoint | Controller | Response |
|---|---|---|
| `DELETE /api/internal/orders/{orderId}` | `InternalOrderToolsController` | 204 |
| `POST /api/internal/orders/{orderId}/execute` | `InternalOrderToolsController` | 200 `OrderRecordResponse` (spec 008) |
| `PUT /api/internal/instruments/{instrumentId}/market-data` | `InternalMarketDataController` | 200 `MarketDataView` |
| `POST /api/internal/reset` | `InternalDataController` | 204 |
| `POST /api/internal/users` | `InternalDataController` | 201 `UserView` |

Request bodies: `MarketDataUpdateBody(@NotNull @DecimalString String close, @DecimalString String previousClose)`,
`CreateUserBody(@NotBlank @Email @Size(max = 255) String email)`.

## CU-04.08 with the real tool

Change the step `the administrator forces its execution at the same time` (spec 008) to call
`POST /api/internal/orders/{id}/execute` with `X-Role: ADMIN` instead of `ForcedExecutionSimulator`. In
`CancelVersusExecutionIT`, add a test that runs `CancelOrderUseCase` and `ExecuteOrderUseCase` concurrently (20
repetitions). The `.feature` file does not change. Keep `ForcedExecutionSimulator` only if another test still uses
it; otherwise delete it.

## BDD steps (`bdd/steps/InternalToolsSteps.java`)

- `a user without the administrator role calls "<METHOD>" "<path>"`: sends the request with `X-User-Id: 1`, no
  `X-Role`, and a valid body for PUT/POST (`{"close": "1.00"}`, `{"email": "x@test.com"}`, none for reset).
- `the seed data is unchanged`: 11 orders, 4 users, 66 instruments, order 7 `NEW`, PAMP latest close 925.85.
- `the administrator forces the execution of order <id>` / `of that order`; `deletes order <id>`; `deleted that
  order`; `sets|set the market data of "<TICKER>" to close "<price>"`; `restores the seed data`; `creates a user with
  email "<e>"` — all with `X-Role: ADMIN`.
- `the order record has status "<S>" and price "<P>"`; `order <id> does not exist`; `the order id is different from
  the deleted order`; `the created user has id <n>, email "<e>" and account number "<a>"`.
- `the portfolio of user N has cash ...`, `... has total value ...`, `... has the warning ... with message ...`,
  `... has the position:` (one row, compared column by column), `... has a position "<T>" with quantity <n> and no
  last price, market value, total return or daily change` — they call the administrator portfolio endpoint
  (spec 006) and reuse the comparison helpers of `PortfolioSteps`.
- Reuse: `user N placed an order[ with idempotency key ...]:`, `user N sends the same order again`, `the order is
  created with status`, `user N holds`, `I search instruments for`, `the instrument ... has name ...`,
  `user N requests their portfolio`, `the portfolio total value is`, `the portfolio has no positions`.

## Plan guard (`infrastructure/adapter/out/persistence/PlanGuardIT`, default build)

To test the real SQL, each `Jdbc*` adapter exposes its SQL as package-private `static final String` constants, and
`JdbcInstrumentSearchRepository` a package-private method that builds the SQL for given criteria. `PlanGuardIT` lives
in the same package. For each query, inside a transaction: `SET LOCAL enable_seqscan = off`, then
`EXPLAIN (FORMAT JSON) <query>` with representative parameters; parse the JSON and fail if any node has
`"Node Type": "Seq Scan"` on `orders`, `instruments` or `marketdata`, naming the query.

Queries: account lock; cash balance; share balance; idempotency record lookup; open positions; fills; latest quote;
latest quotes (batch); history (no filter, status filter, cursor); single order record; search with `q` long, `q`
short, ticker filter, name filter, no criteria with cursor.

## Volume profile

- `src/test/resources/volume/volume-dataset.sql`: copy of `specs/009-internal-tools-volume/volume/volume-dataset.sql`
  (byte-identical; add it to the PR-size exclusions as a copied artifact if needed — it is under 100 lines anyway).
- `pom.xml`: Failsafe gets `<excludedGroups>volume</excludedGroups>` by default; a profile `volume` overrides it with
  `<groups>volume</groups>` and an empty `excludedGroups`, so `./mvnw verify -Pvolume` runs only the volume test.
- `volume/VolumeIT` (`@Tag("volume")`): its **own** PostgreSQL container (never the shared one), the application
  context against it, then execute `volume-dataset.sql` once (JDBC, one statement at a time), then for user id 500
  (a volume user) and through HTTP (`RestTestClient`):

  | Metric | Request | Warm-up / measured | p95 / p99 targets |
  |---|---|---|---|
  | M-08 | `GET /api/v1/portfolio` | 50 / 500 | 200 / 400 ms |
  | M-09 | `GET /api/v1/orders?limit=20` (half without cursor, half with the second page cursor) | 50 / 500 | 100 / 200 ms |
  | M-10 | `POST /api/v1/orders` BUY MARKET 1 share of `V000001`, new key each time | 50 / 500 | 150 / 300 ms |
  | M-11 | `GET /api/v1/instruments?q=12345` and `?q=volume` (worst case), alternating | 50 / 500 | 100 / 200 ms |

  Compute percentiles over the measured latencies (nearest-rank). Print a table with p50, p95 and p99 per metric
  and fail if a target is not met.

## Reference environment (PA-03)

The targets are verified on the author's machine (Apple Silicon laptop, Docker Desktop, PostgreSQL 18.7 container)
with the dataset above. The volume test prints the measured values; record them in the PR of slice 009-d.

## Traceability completion

Append `CU-I` to `scope.txt`. Add `traceability/ScopeIsCompleteTest` (unit): every CU group present in
`docs/casos-de-uso.md` (`CU-01` … `CU-07`, `CU-I`) is listed in `scope.txt`.

## Tests

| CU | Unit | Integration |
|---|---|---|
| I.01 | — | `InternalToolsControllerIT` (each tool without role → 404; data unchanged) |
| I.02, I.09 | `ExecuteOrderServiceTest` (fakes) | `JdbcAdminOrderRepositoryIT` (`executeIfNew` 1 then 0 rows) |
| I.03 | `ExecuteOrderServiceTest` (exception with status) | — |
| I.04, I.05 | `DeleteOrderServiceTest` | `JdbcAdminOrderRepositoryIT` (row and its key gone) |
| I.06 | `UpdateMarketDataServiceTest` (validation) | `JdbcMarketDataWriterIT` (update latest row; insert when none) |
| I.07 | — | `JdbcSeedDataRestorerIT` (after changes, counts and ledger equal the seed; UTC timestamps) |
| I.08 | — | `JdbcUserWriterIT` (account number 10005 on the seed) |

## Contract, features and HTTP examples

- `cp specs/009-internal-tools-volume/contracts/openapi.yaml src/main/resources/static/openapi/openapi.yaml`
- `cp specs/009-internal-tools-volume/features/cu-i-internal-tools.feature src/test/resources/features/`
- `http/009-internal-tools.http` (a separate file: internal tools only), every tool with `X-Role: ADMIN`.
