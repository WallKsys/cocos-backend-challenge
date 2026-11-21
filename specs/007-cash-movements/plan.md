# Implementation Plan: Deposits and withdrawals

**Branch**: `007-cash-movements-<slice>` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

## Summary

Two endpoints over one use case. A cash movement is validated, the user row is locked, the idempotency key is
checked (shared lookup with orders), a withdrawal is evaluated against the available cash, and the movement is
stored as a `CASH_IN` / `CASH_OUT` row of `orders`. No migration is needed: `size NUMERIC(18,2)` already accepts cents
(CM-03), and `requested_amount` stays null for cash movements (V6 constraint).

## Constitution Check

| Principle | How this spec complies |
|---|---|
| I Ledger | Movements are `orders` rows; withdrawals validated against **available** cash |
| VII Concurrency / idempotency | Same lock and the same idempotency lookup as spec 005 |
| VIII Money | Amounts are `Money` with cents |
| X PR size | Three slices |

## Project Structure

```text
src/main/java/com/cocos/challenge/
├── domain/model/
│   ├── CashMovementRequest.java    record (OrderSide side /* CASH_IN | CASH_OUT */, Money amount) + fingerprint()
│   └── CashMovement.java           record (long id, UserId userId, OrderSide side, OrderStatus status, Money amount,
│                                           Rejection rejection /*nullable*/, Instant createdAt, String idempotencyKey,
│                                           RequestFingerprint fingerprint)
├── domain/service/CashMovementEvaluator.java
├── application/port/in/{DepositCashUseCase, WithdrawCashUseCase}.java   + CashMovementCommand, CashMovementResult
├── application/port/out/CashMovementRepository.java   CashMovement findById(long); CashMovement save(NewCashMovement); Optional<InstrumentId> findCashInstrument()
├── application/service/
│   ├── IdempotencyLookup.java      shared helper (§ Shared idempotency lookup)
│   └── CashMovementService.java    implements both use cases
└── infrastructure/
    ├── adapter/out/persistence/JdbcCashMovementRepository.java
    ├── adapter/in/rest/cash/{CashMovementController, CashMovementRequestBody, CashMovementResponse}.java
    └── config/CashMovementConfiguration.java
```

## Domain rules

`CashMovementRequest.of(OrderSide side, String amount)` (amount already passed `@DecimalString`):
- amount ≤ 0 → `InvalidRequestException("Request validation failed: 'amount' must be greater than 0.")`.
- `fingerprint()` = `RequestFingerprint.of("<SIDE>|amount=<plain 2 decimals>")`, for example
  `CASH_IN|amount=100000.00`. A deposit and a withdrawal with the same key and amount differ in fingerprint →
  conflict (DEC-23).

`CashMovementEvaluator.evaluate(CashMovementRequest request, Account account, CashBalance cash)` → status and
optional rejection:
- `CASH_IN` → `FILLED`.
- `CASH_OUT` → `FILLED` if amount ≤ `cash.available()`; otherwise `REJECTED` with `INSUFFICIENT_FUNDS` and the message
  of spec 005: `Insufficient available cash in account <acct>: required ARS <amount>, available ARS <available>.`

## Shared idempotency lookup

`application.service.IdempotencyLookup` (a plain class, constructed in configuration; not a port):

```java
public final class IdempotencyLookup {
    public IdempotencyLookup(OrderRepository orders, BusinessMetrics metrics) { ... }

    /** Empty if the key was never used by this user. If used with the same fingerprint, returns the stored row id
     *  (and records a replay). If used with another fingerprint, records a conflict and throws IdempotencyConflictException. */
    public OptionalLong find(UserId userId, String key, RequestFingerprint fingerprint) { ... }
}
```

It uses `OrderRepository.findIdempotencyRecord` (spec 005, amended). **Refactor `PlaceOrderService` to use it** in
slice 007-a, with no behavior change: every spec 005 test must stay green without modification.

## Application

`CashMovementService` implements `DepositCashUseCase.deposit(CashMovementCommand)` and
`WithdrawCashUseCase.withdraw(CashMovementCommand)`, both `@Transactional`, with
`CashMovementCommand(UserId userId, String idempotencyKey, CashMovementRequest request)` and
`CashMovementResult(CashMovement movement, boolean replayed)`. Flow (Principle VII):

1. Lock the user (`AccountLock`, spec 005) or `NotFoundException("User <id> was not found.")`; record the lock wait.
2. `IdempotencyLookup.find(...)`: a stored id → return `CashMovementResult(repository.findById(id), true)`.
3. `cash = ledgerReader.cashBalance(userId)` (only needed for withdrawals; read it in both for simplicity).
4. `CashMovementEvaluator.evaluate(...)`.
5. Save with `createdAt = clock.instant().truncatedTo(MICROS)`, the cash instrument id, `type` `MARKET`, `price` 1.
6. `businessMetrics.orderProcessed(side, MARKET, status, reasonOrNull)`.

## Persistence

- `findCashInstrument`: `SELECT id FROM instruments WHERE ticker = 'ARS' AND type = 'MONEDA'`.
- `save`: `INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime, rejection_code,
  rejection_message, idempotency_key, request_fingerprint) VALUES (...) RETURNING id` with `size` = amount, `price`
  = 1, `type` = `'MARKET'`. `createdAt` bound as `OffsetDateTime` in UTC.
- `findById`: `SELECT ... FROM orders WHERE id = :id AND side IN ('CASH_IN', 'CASH_OUT')`.

## REST

`CashMovementController`:
- `POST /api/v1/cash/deposits` and `POST /api/v1/cash/withdrawals`, each with `@RequesterId UserId`,
  `@IdempotencyKeyHeader String`, `@Valid @RequestBody CashMovementRequestBody(@NotNull @DecimalString String amount)`.
- `201` via `IdempotentResponses.created`, `200` via `IdempotentResponses.replayed`.
- `CashMovementResponse(long movementId, String type, String status, String amount, Instant createdAt,
  RejectionResponse rejection)` (reuse the rejection DTO of spec 005).

## BDD steps (`bdd/steps/CashMovementSteps.java`)

- `user N deposits "<amount>"` / `withdraws "<amount>"` (new random key); `... with idempotency key "<k>"`;
  `... without idempotency key`.
- `user N deposited "<amount>" with idempotency key "<k>"` / `withdrew ...` (Given form, same request).
- `user N sends a deposit|withdrawal with the raw JSON body:`.
- `user N sends the same cash movement again`; `the response is a replay of the original cash movement` (200,
  header, same `movementId` and body).
- `the cash movement is created with status "<S>"`, `the cash movement type is "<T>" and its amount is "<A>"`,
  `the cash movement rejection is "<code>" with message "<msg>"`.
- `the stored row of the cash movement has side "<S>", size "<N>" and price "<P>"`: SQL on the row of `movementId`.
- Reuse from spec 005: `the available cash of user N is`, `no order is stored`, `user N placed an order:`. In the
  Scenario Outline of CU-05.07, `<operation>` is `deposits` or `withdraws`, matching the step texts above.

## Tests

| CU | Unit | Integration |
|---|---|---|
| 05.01 | `CashMovementEvaluatorTest` (deposit FILLED) | `JdbcCashMovementRepositoryIT` (stored CASH_IN, size 100000.00, price 1.00, ARS instrument) |
| 05.02 | `CashMovementRequestTest` (cents kept) | `JdbcCashMovementRepositoryIT` (1234.56 stored and read back) |
| 05.03, 06.08 | — | `CashMovementControllerIT` (`10.555` → `@DecimalString` message) |
| 05.04, 06.07 | `CashMovementRequestTest` (0 and negative) | — |
| 05.05, 06.04 | `CashMovementServiceTest` (replay with fakes) | — |
| 05.06, 06.05 | — | `CashMovementControllerIT` (unknown field) |
| 05.07, 06.06 | `CashMovementServiceTest` (conflict) | `CashMovementControllerIT` (deposit key reused for a withdrawal) |
| 05.08, 06.09 | — | `CashMovementControllerIT` (missing key) |
| 06.01, 06.02, 06.03 | `CashMovementEvaluatorTest` (available, rejection message, exact available with cents) | — |

Also (no CU tag): `CashMovementControllerIT` unknown user → 404; `CashIdempotencyIT` (M-07): 50 simultaneous
identical deposits with one key → exactly one row, all responses with the same `movementId`.

## Contract, features and HTTP examples

- `cp specs/007-cash-movements/contracts/openapi.yaml src/main/resources/static/openapi/openapi.yaml`
- `cp specs/007-cash-movements/features/*.feature src/test/resources/features/`
- Append `CU-05` and `CU-06` to `src/test/resources/traceability/scope.txt`.
- `http/007-cash-movements.http`: deposit (user 2), replay, withdrawal, rejected withdrawal, conflict.
