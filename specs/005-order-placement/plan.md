# Implementation Plan: Order placement

**Branch**: `005-order-placement-<slice>` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

## Summary

`POST /api/v1/orders`. Pure domain rules decide the outcome of an order (status, quantity, price, rejection); the
application service runs the flow of Constitution Principle VII in one transaction under a lock of the user row;
persistence adapters run hand-written SQL; the REST adapter maps requests and responses. Migration V6 adds
`orders.requested_amount` (CM-12) so that replays return the original response.

## Verified facts

- Migration V6 applies on top of V1–V5 (PostgreSQL 18.7) and its check rejects a requested amount on cash movements.
- The per-instrument balance queries of § Persistence return PAMP 40 / reserved 0 for user 1; the lock query returns
  `1 | 10001`.
- Jackson strictness (spec 003, DEC-24) makes `{"quantity": 1.5}` and unknown fields fail before reaching the domain.

## Constitution Check

| Principle | How this spec complies |
|---|---|
| I Ledger | Balances computed from `orders`; validations against **available** amounts; whole shares |
| III / IV | 42 Gherkin scenarios + unit/integration tests per CU (§ Tests); `CU-03` added to scope |
| V Hexagonal | Rules in `domain.service.OrderEvaluator`; flow in `application.service.PlaceOrderService`; SQL in persistence |
| VII Concurrency / idempotency | Lock of `users` row first; key + fingerprint lookup; unique constraint as safety net |
| VIII Money | `Money` everywhere; `BigDecimal` division with explicit rounding |
| X PR size | Six slices |

## Project Structure

```text
src/main/java/com/cocos/challenge/
├── domain/model/
│   ├── Account.java              record Account(UserId userId, String accountNumber)
│   ├── Instrument.java           record Instrument(InstrumentId id, String ticker, InstrumentType type)
│   ├── CashBalance.java          record CashBalance(Money cash, Money reserved) → available()
│   ├── ShareBalance.java         record ShareBalance(long quantity, long reserved) → available()
│   ├── OrderRequest.java         validated BUY/SELL request (§ Validation)
│   ├── Rejection.java            record Rejection(RejectionReason reason, String message)
│   ├── OrderEvaluation.java      record (OrderStatus status, long quantity, Money price /*nullable*/, Rejection rejection /*nullable*/)
│   └── Order.java                stored order (§ Order)
├── domain/service/OrderEvaluator.java          pure rules (§ Order evaluation)
├── application/port/in/PlaceOrderUseCase.java  + PlaceOrderCommand, PlaceOrderResult (records)
├── application/port/out/
│   ├── AccountLock.java          Optional<Account> lock(UserId)
│   ├── LedgerReader.java         CashBalance cashBalance(UserId); ShareBalance shareBalance(UserId, InstrumentId)
│   ├── InstrumentRepository.java Optional<Instrument> findById(InstrumentId)
│   ├── MarketDataRepository.java Optional<MarketQuote> latestQuote(InstrumentId)
│   ├── IdempotencyRecord.java    record IdempotencyRecord(long orderId, OrderSide side, RequestFingerprint fingerprint)
│   └── OrderRepository.java      Optional<IdempotencyRecord> findIdempotencyRecord(UserId, String key); Order findById(long id); Order save(NewOrder)
├── application/service/PlaceOrderService.java
└── infrastructure/
    ├── adapter/out/persistence/
    │   ├── JdbcAccountLock.java
    │   ├── JdbcLedgerReader.java
    │   ├── JdbcInstrumentRepository.java
    │   ├── JdbcMarketDataRepository.java
    │   └── JdbcOrderRepository.java
    ├── adapter/in/rest/order/
    │   ├── OrderController.java
    │   ├── PlaceOrderRequestBody.java
    │   └── OrderResponse.java (+ RejectionResponse)
    └── config/OrderConfiguration.java

src/main/resources/db/migration/V6__orders_requested_amount.sql   (copy of specs/005-order-placement/migrations/)
src/main/resources/static/openapi/openapi.yaml                     (copy of this spec's contract)
src/test/resources/features/cu-03-order-placement.feature          (copy of this spec's feature)
src/test/resources/traceability/scope.txt                          (append CU-03)
```

`NewOrder` (in `application.port.out`) is the record the service hands to `OrderRepository.save`: all fields of
`Order` except `id`.

## Validation

`OrderRequest.of(Long instrumentId, String side, String type, Integer quantity, String amount, String price)` runs
after Bean Validation of the body (types and decimal format) and throws `InvalidRequestException` with these exact
details, checked **in this order**:

| # | Rule | Detail |
|---|---|---|
| 1 | `side` is `BUY` or `SELL` | `Request validation failed: 'side' must be BUY or SELL.` |
| 2 | `type` is `MARKET` or `LIMIT` | `Request validation failed: 'type' must be MARKET or LIMIT.` |
| 3 | exactly one of `quantity`, `amount` | `Request validation failed: exactly one of 'quantity' or 'amount' is required.` |
| 4 | `quantity` ≥ 1 | `Request validation failed: 'quantity' must be greater than 0.` |
| 5 | `amount` > 0 | `Request validation failed: 'amount' must be greater than 0.` |
| 6 | LIMIT has `price` | `Request validation failed: 'price' is required for LIMIT orders.` |
| 7 | LIMIT `price` > 0 | `Request validation failed: 'price' must be greater than 0.` |
| 8 | MARKET has no `price` | `Request validation failed: 'price' must not be sent for MARKET orders.` |

Body record `PlaceOrderRequestBody(@NotNull @Positive Long instrumentId, @NotNull String side, @NotNull String type,
Integer quantity, @DecimalString String amount, @DecimalString String price)`. `side` and `type` are `String` (not
enums) so the messages above are ours, not Jackson's. Missing `instrumentId`, `side` or `type` → Bean Validation
`VALIDATION_ERROR` (spec 003 format). A non-integer `quantity` (`1.5`) → `Request validation failed: 'quantity' has
an invalid type or format.` (spec 003). A `quantity` out of the
range of its type (`99999999999`) → the same detail (DEC-28, slice 003-f).

Checks done by the service (they need the database), with their details:

| Check | Response |
|---|---|
| user does not exist (lock returns empty) | `NotFoundException("User <id> was not found.")` → 404 `NOT_FOUND` |
| instrument does not exist | `InvalidRequestException("Request validation failed: 'instrumentId' does not reference an existing instrument.")` |
| instrument is `MONEDA` | `InvalidRequestException("Request validation failed: 'instrumentId' must reference a tradable instrument; ARS cannot be bought or sold.")` |

These run **after** the lock and the idempotency lookup (Principle VII) and before storing; the transaction rolls
back, so nothing is stored and the key is not consumed.

## Request fingerprint

`OrderRequest.fingerprint()` = `RequestFingerprint.of(canonical)` where `canonical` is exactly:

```text
ORDER|instrumentId=<id>|side=<SIDE>|type=<TYPE>|quantity=<n or empty>|amount=<plain 2 decimals or empty>|price=<plain 2 decimals or empty>
```

Example: `ORDER|instrumentId=47|side=BUY|type=MARKET|quantity=10|amount=|price=`. Amounts are normalized by `Money`
(`"900"` and `"900.00"` give the same fingerprint). Spec 007 uses the prefixes `CASH_IN` / `CASH_OUT`, so a key
reused across operation types always differs in fingerprint → conflict (DEC-23).

## Order evaluation

`OrderEvaluator.evaluate(OrderRequest request, Instrument instrument, Optional<MarketQuote> quote, Account account,
CashBalance cash, ShareBalance shares)` → `OrderEvaluation`. Pure Java, no I/O. Steps in order:

1. **Reference price**: MARKET → `quote.close()`; if there is no quote → `REJECTED`, reason `NO_MARKET_PRICE`,
   quantity = requested quantity or 0 (by amount), price = null. LIMIT → the limit price.
   **Price band** (LIMIT only, and only when there is a quote; DEC-29): `min` = `close × 0.80` rounded **UP**
   (`RoundingMode.CEILING`) to 2 decimals, `max` = `close × 1.20` rounded **DOWN** (`RoundingMode.FLOOR`). A limit
   price below `min` or above `max` → `REJECTED`, reason `PRICE_OUT_OF_RANGE`, quantity = requested quantity or 0
   (by amount), price = the limit price. Both bounds are allowed (PAMP at 925.85: 740.68 to 1111.02). Without a
   quote there is no band (CU-03.19).
2. **Quantity**: by quantity → as requested. By amount → `amount / price` rounded **DOWN** to an integer
   (`BigDecimal.divide(price, 0, RoundingMode.DOWN)`). If it is greater than 2147483647 → `REJECTED`, reason
   `QUANTITY_TOO_LARGE`, quantity 0, price = reference price (DEC-30). If it is 0 → `REJECTED`, reason
   `AMOUNT_BELOW_PRICE`, quantity 0, price = reference price.
3. **Total** = `quantity × price` (`Money.times`).
4. **Availability**: BUY → total must be ≤ `cash.available()`, otherwise `INSUFFICIENT_FUNDS`. SELL → quantity must
   be ≤ `shares.available()`, otherwise `INSUFFICIENT_SHARES`. Rejected orders keep the computed quantity and price.
5. Otherwise: MARKET → `FILLED`; LIMIT → `NEW`.

Rejection messages (English, exact; money as plain text with 2 decimals; `<acct>` = `account.accountNumber()`):

| Reason | Message |
|---|---|
| `INSUFFICIENT_FUNDS` | `Insufficient available cash in account <acct>: required ARS <total>, available ARS <available>.` |
| `INSUFFICIENT_SHARES` | `Insufficient available shares of <TICKER> in account <acct>: required <quantity>, available <available>.` |
| `AMOUNT_BELOW_PRICE` | `The amount ARS <amount> is not enough to buy one share of <TICKER> at ARS <price>.` (`sell` for SELL) |
| `NO_MARKET_PRICE` | `No market price is available for <TICKER>; MARKET orders cannot be executed.` |
| `PRICE_OUT_OF_RANGE` | `The limit price ARS <price> is outside the allowed range for <TICKER>: ARS <min> to ARS <max> (20% around the last price ARS <close>).` |
| `QUANTITY_TOO_LARGE` | `The amount ARS <amount> would buy more than 2147483647 shares of <TICKER> at ARS <price>.` (`sell` for SELL) |

Available shares may be negative (seed BMA: `-10`); the message shows it as is (DEC-11).

## Order

`Order` fields: `id`, `userId`, `instrumentId`, `ticker`, `side`, `type`, `status`, `quantity` (long), `price`
(nullable `Money`), `requestedAmount` (nullable `Money`), `rejection` (nullable), `createdAt` (`Instant`),
`idempotencyKey`, `fingerprint`. Derived: `totalAmount()` = quantity × price (null without price);
`remainingAmount()` = requestedAmount − totalAmount (null if either is null).

## Application

```java
public interface PlaceOrderUseCase {
    PlaceOrderResult place(PlaceOrderCommand command);
}
public record PlaceOrderCommand(UserId userId, String idempotencyKey, OrderRequest request) {}
public record PlaceOrderResult(Order order, boolean replayed) {}
```

`PlaceOrderService.place` — `@Transactional`:

1. `start = System.nanoTime()`; `account = accountLock.lock(userId)` or throw `NotFoundException`;
   `businessMetrics.accountLockWaited(Duration.ofNanos(System.nanoTime() - start))`.
2. `orderRepository.findIdempotencyRecord(userId, key)`: present with the same fingerprint →
   `businessMetrics.idempotentReplay(side)` and return `PlaceOrderResult(orderRepository.findById(record.orderId()), true)`;
   present with a different fingerprint → `businessMetrics.idempotencyConflict()` and throw
   `IdempotencyConflictException(key)`. **Why a lightweight record first**: the key is unique per user across all
   operations (DEC-23), so the row found may be a deposit or withdrawal whose `size` has cents; mapping it to an
   `Order` (whole shares) would fail with an arithmetic error instead of answering the conflict. The full order is
   loaded only after the fingerprint proves it is the same order request.
3. Instrument lookup and tradability checks (§ Validation).
4. `quote` (MARKET only needs it; read it anyway), `cash`, `shares` from `LedgerReader`.
5. `OrderEvaluator.evaluate(...)`.
6. `orderRepository.save(new NewOrder(..., createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS), ...))`.
7. `businessMetrics.orderProcessed(side, type, status, reasonOrNull)`; return `PlaceOrderResult(saved, false)`.

## Persistence

Every SQL statement of this section is a **package-private `static final String` constant** of its adapter (named
after the query, for example `CASH_BALANCE_SQL`). The plan guard of spec 009 (`PlanGuardIT`, same package) runs
`EXPLAIN` on these constants, so they must be the exact statements the adapter executes.

`JdbcAccountLock.lock`: `SELECT id, accountnumber FROM users WHERE id = :userId FOR UPDATE`.

`JdbcLedgerReader.cashBalance` (one query, one row):

```sql
SELECT COALESCE(SUM(CASE
           WHEN status = 'FILLED' AND side = 'CASH_IN'  THEN  size
           WHEN status = 'FILLED' AND side = 'CASH_OUT' THEN -size
           WHEN status = 'FILLED' AND side = 'BUY'      THEN -size * price
           WHEN status = 'FILLED' AND side = 'SELL'     THEN  size * price
       END), 0) AS cash,
       COALESCE(SUM(CASE WHEN status = 'NEW' AND side = 'BUY' THEN size * price END), 0) AS reserved
FROM orders
WHERE userid = :userId AND status IN ('FILLED', 'NEW')
```

`JdbcLedgerReader.shareBalance`:

```sql
SELECT COALESCE(SUM(CASE WHEN status = 'FILLED' AND side = 'BUY'  THEN  size
                         WHEN status = 'FILLED' AND side = 'SELL' THEN -size END), 0) AS quantity,
       COALESCE(SUM(CASE WHEN status = 'NEW' AND side = 'SELL' THEN size END), 0) AS reserved
FROM orders
WHERE userid = :userId AND instrumentid = :instrumentId AND status IN ('FILLED', 'NEW')
  AND side IN ('BUY', 'SELL')
```

Convert the `NUMERIC` results with `Money.of(BigDecimal)` (scale 4 → 2) and shares with
`BigDecimal.longValueExact()` after `stripTrailingZeros()`.

`JdbcMarketDataRepository.latestQuote`: the "latest market data" query of `specs/002-database/data-model.md`.

`JdbcOrderRepository`:
- `findIdempotencyRecord`: `SELECT id, side, request_fingerprint FROM orders WHERE userid = :userId AND
  idempotency_key = :key` (served by the unique index `orders_user_idempotency_key`).
- `findById`: select the order joined with `instruments` (for the ticker) by `id`.
- `save`: `INSERT ... RETURNING id`, writing `instrumentid, userid, size, price, type, side, status, datetime,
  rejection_code, rejection_message, idempotency_key, request_fingerprint, requested_amount`. Bind `createdAt` as
  `OffsetDateTime` in UTC (`createdAt.atOffset(ZoneOffset.UTC)`), never as a string.

## REST

`OrderController` — `POST /api/v1/orders`, parameters `@RequesterId UserId`, `@IdempotencyKeyHeader String`,
`@Valid @RequestBody PlaceOrderRequestBody`. Builds `OrderRequest.of(...)`, calls `PlaceOrderUseCase`, returns
`IdempotentResponses.replayed(dto)` when `replayed`, otherwise `IdempotentResponses.created(dto)`.
`OrderResponse` fields and nullability are those of the contract schema `Order`; money via `ApiDecimals.format`.

## BDD steps (`bdd/steps/OrderSteps.java`)

Implements every step of the feature file. Conventions:

- The vertical table of `places an order:` maps keys to JSON fields: `instrument` → resolve the ticker to
  `instrumentId` with SQL; `instrumentId`, `quantity` → JSON integers; `amount`, `price` → JSON strings; `side`,
  `type` → strings; **any other key** (for example `userId`) is sent as an extra JSON field with an integer value.
- Without `with idempotency key "<k>"`, every request uses a new random UUID key. `without idempotency key` sends no
  header.
- `sends the same order again` resends the last request with the same key and body.
- `the available cash of user N is`, `user N holds N shares of`, `user N can sell N shares of` read the ledger with
  the queries of § Persistence (there is no portfolio endpoint yet).
- `no order is stored`: the number of rows in `orders` equals the number before the `When` step.
- `made a deposit of "<amount>" with idempotency key "<k>"`: inserts a `FILLED` `CASH_IN` on instrument 66 with
  that key and the fingerprint of `CASH_IN|amount=<amount>` (`RequestFingerprint.of`), with the amount normalized by
  `Money` to plain text with 2 decimals (`"1234.5"` → `CASH_IN|amount=1234.50`): exactly the canonical form of
  spec 007, so the row is the one a real deposit would have stored.
- `places these orders at the same time:` sends all requests concurrently (start them together with a
  `CountDownLatch` on an executor) and waits for all responses.
- `the current time is "<instant>"`: sets a test clock. Provide `bdd/support/TestClockConfiguration`, a
  `@TestConfiguration` imported by `CucumberSpringConfiguration`, defining a `@Primary` mutable `Clock` that
  delegates to `Clock.systemUTC()` until a scenario fixes it; `DatabaseHooks` resets it before each scenario.
- `the response is a replay of the original order`: status 200, header `Idempotent-Replayed: true`, and the same
  `orderId` and body as the first response.

## Tests

Every CU test carries `@Tag("<CU id>")` plus `unit` or `integration`.

| CU | Unit (class) | Integration (class) |
|---|---|---|
| 03.01–03.12, 03.13–03.19, 03.38–03.40 | `OrderEvaluatorTest` (one test per CU with the scenario's numbers) | — (BDD covers the HTTP path) |
| 03.43, 03.44, 03.45, 03.46 | `OrderEvaluatorTest` (also, without a CU tag: one cent inside and one cent outside each bound, a SELL by amount with `QUANTITY_TOO_LARGE`) | — (BDD covers the HTTP path) |
| 03.20 | `PlaceOrderServiceTest` (fake lock returns empty → `NotFoundException`) | — |
| 03.21, 03.28 | `PlaceOrderServiceTest` (fake instrument repository) | — |
| 03.22, 03.24, 03.25, 03.26, 03.27 | `OrderRequestTest` | — |
| 03.23, 03.29, 03.30 | — | `OrderControllerIT` (JSON strictness, missing key, unknown field) |
| 03.31, 03.32, 03.34, 03.41 | `PlaceOrderServiceTest` (fake order repository) | — |
| 03.33, 03.35, 03.42 | `PlaceOrderServiceTest` | `JdbcOrderRepositoryIT` (key scoped by user; key shared with a CASH_IN row **with cents**, for example 1234.56, answers the conflict without errors) |
| 03.36 | — | `ConcurrentOrdersIT` (two threads, one FILLED and one REJECTED, cash never negative) |
| 03.37 | — | `JdbcOrderRepositoryIT` (stored and read back as the same `Instant`, session time zone `America/Argentina/Buenos_Aires`) |

PRD metrics (integration, no CU tag), in `IdempotencyLoadIT`:

| Metric | Test |
|---|---|
| M-04 | 1,000 sequential identical requests with one key → exactly 1 row, 1 balance movement, 1,000 responses with the same `orderId` (999 with `Idempotent-Replayed: true`) |
| M-05 | 50 simultaneous identical requests with one key → exactly 1 row; every response has the same `orderId` |
| M-06 | same key with different content and with a deposit row → 100 % `IDEMPOTENCY_CONFLICT`, 0 rows added |
| M-02 | 50 simultaneous different orders that together exceed the available cash → available cash never negative; the sum of FILLED totals ≤ initial available |

Also: `JdbcLedgerReaderIT` (seed balances), `JdbcAccountLockIT` (second transaction waits while the first holds the
lock — use two threads and assert ordering), `OrderEvaluatorTest` rounding cases.

## Contract, features and HTTP examples

- `cp specs/005-order-placement/contracts/openapi.yaml src/main/resources/static/openapi/openapi.yaml`
- `cp specs/005-order-placement/features/cu-03-order-placement.feature src/test/resources/features/`
- `cp specs/005-order-placement/migrations/V6__orders_requested_amount.sql src/main/resources/db/migration/`
- `cp specs/005-order-placement/migrations/V7__orders_new_rejection_codes.sql src/main/resources/db/migration/`
  (CM-13: the two new rejection codes in `orders_rejection_code_check`; verified on PostgreSQL 18 on top of V1–V6,
  it accepts both new codes and still rejects an unknown one)
- `http/005-order-placement.http`: market buy by quantity, by amount, limit sell, a rejection, a replay (same key),
  a conflict.
