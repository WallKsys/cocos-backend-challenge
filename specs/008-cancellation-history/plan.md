# Implementation Plan: Order cancellation and history

**Branch**: `008-cancellation-history-<slice>` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

## Summary

`POST /api/v1/orders/{orderId}/cancel`, `GET /api/v1/orders` (history), `GET /api/v1/orders/{orderId}` and the
internal `GET /api/internal/users/{userId}/orders`. Reads return `OrderRecord`, a ledger view that represents
trades (whole shares) and cash movements (amounts with cents) alike.

## Verified facts

On PostgreSQL 18.7 with migrations V1–V6: the history query returns `11,10,9,8,7,6` for the first page and
`6,5,4,3,2,1` after the cursor `(2023-07-13T11:13:20Z, 7)`; the `NEW` filter returns `7,5`; under the user lock the
cancellation `UPDATE ... WHERE status = 'NEW'` affects 1 row the first time and 0 rows the second time, and the
available cash becomes 717500.00.

## Constitution Check

| Principle | How this spec complies |
|---|---|
| I Ledger | Cancelling changes only the status; the reservation disappears because NEW rows are no longer counted |
| VII Concurrency | Cancel locks the user row first; status change guarded by `WHERE status = 'NEW'` |
| IX API | "my data" style; other users' orders answer 404 like missing ones |
| X PR size | Three slices |

## Project Structure

```text
src/main/java/com/cocos/challenge/
├── domain/model/
│   ├── OrderRecord.java           record (long id, InstrumentId instrumentId, String ticker, OrderSide side, OrderType type,
│   │                                      OrderStatus status, BigDecimal size, Money price /*nullable*/,
│   │                                      Money requestedAmount /*nullable*/, Rejection rejection /*nullable*/, Instant createdAt)
│   │                                quantity(): Long (null for CASH_*); amount(): Money (q × price, or size for CASH_*)
│   ├── HistoryCursor.java         record (Instant createdAt, long orderId): encode()/decode() like SearchCursor (spec 004)
│   ├── OrderHistoryCriteria.java  record (OrderStatus status /*nullable*/, int limit, HistoryCursor cursor /*nullable*/) + of(String, Integer, String)
│   └── OrderHistoryPage.java      record (List<OrderRecord> items, HistoryCursor nextCursor /*nullable*/)
├── application/port/in/
│   ├── CancelOrderUseCase.java    CancellationResult cancel(UserId, long orderId)
│   ├── CancellationResult.java    record (OrderRecord order, boolean alreadyCancelled)
│   ├── GetOrderQuery.java         OrderRecord get(UserId, long orderId)
│   └── GetOrderHistoryQuery.java  OrderHistoryPage list(UserId, OrderHistoryCriteria)
├── application/port/out/OrderLedgerRepository.java
│     Optional<OrderRecord> findRecord(UserId, long orderId); boolean cancelIfNew(UserId, long orderId);
│     List<OrderRecord> history(UserId, OrderHistoryCriteria, int maxResults)
├── application/service/{CancelOrderService, GetOrderService, GetOrderHistoryService}.java
└── infrastructure/
    ├── adapter/out/persistence/JdbcOrderLedgerRepository.java
    ├── adapter/in/rest/order/{OrderQueryController, OrderRecordResponse, OrderHistoryPageResponse, CancellationResponse}.java
    ├── adapter/in/rest/internal/InternalOrderHistoryController.java
    └── config/OrderQueryConfiguration.java
```

The cancel endpoint lives in `OrderQueryController` together with the two reads to keep `/api/v1/orders/{orderId}`
in one controller; `POST /api/v1/orders` stays in `OrderController` (spec 005).

## Cancellation

`CancelOrderService.cancel(UserId userId, long orderId)` — `@Transactional`:

1. Lock the user (`AccountLock`, spec 005) or `NotFoundException("User <id> was not found.")`.
2. `record = repository.findRecord(userId, orderId)` or `NotFoundException("Order <orderId> was not found.")` —
   the query filters by **both** id and user, so another user's order is simply not found (FR-005).
3. By status:
   - `CANCELLED` → `orderCancelled(true)`; return `CancellationResult(record, true)`.
   - `NEW` → `repository.cancelIfNew(userId, orderId)`; it must return `true` (the user lock guarantees no one else
     changed it; if it returns `false`, re-read and apply this same table to the new status). Return
     `CancellationResult(record with status CANCELLED, false)`; `orderCancelled(false)`.
   - `FILLED` / `REJECTED` → `OrderNotCancellableException(orderId, status)` (spec 003: 409).

`cancelIfNew`: `UPDATE orders SET status = 'CANCELLED' WHERE id = :orderId AND userid = :userId AND status = 'NEW'`
→ `true` if one row was updated.

## History and single order

- `OrderHistoryCriteria.of(String status, Integer limit, String cursor)`: status null/blank → no filter; otherwise
  one of the four statuses or `InvalidRequestException("Request validation failed: 'status' must be one of NEW,
  FILLED, REJECTED, CANCELLED.")`; limit as in spec 004 (default 20, 1–100); cursor via `HistoryCursor.decode`.
- `HistoryCursor.encode()` = Base64 URL-safe without padding of `"v1:" + <createdAt in epoch microseconds> + ":" +
  orderId`; invalid → `InvalidRequestException("Request validation failed: 'cursor' is invalid.")`.
- `GetOrderHistoryService.list` (`@Transactional(readOnly = true)`): check the user exists (`AccountReader`, spec
  006) or 404; query with `limit + 1`; `nextCursor` from the last returned item when there are more.
- `GetOrderService.get` (`@Transactional(readOnly = true)`): user exists or 404; `findRecord` or
  `NotFoundException("Order <id> was not found.")`.

`JdbcOrderLedgerRepository`: `findRecord` and `cancelIfNew` are package-private `static final String` constants, and
the history statement is built by a **package-private** method `String historySql(OrderHistoryCriteria criteria)`
from `private static final String` fragments. The plan guard of spec 009 (`PlanGuardIT`, same package) runs
`EXPLAIN` on them, so they must be the exact statements the adapter executes.

`JdbcOrderLedgerRepository.history` (fragments in brackets only when present):

```sql
SELECT o.id, o.instrumentid, i.ticker, o.side, o.type, o.status, o.size, o.price, o.requested_amount,
       o.rejection_code, o.rejection_message, o.datetime
FROM orders o
JOIN instruments i ON i.id = o.instrumentid
WHERE o.userid = :userId
  [AND o.status = :status]
  [AND (o.datetime, o.id) < (:cursorCreatedAt, :cursorOrderId)]
ORDER BY o.datetime DESC, o.id DESC
LIMIT :maxResults
```

`findRecord`: the same columns `WHERE o.id = :orderId AND o.userid = :userId`. Bind instants as `OffsetDateTime` in
UTC. `rejection` is built only when `rejection_code` is not null (seed `REJECTED` order 6 has none, CU-07.04).

## REST

- `OrderQueryController`: `GET /api/v1/orders` (`status`, `limit`, `cursor` query params), `GET
  /api/v1/orders/{orderId}`, `POST /api/v1/orders/{orderId}/cancel` — all with `@RequesterId UserId`. Cancel
  answers 200 with `CancellationResponse` (all `OrderRecord` fields plus `alreadyCancelled`). A non-integer
  `orderId` produces Spring's `MethodArgumentTypeMismatchException` → `VALIDATION_ERROR` (spec 003) with detail
  `Request validation failed: 'orderId' has an invalid type or format.` (DEC-25); `OrderQueryControllerIT` asserts
  this exact detail for `GET /api/v1/orders/abc` and `POST /api/v1/orders/abc/cancel`. The path variable must be
  named `orderId`.
- Path ids are validated with Bean Validation (DEC-26): `@PathVariable @Positive long orderId` in the three
  `OrderQueryController` routes and `@PathVariable @Positive int userId` in `InternalOrderHistoryController`. 0 or
  negative → 400 `VALIDATION_ERROR` `Request validation failed: '<orderId|userId>' must be greater than 0.` with the
  `errors` list. `OrderQueryControllerIT` asserts it for `GET /api/v1/orders/0` and `POST /api/v1/orders/0/cancel`,
  and `InternalOrderHistoryControllerIT` for `GET /api/internal/users/0/orders` (with `X-Role: ADMIN`).
- `InternalOrderHistoryController` (`rest.internal`, `@ConditionalOnProperty`): `GET
  /api/internal/users/{userId}/orders`, same parameters and response as the user history.
- `OrderRecordResponse` follows the contract schema `OrderRecord`: `quantity` null for cash movements; `amount` =
  quantity × price (null without price) for trades, size for cash movements.

## CU-04.08: cancellation versus forced execution

Spec 009 delivers the forced-execution tool (I-02) and **must** follow this protocol: lock the user row, then
`UPDATE orders SET status = 'FILLED' ... WHERE id = :orderId AND status = 'NEW'`. In this spec the BDD step
`the administrator forces its execution at the same time` is implemented by `support/ForcedExecutionSimulator` (test sources, shared by the IT and the BDD step),
which runs that exact protocol in its own transaction (JdbcClient + `TransactionTemplate`). Both operations start
together (`CountDownLatch`). The assertions:
- exactly one of them changed the row (the simulator reports how many rows its `UPDATE` affected);
- final status `CANCELLED` ⇔ the cancel response was 200 with `alreadyCancelled: false`; final status `FILLED` ⇔ the
  cancel response was 409 `ORDER_NOT_CANCELLABLE`.

Spec 009 replaces the simulator with a call to the real endpoint in the same step definition (no `.feature` change).

## BDD steps (`bdd/steps/OrderHistorySteps.java`, `bdd/steps/CancellationSteps.java`)

- `user N cancels order <id>` / `user N cancelled order <id>` (Given) / `user N cancels that order` (the order of the
  last `placed an order` step).
- `the cancelled order has status "<S>" and was [not] already cancelled`; `order <id> has status "<S>"` (SQL).
- `user N requests their order history` / `... with status "<S>"`; `user N requests order <id>` / `that order`;
  `the administrator requests the order history of user N`; `user N pages through their order history with a page
  size of <n>`.
- `the history order ids are "<ids>"`; `the history entry of order <id> has ticker ..., side ..., status ... and
  timestamp ...`; `there is no next history page`; `the history is empty`; `the history pages contain the order ids
  "<a>", "<b>" and "<c>"`; `the order record has status "<S>" and rejection "<code>" with message "<msg>"` /
  `and no rejection`.
- Reuse: `user N placed an order:`, `the available cash of user N is`, `user N can sell N shares of` (spec 005).

## Tests

| CU | Unit | Integration |
|---|---|---|
| 04.01, 04.02, 04.03 | `CancelOrderServiceTest` (fakes: NEW → cancelled; already cancelled → no update, metric `true`) | `JdbcOrderLedgerRepositoryIT` (`cancelIfNew` 1 then 0 rows; available 717500.00 after cancelling 7) |
| 04.04, 04.05 | `CancelOrderServiceTest` (exception with status) | — |
| 04.06, 04.07 | `CancelOrderServiceTest` (not found) | `JdbcOrderLedgerRepositoryIT` (`findRecord` with another user → empty) |
| 04.08 | — | `CancelVersusExecutionIT` (simulator + cancel use case concurrently, 20 repetitions, never both applied) |
| 07.01, 07.02, 07.08 | — | `JdbcOrderLedgerRepositoryIT` (ids and order; filters) |
| 07.03, 07.04 | `OrderRecordTest` (rejection present/absent; amount for trades and cash) | — |
| 07.05 | `GetOrderServiceTest` | — |
| 07.06 | — | `InternalOrderHistoryControllerIT` |
| 07.07 | `HistoryCursorTest`, `GetOrderHistoryServiceTest` (next cursor) | `JdbcOrderLedgerRepositoryIT` (pages 5/5/1) |
| 07.09 | `GetOrderHistoryServiceTest` (empty page, null cursor) | — |

Also (no CU tag): `OrderHistoryCriteriaTest` (invalid status and cursor messages); `CancellationLoadIT` (M-07): 50
simultaneous cancellations of order 7 → exactly one `alreadyCancelled: false`, available cash 717500.00.

## Contract, features and HTTP examples

- `cp specs/008-cancellation-history/contracts/openapi.yaml src/main/resources/static/openapi/openapi.yaml`
- `cp specs/008-cancellation-history/features/*.feature src/test/resources/features/`
- Append `CU-04` and `CU-07` to `src/test/resources/traceability/scope.txt`.
- `http/008-cancellation-history.http`: history, filter, next page, single order, cancel, cancel again, cancel a
  FILLED order, administrator history.
