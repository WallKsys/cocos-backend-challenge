# Implementation Plan: Consolidation (spec 011)

**Spec**: `spec.md` · **RFC**: TEC-38 · **Constitution**: v1.5.0 (Principle III, refactor without behavior change)

## Summary

Merge the production classes that specs 001 to 010 left split by spec: persistence by table, inbound ports and
controllers by resource, configuration by purpose. Every merge **moves** code; it does not rewrite it. The suite of
v1.0.0 is the specification: it stays green in every slice, it changes only by the mechanical adjustments of
§ Test adjustments, and `preservation.sh` proves in every PR that the SQL and the executed tests are those of the
baseline (§ Preservation evidence).

From 165 production files to **103** (§ Target structure). The ArchUnit rules, traceability, `.feature` files,
published contract, migrations, `pom.xml`, `docs/` and `README.md` do not change.

## Rules for every slice

1. **No behavior change** (FR-001). If a step seems to need one, **stop and report**.
2. **Move, do not rewrite.** Methods, SQL constants, Javadoc and comments move verbatim to their new class. While
   moving, change only: the types and names that this plan renames, imports, and private helpers that become
   duplicates inside one class (keep one; § Shared row helpers is the only case where three mappers share helpers).
3. **Names.**
    - A merged class takes the name that § Target structure gives it. Replaced files are deleted in the same slice.
    - A type that is nested in another **keeps its simple name** (`PlaceOrderUseCase.PlaceOrderCommand`,
      `Portfolio.PortfolioWarning`), so the code that uses it changes only its imports.
    - Port methods keep their names. The only renames are those listed in this plan (§ Method and constant
      renames).
    - SQL constants keep their names unless two collide in the merged class; then this plan gives the new name. The
      **text** of a constant never changes (`preservation.sh` fails otherwise).
4. SQL constants and SQL builder methods stay **package-private** (DAT-12): `PlanGuardIT` reads them.
5. No dependency added, no `pom.xml` change, no `@Component`/`@Service` in `application` (beans stay in
   `infrastructure.config`).
6. Untouchable: `src/test/java/com/cocos/challenge/architecture/`, `src/test/java/com/cocos/challenge/traceability/`,
   `src/test/resources/`, `src/main/resources/` (contract, migrations, configuration), `docs/`, `README.md`.

## Target structure

Files of `src/main/java/com/cocos/challenge` after slice 011-k. Packages not listed do not change (bootstrap class 1,
`domain.service` 3, `domain.exception` 7, REST `error` 3, `format` 3, `idempotency` 3, `identity` 2, `tracing` 2,
`adapter.out.metrics` 1).

| Package | Before | After | Files after |
|---|---:|---:|---|
| `domain.model` | 37 | 32 | the same, minus `InstrumentPage`, `OrderHistoryPage` (→ `CursorPage`), `OrderEvaluation` (→ `OrderEvaluator`), `PortfolioWarning`, `PortfolioWarningCode` (→ `Portfolio`), `RankedInstrument` (→ `InstrumentRepository`); plus `CursorPage` |
| `application.port.in` | 18 | 7 | `PlaceOrderUseCase`, `CancelOrderUseCase`, `OrderQuery`, `CashMovementUseCase`, `GetPortfolioQuery`, `SearchInstrumentsQuery`, `InternalToolsUseCase` |
| `application.port.out` | 19 | 8 | `AccountRepository`, `AdminOrderRepository`, `BusinessMetrics`, `InstrumentRepository`, `LedgerReader`, `MarketDataRepository`, `OrderLedgerRepository`, `SeedDataRestorer` |
| `application.service` | 13 | 8 | `CancelOrderService`, `CashMovementService`, `GetPortfolioService`, `IdempotentMutationGuard`, `InternalToolsService`, `OrderQueryService`, `PlaceOrderService`, `SearchInstrumentsService` |
| `rest.order` | 7 | 5 | `OrderController`, `OrderResponse`, `OrderRecordResponse`, `CancellationResponse`, `OrderHistoryPageResponse` |
| `rest.cash` | 3 | 2 | `CashMovementController`, `CashMovementResponse` |
| `rest.portfolio` | 5 | 2 | `PortfolioController`, `PortfolioResponse` |
| `rest.instrument` | 3 | 2 | `InstrumentSearchController`, `InstrumentPageResponse` |
| `rest.internal` | 10 | 2 | `AdminRoleFilter`, `InternalToolsController` |
| `adapter.out.persistence` | 14 | 6 | `JdbcAccountRepository`, `JdbcInstrumentRepository`, `JdbcLedgerReader`, `JdbcMarketDataRepository`, `JdbcOrderLedgerRepository`, `JdbcSeedDataRestorer` |
| `infrastructure.config` | 11 | 4 | `ApplicationConfiguration`, `InternalToolsConfiguration`, `ObservabilityConfiguration`, `WebConfiguration` |
| **Total** | **165** | **103** | |

## Persistence and outbound ports (audit P1)

One adapter per table. Each port below is the **final** one; the slice that creates it says which methods arrive
when.

### `users` — `AccountRepository` / `JdbcAccountRepository` (slice 011-b)

```java
/** The users table. */
public interface AccountRepository {

    /** Reads the user row with SELECT ... FOR UPDATE: the lock lasts until the transaction ends (TEC-16). */
    Optional<Account> lock(UserId userId);

    Optional<Account> find(UserId userId);

    CreatedUser create(String email);
}
```

`JdbcAccountRepository` takes `LOCK_SQL` (from `JdbcAccountLock`), `FIND_ACCOUNT_SQL` (from `JdbcAccountReader`)
and `INSERT_SQL` (from `JdbcUserWriter`); `lock` and `find` share one row mapper. Replaces `AccountLock`,
`AccountReader`, `UserWriter` and their three adapters.

### `instruments` — `InstrumentRepository` / `JdbcInstrumentRepository` (slice 011-c)

```java
/** The instruments table: lookup, the ARS cash instrument and the ranked search (spec 004). */
public interface InstrumentRepository {

    Optional<Instrument> findById(InstrumentId instrumentId);

    Optional<InstrumentId> findCashInstrument();

    List<RankedInstrument> search(InstrumentSearchCriteria criteria, int maxResults);

    record RankedInstrument(int rank, InstrumentSummary instrument) {
    }
}
```

`JdbcInstrumentRepository` takes `FIND_BY_ID_SQL`, `FIND_CASH_INSTRUMENT_SQL` (from `JdbcCashMovementRepository`)
and every constant and method of `JdbcInstrumentSearchRepository`, including the package-private `sql(criteria)`
builder. Replaces `InstrumentSearchRepository`, `JdbcInstrumentSearchRepository`, `domain.model.RankedInstrument`,
and `findCashInstrument()` of `CashMovementRepository`. `CashMovementService` gets `InstrumentRepository` as a new
constructor parameter, right after its `CashMovementRepository`.

### `marketdata` — `MarketDataRepository` / `JdbcMarketDataRepository` (slice 011-d)

```java
public interface MarketDataRepository {

    Optional<MarketQuote> latestQuote(InstrumentId instrumentId);

    // the default latestQuotes(Set<InstrumentId>) method stays exactly as it is

    StoredMarketData upsert(InstrumentId instrumentId, Money close, Money previousClose, LocalDate date);

    record StoredMarketData(LocalDate date, Money close, Money previousClose) {
    }
}
```

`JdbcMarketDataRepository` takes `UPDATE_LATEST_SQL` and `INSERT_SQL` of `JdbcMarketDataWriter`; the two row
mappers stay separate (they read different columns). Replaces `MarketDataWriter` and `JdbcMarketDataWriter`.

### Aggregations over `orders` — `LedgerReader` / `JdbcLedgerReader` (slice 011-d)

```java
/** Aggregations over the orders ledger, computed in SQL (TEC-17). */
public interface LedgerReader {

    CashBalance cashBalance(UserId userId);

    ShareBalance shareBalance(UserId userId, InstrumentId instrumentId);

    List<OpenPosition> openPositions(UserId userId);

    Map<InstrumentId, List<Fill>> fills(UserId userId, Set<InstrumentId> instrumentIds);

    record OpenPosition(InstrumentId id, String ticker, String name, long quantity, long reservedQuantity) {
    }
}
```

`JdbcLedgerReader` takes `OPEN_POSITIONS_SQL` and `FILLS_SQL` of `JdbcPortfolioReader`. Replaces `PortfolioReader`,
`OpenPosition` and `JdbcPortfolioReader`. `GetPortfolioService` loses its `PortfolioReader` parameter and calls
`ledger` instead.

### Rows of `orders` — `OrderLedgerRepository` and `AdminOrderRepository` / `JdbcOrderLedgerRepository` (slices 011-e and 011-f)

```java
/** Rows of the orders table: orders, cash movements and their records (Constitution I). */
public interface OrderLedgerRepository {

    Optional<IdempotencyRecord> findIdempotencyRecord(UserId userId, String key);   // 011-e

    Order findById(long id);                                                         // 011-e

    Order save(NewOrder order);                                                      // 011-e

    CashMovement findCashMovement(long id);                                          // 011-f

    CashMovement save(NewCashMovement movement);                                     // 011-f

    Optional<OrderRecord> findRecord(UserId userId, long orderId);

    Optional<OrderRecord> findRecordById(long orderId);

    boolean cancelIfNew(UserId userId, long orderId);

    List<OrderRecord> history(UserId userId, OrderHistoryCriteria criteria, int maxResults);

    record IdempotencyRecord(long orderId, OrderSide side, RequestFingerprint fingerprint) {   // 011-e
    }

    record NewOrder(...) {          // 011-e, components unchanged
    }

    record NewCashMovement(...) {   // 011-f, components unchanged
    }
}
```

- **Slice 011-e** moves `OrderRepository` and `JdbcOrderRepository` into them. Constants: `FIND_IDEMPOTENCY_RECORD_SQL`
  keeps its name; `FIND_BY_ID_SQL` becomes `FIND_ORDER_SQL`; `INSERT_SQL` becomes `INSERT_ORDER_SQL`.
- **Slice 011-f** moves `CashMovementRepository` and `JdbcCashMovementRepository` (their `findById` becomes
  `findCashMovement`; `FIND_BY_ID_SQL` becomes `FIND_CASH_MOVEMENT_SQL`; `INSERT_SQL` becomes
  `INSERT_CASH_MOVEMENT_SQL`), and `JdbcAdminOrderRepository` (constants unchanged). **`AdminOrderRepository` stays a
  separate port** (TEC-38: the main port does not offer demo-only destructive operations); after 011-f
  `JdbcOrderLedgerRepository implements OrderLedgerRepository, AdminOrderRepository`, and its bean is declared with
  the concrete return type `JdbcOrderLedgerRepository`, so that the one instance serves both ports. The
  `adminOrderRepository` bean is removed.

**Shared row helpers (slice 011-f).** After the move, `mapOrder`, `mapMovement` and `mapRecord` of
`JdbcOrderLedgerRepository` repeat the same conversions. Replace the repetitions with these private static helpers,
and change nothing else in the mappers:

```java
private static Rejection rejection(ResultSet rs) throws SQLException      // null when rejection_code is null
private static Money money(BigDecimal amount)                             // null-safe, already in JdbcOrderRepository
private static BigDecimal amount(Money money)                             // null-safe, already in JdbcOrderRepository
private static Instant createdAt(ResultSet rs) throws SQLException        // datetime as OffsetDateTime → Instant
private static RequestFingerprint fingerprint(ResultSet rs) throws SQLException   // null-safe
```

### Unchanged ports

`AdminOrderRepository` (methods), `SeedDataRestorer` / `JdbcSeedDataRestorer` and `BusinessMetrics` do not change.

## Inbound ports and services (audit P3)

Final inbound ports. Commands and results are nested records that keep their simple names; their components do not
change.

```java
public interface PlaceOrderUseCase {
    PlaceOrderResult place(PlaceOrderCommand command);
    record PlaceOrderCommand(UserId userId, String idempotencyKey, OrderRequest request) { }
    record PlaceOrderResult(Order order, boolean replayed) { }
}

public interface CancelOrderUseCase {
    CancellationResult cancel(UserId userId, long orderId);
    record CancellationResult(OrderRecord order, boolean alreadyCancelled) { }
}

public interface OrderQuery {
    OrderRecord get(UserId userId, long orderId);
    CursorPage<OrderRecord, HistoryCursor> list(UserId userId, OrderHistoryCriteria criteria);
}

public interface CashMovementUseCase {
    CashMovementResult deposit(CashMovementCommand command);
    CashMovementResult withdraw(CashMovementCommand command);
    record CashMovementCommand(UserId userId, String idempotencyKey, CashMovementRequest request) { }
    record CashMovementResult(CashMovement movement, boolean replayed) { }
}

public interface GetPortfolioQuery {                       // unchanged
    Portfolio get(UserId userId);
}

public interface SearchInstrumentsQuery {
    CursorPage<InstrumentSummary, SearchCursor> search(InstrumentSearchCriteria criteria);
}

public interface InternalToolsUseCase {
    CreatedUser createUser(String email);
    void deleteOrder(long orderId);
    OrderRecord executeOrder(long orderId);
    void restoreSeedData();
    MarketDataResult updateMarketData(InstrumentId instrumentId, Money close, Money previousClose);
    record MarketDataResult(
            InstrumentId instrumentId, String ticker, LocalDate date, Money close, Money previousClose) { }
}
```

Services:

- `OrderQueryService implements OrderQuery` (slice 011-g): the bodies of `GetOrderService.get` and
  `GetOrderHistoryService.list`, verbatim, with their annotations. Constructor `(AccountRepository accounts,
  OrderLedgerRepository ledger)`.
- `CashMovementService implements CashMovementUseCase` (slice 011-g): only the `implements` clause and the imports
  change.
- `InternalToolsService implements InternalToolsUseCase` (slice 011-h): the bodies of `CreateUserService.create`,
  `DeleteOrderService.delete`, `ExecuteOrderService.execute`, `RestoreSeedDataService.restore` and
  `UpdateMarketDataService.update`, verbatim, each with its `@Transactional`, under the method names of the port.
  Final constructor, used from the first half of the slice on (unused parameters are simply stored):
  `(AccountRepository accounts, AdminOrderRepository adminOrders, OrderLedgerRepository ledger,
  InstrumentRepository instruments, MarketDataRepository marketData, SeedDataRestorer seedDataRestorer, Clock clock)`.
- `PlaceOrderService` and `CancelOrderService` stay separate classes (TEC-38).

The ArchUnit rules need no change: `OrderQueryService` methods are `@Transactional(readOnly = true)`, and every
public method of `InternalToolsService` is `@Transactional`.

## Idempotent mutation guard (audit P7, slice 011-g)

`IdempotencyLookup` becomes `IdempotentMutationGuard` (same package, `public final`): the shared start of the three
operations that take an `Idempotency-Key`. Cancellation, deletion and forced execution do **not** use it: they have no
key and do not record the lock wait (specs 005, 007 and 010 define which operations record it), and that stays so.

```java
/** Shared start of the operations that take an Idempotency-Key: placement, deposit and withdrawal (specs 005, 007). */
public final class IdempotentMutationGuard {

    public IdempotentMutationGuard(AccountRepository accounts, OrderLedgerRepository orders, BusinessMetrics metrics,
            Clock clock) { ... }

    /** Locks the user row (TEC-16) and records the wait; an unknown user is "User <id> was not found.". */
    public Account lock(UserId userId) {
        long start = System.nanoTime();
        Account account = accounts.lock(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId.value() + " was not found."));
        metrics.accountLockWaited(Duration.ofNanos(System.nanoTime() - start));
        return account;
    }

    /** IdempotencyLookup.find, verbatim. */
    public OptionalLong find(UserId userId, String key, RequestFingerprint fingerprint) { ... }

    /** The current instant truncated to microseconds, the precision of PostgreSQL timestamps. */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
```

Final constructors (one each; the second `PlaceOrderService` constructor disappears):

- `PlaceOrderService(IdempotentMutationGuard guard, OrderLedgerRepository orders, InstrumentRepository instruments,
  MarketDataRepository marketData, LedgerReader ledger, OrderEvaluator evaluator, BusinessMetrics metrics)`
- `CashMovementService(IdempotentMutationGuard guard, OrderLedgerRepository movements, InstrumentRepository
  instruments, LedgerReader ledger, CashMovementEvaluator evaluator, BusinessMetrics metrics)`

Both services call `guard.lock`, `guard.find` and `guard.now()` exactly where they now lock, record the wait, call
`idempotencyLookup.find` and compute the truncated instant. The order of the calls does not change.

## REST (audit P4)

- **`OrderController`** (slice 011-j) absorbs `OrderQueryController`: class-level `@RequestMapping("/api/v1/orders")`,
  `place` with `@PostMapping`, and `list`, `get`, `cancel` with their current annotations. It injects
  `PlaceOrderUseCase`, `OrderQuery` and `CancelOrderUseCase`. `PlaceOrderRequestBody` is nested in it.
- **`CashMovementController`** (slice 011-g injects `CashMovementUseCase`; slice 011-j nests
  `CashMovementRequestBody`).
- **`PortfolioResponse`** nests `CashResponse`, `PositionResponse` and `WarningResponse` (slice 011-j), with their
  current modifiers.
- **`InstrumentPageResponse`** nests `InstrumentResponse` (slice 011-j).
- **`InternalToolsController`** (slice 011-i) absorbs the five internal controllers. Annotations:
  `@RestController` and `@ConditionalOnProperty(name = "cocos.internal-tools.enabled", havingValue = "true")`; no
  class-level `@RequestMapping`: every method keeps its full path and its parameter annotations. It injects
  `InternalToolsUseCase`, `GetPortfolioQuery` and `OrderQuery`. Its methods are named `restoreSeedData`,
  `createUser`, `updateMarketData`, `listOrders`, `deleteOrder`, `executeOrder` and `getPortfolio`. It nests
  `CreateUserBody`, `MarketDataUpdateBody`, `MarketDataView` and `UserView`.
- Top-level responses stay top-level (`OrderResponse`, `OrderRecordResponse`, `CancellationResponse`,
  `OrderHistoryPageResponse`, `CashMovementResponse`, `PortfolioResponse`, `InstrumentPageResponse`): request bodies
  are nested in their controller, child DTOs in their parent DTO.
- The JSON is unchanged: nesting does not change component names. `/v3/api-docs` (generated, not published) may name
  schemas differently; the published contract is the static file.

## Domain (audit P5, slice 011-a)

```java
/** One page of a cursor-paginated list (CM-08): its items and the cursor of the next page, null on the last one. */
public record CursorPage<T, C>(List<T> items, C nextCursor) {
    public CursorPage {
        items = List.copyOf(items);
    }
}
```

- `CursorPage<InstrumentSummary, SearchCursor>` replaces `InstrumentPage`; `CursorPage<OrderRecord, HistoryCursor>`
  replaces `OrderHistoryPage`.
- `OrderEvaluation` is nested in `OrderEvaluator` (`domain.service`), with its Javadoc.
- `PortfolioWarning` and `PortfolioWarningCode` are nested in `Portfolio`.
- `RankedInstrument` moves to `InstrumentRepository` in slice 011-c (it is a projection of the search query, not a
  domain concept).

## Sealed domain exceptions (audit P6, slice 011-a)

```java
public abstract sealed class DomainException extends RuntimeException
        permits IdempotencyConflictException, InvalidRequestException, NotFoundException,
        OrderNotCancellableException, OrderNotExecutableException, SearchTermTooShortException {
```

Each of the six subclasses becomes `public non-sealed class` (Java requires a permitted subclass to be `final`,
`sealed` or `non-sealed`). Not `final`: `ErrorHandlingIT.domainExceptionSubclassUsesTheDomainMapping` throws an
anonymous subclass of `NotFoundException` (`ProbeControllers`, `not-found-subclass`) and expects the mapping of its
parent, a regression guard of spec 003; the `switch` below keeps it, because `case NotFoundException _` matches every
subclass. Neither test file changes. In `ApiExceptionHandler`, the `DOMAIN_CODES` map and its
lookup are replaced by:

```java
    @ExceptionHandler({ApiException.class, DomainException.class})
    ResponseEntity<Object> handleExpectedException(Exception exception, WebRequest request) {
        ApiErrorCode code = exception instanceof ApiException apiException
                ? apiException.code() : domainCode((DomainException) exception);
        return problem(code, code.status(), exception.getMessage(), new HttpHeaders(), request);
    }

    private static ApiErrorCode domainCode(DomainException exception) {
        return switch (exception) {
            case InvalidRequestException _ -> ApiErrorCode.VALIDATION_ERROR;
            case NotFoundException _ -> ApiErrorCode.NOT_FOUND;
            case IdempotencyConflictException _ -> ApiErrorCode.IDEMPOTENCY_CONFLICT;
            case OrderNotCancellableException _ -> ApiErrorCode.ORDER_NOT_CANCELLABLE;
            case OrderNotExecutableException _ -> ApiErrorCode.ORDER_NOT_EXECUTABLE;
            case SearchTermTooShortException _ -> ApiErrorCode.SEARCH_TERM_TOO_SHORT;
        };
    }
```

No `default` branch: a new permitted subclass without its `case` does not compile (SC-004, ARQ-15).

## Configuration (audit P2, slice 011-k)

During slices 011-b to 011-j, edit the bean methods where they are: remove the beans of deleted ports, add each new
bean to the configuration class that held the bean it replaces, and keep the bean method names of the beans that
survive. Slice 011-k then regroups them by purpose:

| Class | Beans |
|---|---|
| `ApplicationConfiguration` | `clock`; the repositories `accountRepository`, `instrumentRepository`, `marketDataRepository`, `orderLedgerRepository` (return type `JdbcOrderLedgerRepository`), `ledgerReader`; `idempotentMutationGuard`; the use cases `placeOrderUseCase`, `cancelOrderUseCase`, `orderQuery`, `cashMovementUseCase`, `getPortfolioQuery`, `searchInstrumentsQuery` |
| `InternalToolsConfiguration` | `seedDataRestorer`, `internalToolsUseCase` |
| `WebConfiguration` | as today, plus the `JsonMapperBuilderCustomizer` bean of `JacksonConfiguration` |
| `ObservabilityConfiguration` | `businessMetrics` (from `MetricsConfiguration`) and `openTelemetryLogbackAppenderInstaller` (from `OpenTelemetryLoggingConfiguration`) |

Each bean method moves verbatim. Deleted: `ClockConfiguration`, `MetricsConfiguration`,
`OpenTelemetryLoggingConfiguration`, `JacksonConfiguration`, `OrderConfiguration`, `OrderQueryConfiguration`,
`PortfolioConfiguration`, `CashMovementConfiguration`, `InstrumentSearchConfiguration`.

## Method and constant renames

| Where | Before | After | Slice |
|---|---|---|---|
| `OrderLedgerRepository` (from `CashMovementRepository`) | `findById(long)` | `findCashMovement(long)` | 011-f |
| `JdbcOrderLedgerRepository` (from `JdbcOrderRepository`) | `FIND_BY_ID_SQL`, `INSERT_SQL` | `FIND_ORDER_SQL`, `INSERT_ORDER_SQL` | 011-e |
| `JdbcOrderLedgerRepository` (from `JdbcCashMovementRepository`) | `FIND_BY_ID_SQL`, `INSERT_SQL` | `FIND_CASH_MOVEMENT_SQL`, `INSERT_CASH_MOVEMENT_SQL` | 011-f |
| `InternalToolsUseCase` | `create`, `delete`, `execute`, `restore`, `update` | `createUser`, `deleteOrder`, `executeOrder`, `restoreSeedData`, `updateMarketData` | 011-h |

## Test adjustments

The existing suite is the specification of this spec. A slice changes a test file **only if its task names the
file**, and only with these mechanical adjustments:

- **(a) Imports** and qualified names of types that this plan moves, nests or renames.
- **(b) Types and class names**: the declared type of a field, variable, mock, spy or fake that stands for a merged
  port, adapter or use case, and the class in its `new`/`mock(...)`.
- **(c) Constructor calls** of production classes whose constructor this plan changes: the arguments in the new order;
  a collaborator the test does not use gets `mock(...)` (or the fake the test already passes for other ports).
- **(d) Method names** that this plan renames, wherever they are called (stubs, verifications, assertions), with the
  same arguments and expected values; and calls to a method that this plan moves to another class go to an instance of
  that class, created like the test creates its other adapters.
- **(e) Hand-written fakes** (`implements <port>`): the `implements` clause, and one stub per method the port gains,
  throwing `UnsupportedOperationException`. Existing fake methods change only by (d).
- **(f) Merged collaborators**: when two fields of a test stand for ports that become one, keep the first and use it
  in place of the second (for example two `@MockitoSpyBean` fields that would wrap the same bean); for an `InOrder`
  verification of a call that moves to another mock, add that mock to `inOrder(...)` and verify the call on it, in
  the same position.
- **(g) Renamed test classes**: the class and its file take the name that the task gives (git must see a rename).
  Test classes are **never merged**: their methods, fields, tags and helpers stay where they are.

Not allowed: changing an expected value, an assertion or its matcher, test data, a test method name, a `@Tag`, or
adding, removing, disabling or skipping a test. `preservation.sh` fails if a test method disappears or appears. Any
other change: **stop and report**.

In the PR, the section "Test adjustments" lists every changed test file with the letters it applied.

## Preservation evidence

The refactor has no red run (Constitution III, v1.5.0): no test is new, and the existing ones must pass at every step.
The evidence that behavior did not change replaces it:

1. `./mvnw clean verify` green (`clean` is required: `target/` keeps classes and reports of deleted sources).
2. `specs/011-consolidation/preservation.sh`, which compares with `specs/011-consolidation/baseline/` (recorded from
   v1.0.0, commit `66c8b63`):
    - **SQL texts**: every `String` constant of the persistence adapters, read with `javap -constants`, must be the
      same set (41 texts). Names may change; texts may not.
    - **Executed tests**: the names of the 672 test cases of the Surefire and Failsafe reports (275 unit, 258
      integration, 139 BDD), with repetitions. Classes may be renamed; methods may not change.
   If it reports a difference, **stop and report**.

The PR description, section "Test-first evidence", says "Refactor without behavior change (spec 011): no red run" and
pastes the output of `preservation.sh` and the final `Tests run` lines of Surefire and Failsafe.

Commits: (1) the test adjustments that the tasks prescribe, (2) the production change. The tests commit does not
compile alone; that is expected, as in a slice whose skeletons come in the second commit.

## Slices

| Slice | Content | Audit | Estimate | Manual review |
|---|---|---|---:|---|
| 011-a | Sealed domain exceptions; `CursorPage`; nested `OrderEvaluation` and `PortfolioWarning` | P6, P5 | ~200 | — |
| 011-b | `users`: `AccountRepository` / `JdbcAccountRepository` (split b1/b2 if over 400) | P1 | ~300 | required |
| 011-c | `instruments`: `InstrumentRepository` / `JdbcInstrumentRepository`, cash instrument, `RankedInstrument` | P1, P5 | ~320 | — |
| 011-d | `marketdata` and ledger aggregations: writer into `MarketDataRepository`, `PortfolioReader` into `LedgerReader` (split d1/d2 if over 400) | P1 | ~340 | — |
| 011-e | `orders` rows (1/2): `OrderRepository` into `OrderLedgerRepository` | P1 | ~330 | required |
| 011-f | `orders` rows (2/2): cash movements and admin operations into `JdbcOrderLedgerRepository`; shared row helpers | P1 | ~320 | required |
| 011-g | Public inbound ports; `IdempotentMutationGuard` (split g1/g2 if over 400) | P3, P7 | ~400 | required |
| 011-h | `InternalToolsUseCase` / `InternalToolsService` (split h1/h2 if over 400) | P3 | ~410 | required |
| 011-i | `InternalToolsController` and its DTOs | P4 | ~330 | — |
| 011-j | `OrderController`; nested DTOs of the public API | P4 | ~260 | — |
| 011-k | Configuration in four classes; `-Pvolume` run (split k1/k2 if over 400) | P2 | ~410 | — |

Estimates count tests (renames count only their changed lines). A slice that exceeds 400 lines and has no predefined
split: **stop and report**.
