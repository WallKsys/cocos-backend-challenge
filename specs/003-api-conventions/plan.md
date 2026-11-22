# Implementation Plan: API conventions

**Branch**: `003-api-conventions-<slice>` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

## Summary

Build the cross-cutting API pieces: RFC 9457 error handling with a closed catalog, identity and admin handling,
`Idempotency-Key` handling, money/percentage/timestamp formats, correlation id, the `BusinessMetrics` port with a
Micrometer adapter, and the published OpenAPI contract with Swagger UI. Verified with test-only probe endpoints.

## Technical Context

**Language/Version**: Java 25 · Spring Boot 4.1.3 · Jackson 3 (`tools.jackson.*`)

**New dependency**: `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.1` (compatible with Boot 4.1.x)

**Testing**: unit tests (domain building blocks, metrics adapter with `SimpleMeterRegistry`), integration tests
with probe controllers and `RestTestClient`

**Verified facts** (checked against Boot 4.1.3 / Jackson 3.1.8 / springdoc 3.2.1 on 2026-11-21):

- Jackson 3 serializes `Instant` as ISO 8601 by default: `"2026-11-21T14:36:00Z"`.
- Jackson 3 **accepts** a JSON number for a `String` field by default (`925.85` → `"925.85"`). It must be disabled
  with the coercion configuration of § Jackson.
- The customizer interface is `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`.
- springdoc disables Swagger UI when `springdoc.api-docs.enabled=false`. Keep api-docs enabled and point the UI to
  the published file with `springdoc.swagger-ui.url`. The generated `/v3/api-docs` still exists; it is **not** the
  contract.

## Constitution Check

| Principle | How this spec complies |
|---|---|
| II Spec is law | Error catalog, messages and formats are fixed in this plan |
| III Test-first | Every slice starts with its tests (probe controllers in `src/test`) |
| V Hexagonal | Domain building blocks are pure Java; `BusinessMetrics` is an outbound port; Micrometer only in infrastructure |
| VIII Money | `Money` (BigDecimal, scale 2, HALF_UP); strings in JSON |
| IX API conventions | Implemented here |
| X PR size | Five slices |

## Project Structure

```text
src/main/java/com/cocos/challenge/
├── domain/
│   ├── model/
│   │   ├── UserId.java                 record UserId(long value)
│   │   ├── Money.java                  record Money(BigDecimal amount)
│   │   ├── OrderSide.java              enum BUY, SELL, CASH_IN, CASH_OUT
│   │   ├── OrderType.java              enum MARKET, LIMIT
│   │   ├── OrderStatus.java            enum NEW, FILLED, REJECTED, CANCELLED
│   │   ├── RejectionReason.java        enum INSUFFICIENT_FUNDS, INSUFFICIENT_SHARES, AMOUNT_BELOW_PRICE, NO_MARKET_PRICE
│   │   └── RequestFingerprint.java     record RequestFingerprint(String hex)
│   └── exception/
│       ├── DomainException.java
│       ├── InvalidRequestException.java
│       ├── NotFoundException.java
│       ├── IdempotencyConflictException.java
│       └── OrderNotCancellableException.java
├── application/port/out/
│   └── BusinessMetrics.java
└── infrastructure/
    ├── adapter/in/rest/                  (subpackages by concern; no "support" grab-bag)
    │   ├── error/ApiErrorCode.java
    │   ├── error/ApiException.java
    │   ├── error/ApiExceptionHandler.java
    │   ├── identity/RequesterId.java                      (annotation)
    │   ├── identity/RequesterIdArgumentResolver.java
    │   ├── idempotency/IdempotencyKeyHeader.java          (annotation)
    │   ├── idempotency/IdempotencyKeyArgumentResolver.java
    │   ├── idempotency/IdempotentResponses.java
    │   ├── format/ApiDecimals.java
    │   ├── format/DecimalString.java                      (constraint annotation)
    │   ├── format/DecimalStringValidator.java
    │   ├── tracing/RequestIdFilter.java
    │   └── internal/AdminRoleInterceptor.java
    ├── adapter/out/metrics/MicrometerBusinessMetrics.java
    └── config/
        ├── WebConfiguration.java       registers the two argument resolvers and the admin interceptor
        ├── JacksonConfiguration.java   § Jackson
        ├── MetricsConfiguration.java   BusinessMetrics bean
        └── ClockConfiguration.java     java.time.Clock bean (Clock.systemUTC())

src/main/resources/static/openapi/openapi.yaml        copy of specs/003-api-conventions/contracts/openapi.yaml

src/test/java/com/cocos/challenge/
├── domain/model/{UserIdTest, MoneyTest, RequestFingerprintTest}.java
├── infrastructure/adapter/out/metrics/MicrometerBusinessMetricsTest.java
├── infrastructure/adapter/in/rest/format/{ApiDecimalsTest, DecimalStringValidatorTest}.java
├── conventions/
│   ├── ProbeControllers.java            @TestConfiguration with the probe controllers (§ Probe endpoints)
│   ├── ErrorHandlingIT.java
│   ├── IdentityAndAdminIT.java
│   ├── InternalToolsDisabledIT.java
│   ├── IdempotencyKeyIT.java
│   ├── FormatsIT.java
│   └── RequestIdIT.java
├── contract/
│   ├── PublishedContractIsLatestSpecContractTest.java   (unit)
│   └── PublishedContractMatchesRoutesIT.java
└── architecture/InternalToolsArchitectureTest.java      (new rules; ArchitectureTest stays untouched)
```

## Domain building blocks

| Type | Rules |
|---|---|
| `UserId(long value)` | `1 <= value <= Integer.MAX_VALUE` (the `users.id` column is `INT`); otherwise `InvalidRequestException`. |
| `Money(BigDecimal amount)` | Not null. The canonical constructor normalizes to scale 2 with `RoundingMode.HALF_UP`. Constants/methods: `ZERO`, `of(String)`, `of(BigDecimal)`, `plus(Money)`, `minus(Money)`, `times(BigDecimal factor)` (result rounded HALF_UP to scale 2), `isNegative()`, `isGreaterThan(Money)`, `compareTo`. Because of normalization, `Money.of("925.8").equals(Money.of("925.80"))` is true. |
| `RequestFingerprint(String hex)` | `of(String canonicalRequest)` = SHA-256 (UTF-8) as 64 lowercase hex chars. Test vector: `of("abc").hex()` = `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`. The constructor rejects anything that is not 64 lowercase hex chars. |
| Enums | Exactly the values listed in § Project Structure; names match the database values. |

## Domain exceptions

All extend `DomainException` (abstract, extends `RuntimeException`). Messages are English and exactly as below.

| Exception | Constructor | `getMessage()` |
|---|---|---|
| `InvalidRequestException` | `(String detail)` | the detail |
| `NotFoundException` | `(String detail)` | the detail (for example `Order 1234 was not found.`) |
| `IdempotencyConflictException` | `(String idempotencyKey)` | `Idempotency key '<key>' was already used for a different request.` |
| `OrderNotCancellableException` | `(long orderId, OrderStatus status)` | `Order <id> cannot be cancelled because its status is <STATUS>. Only NEW orders can be cancelled.` |

## Error catalog

`type` = `https://api.cocos.example/errors/` + the slug. Content type `application/problem+json`. Every error
response includes `type`, `title`, `status`, `code`, `detail`, `instance` (the request path) and the
`X-Request-Id` header.

| `code` | HTTP | `title` | slug |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Validation error | `validation-error` |
| `MISSING_IDEMPOTENCY_KEY` | 400 | Missing idempotency key | `missing-idempotency-key` |
| `SEARCH_TERM_TOO_SHORT` | 400 | Search term too short | `search-term-too-short` |
| `NOT_FOUND` | 404 | Resource not found | `not-found` |
| `METHOD_NOT_ALLOWED` | 405 | Method not allowed | `method-not-allowed` |
| `NOT_ACCEPTABLE` | 406 | Not acceptable | `not-acceptable` |
| `ORDER_NOT_CANCELLABLE` | 409 | Order not cancellable | `order-not-cancellable` |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Unsupported media type | `unsupported-media-type` |
| `IDEMPOTENCY_CONFLICT` | 422 | Idempotency key conflict | `idempotency-conflict` |
| `REQUEST_ERROR` | other 4xx | Request error | `request-error` |
| `INTERNAL_ERROR` | 500 | Internal error | `internal-error` |

`ApiErrorCode` is an enum with these values and their HTTP status, title and slug. `ApiException(ApiErrorCode code,
String detail)` is the runtime exception the REST adapter throws for its own errors (missing headers, admin guard,
search term length in spec 004).

### Exception mapping (`ApiExceptionHandler`)

`@RestControllerAdvice` extending `ResponseEntityExceptionHandler`. Override `handleExceptionInternal` so that
Spring MVC's own exceptions also get the `code` property and the fixed details below. The internal message of an
exception is **never** copied into the response unless the table says so.

| Exception | Code | `detail` |
|---|---|---|
| `ApiException` | its code | its detail |
| `InvalidRequestException` | `VALIDATION_ERROR` | exception message |
| `NotFoundException` | `NOT_FOUND` | exception message |
| `IdempotencyConflictException` | `IDEMPOTENCY_CONFLICT` | exception message |
| `OrderNotCancellableException` | `ORDER_NOT_CANCELLABLE` | exception message |
| `MethodArgumentNotValidException`, `HandlerMethodValidationException` | `VALIDATION_ERROR` | `Request validation failed: '<field>' <message>[; '<field>' <message>...].` plus `errors` (§ Validation) |
| `HttpMessageNotReadableException` caused by `tools.jackson.databind.exc.UnrecognizedPropertyException` (DEC-24) | `VALIDATION_ERROR` | `Request validation failed: '<property>' is not a recognized field.` plus `errors` with that field and message `is not a recognized field` |
| `HttpMessageNotReadableException` caused by any other `tools.jackson.databind.exc.MismatchedInputException` with a field path | `VALIDATION_ERROR` | `Request validation failed: '<path>' has an invalid type or format.` plus `errors` with that field |
| other `HttpMessageNotReadableException` | `VALIDATION_ERROR` | `Malformed JSON request body.` |
| `MethodArgumentTypeMismatchException` | `VALIDATION_ERROR` | `Request validation failed: '<name>' has an invalid type or format.` (DEC-25) |
| `MissingServletRequestParameterException` | `VALIDATION_ERROR` | `Request validation failed: '<name>' is required.` (DEC-25) |
| `NoResourceFoundException` | `NOT_FOUND` | `Resource not found.` |
| `HttpRequestMethodNotSupportedException` | `METHOD_NOT_ALLOWED` | `Method <METHOD> is not supported for this resource.` |
| `HttpMediaTypeNotAcceptableException` | `NOT_ACCEPTABLE` | `The requested media type is not supported.` |
| `HttpMediaTypeNotSupportedException` | `UNSUPPORTED_MEDIA_TYPE` | `Content type '<type>' is not supported.` |
| any other exception handled by `ResponseEntityExceptionHandler` | `REQUEST_ERROR` (4xx) or `INTERNAL_ERROR` (5xx) | Spring's default detail for 4xx; `An unexpected error occurred.` for 5xx |
| any other `Exception` | `INTERNAL_ERROR` | `An unexpected error occurred.` — log the exception at ERROR level with the stack trace |

### Validation

- `errors` is a list of `{ "field": "<name>", "message": "<message>" }` in a deterministic order (sorted by field,
  then message).
- Messages are English: configure a fixed locale (§ Configuration). Custom constraints define their own English
  messages.

## Identity and admin

- `@RequesterId` (target: parameter) marks a controller parameter of type `UserId`.
  `RequesterIdArgumentResolver` reads `X-User-Id`: trims it; missing or blank → `ApiException(VALIDATION_ERROR,
  "The 'X-User-Id' header is required.")`; not matching `^\d+$`, zero, or greater than 2147483647 →
  `ApiException(VALIDATION_ERROR, "The 'X-User-Id' header must be a positive integer.")`.
- `AdminRoleInterceptor` is registered for `/api/internal/**`. If the header `X-Role` is not exactly `ADMIN`, it
  throws `ApiException(NOT_FOUND, "Resource not found.")` — identical to a missing route.
- Internal controllers are annotated with
  `@ConditionalOnProperty(name = "cocos.internal-tools.enabled", havingValue = "true")`: when disabled they do not
  exist and Spring answers `NoResourceFoundException` → `NOT_FOUND` `Resource not found.`
- `InternalToolsArchitectureTest` (new file, `@ArchTag("unit")`, `allowEmptyShould(true)`):
  1. classes in `..adapter.in.rest.internal..` annotated with `@RestController` must be annotated with
     `@ConditionalOnProperty`;
  2. classes annotated with `@RestController` outside `..adapter.in.rest.internal..` must not have request mappings
     whose path starts with `/api/internal` — implement by checking the class-level `@RequestMapping` value.

## Idempotency key

- `@IdempotencyKeyHeader` (target: parameter, type `String`). `IdempotencyKeyArgumentResolver` reads
  `Idempotency-Key` **without trimming**: missing or empty after `isBlank()` → `ApiException(MISSING_IDEMPOTENCY_KEY,
  "The 'Idempotency-Key' header is required for this operation.")`; not matching `^[\x21-\x7E]{1,255}$` →
  `ApiException(VALIDATION_ERROR, "The 'Idempotency-Key' header must be 1 to 255 visible ASCII characters.")`.
- `IdempotentResponses`: `static <T> ResponseEntity<T> created(T body)` (201) and
  `static <T> ResponseEntity<T> replayed(T body)` (200 with header `Idempotent-Replayed: true`). Specs 005 and 007
  use them.

## Formats

- `ApiDecimals.format(BigDecimal)` → `value.setScale(2, RoundingMode.HALF_UP).toPlainString()`;
  `ApiDecimals.format(Money)`; `ApiDecimals.parse(String)` → `new BigDecimal(text)` (call only after validation).
- `@DecimalString` (Bean Validation constraint on `String` fields): `null` is valid (combine with `@NotNull` when
  required); otherwise must match `^-?\d{1,16}(\.\d{1,2})?$` with no surrounding spaces. Message:
  `must be a decimal string with up to 16 integer digits and at most 2 decimal places`.
- Response DTOs declare money and percentages as `String` and fill them with `ApiDecimals.format`.
- Timestamps in DTOs are `java.time.Instant` (Jackson 3 default produces `...Z`).

### Jackson

`JacksonConfiguration` defines a `JsonMapperBuilderCustomizer` bean
(`org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`) that disables scalar coercions:
JSON numbers and booleans are rejected for `String` fields, and decimals or strings are rejected for integer fields.

```java
@Bean
JsonMapperBuilderCustomizer rejectScalarCoercions() {
    return builder -> builder
            .withCoercionConfig(LogicalType.Textual, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .withCoercionConfig(LogicalType.Integer, config -> config
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.String, CoercionAction.Fail));
}
```

**Why the integer rule is critical** (verified on Jackson 3.1.8): without it, `{"quantity": 1.5}` is silently
truncated to `1`, and `"10"` and `10.0` are accepted. An order for a different quantity than the user sent would be
executed. With the configuration above, all three fail with `MismatchedInputException` → `VALIDATION_ERROR`.

Imports: `tools.jackson.databind.cfg.CoercionAction`, `tools.jackson.databind.cfg.CoercionInputShape`,
`tools.jackson.databind.type.LogicalType`. (Verified: with this configuration `{"amount":925.85}` fails with
`InvalidFormatException`, a subclass of `MismatchedInputException`.)

## Correlation id

`RequestIdFilter` (`rest.tracing`, `OncePerRequestFilter`, highest precedence, registered as a bean in
`WebConfiguration`): take `X-Request-Id` if it matches `^[A-Za-z0-9._-]{1,64}$`, otherwise generate
`UUID.randomUUID().toString()`; put it in the MDC under `requestId`; set the response header **before** calling the
chain; remove the MDC key in `finally`.

## Business metrics

Port `application.port.out.BusinessMetrics` (pure Java; only domain types and `java.time.Duration`):

```java
public interface BusinessMetrics {
    void orderProcessed(OrderSide side, OrderType type, OrderStatus status, RejectionReason rejectionReasonOrNull);
    void orderCancelled(boolean alreadyCancelled);
    void idempotentReplay(OrderSide side);
    void idempotencyConflict();
    void accountLockWaited(Duration waitTime);
}
```

Adapter `MicrometerBusinessMetrics(MeterRegistry)`:

| Method | Meter | Type | Tags |
|---|---|---|---|
| `orderProcessed` | `cocos.orders.processed` | counter | `side`, `type`, `status`, `rejection_reason` (`NONE` when null) |
| `orderCancelled` | `cocos.orders.cancelled` | counter | `already_cancelled` (`true`/`false`) |
| `idempotentReplay` | `cocos.idempotency.replays` | counter | `side` |
| `idempotencyConflict` | `cocos.idempotency.conflicts` | counter | — |
| `accountLockWaited` | `cocos.account.lock.wait` | timer | — (publish percentile histogram) |

Tags are **only** these. Never a user id, order id, idempotency key or ticker (TEC-31).

## Published contract

- Copy `specs/003-api-conventions/contracts/openapi.yaml` to `src/main/resources/static/openapi/openapi.yaml`
  byte-identical (`cp`). Each later spec replaces it with its own cumulative contract.
- `PublishedContractIsLatestSpecContractTest` (unit): find the folder `specs/NNN-*` with the highest `NNN` that
  contains `contracts/openapi.yaml`; assert its bytes equal the published file.
- `PublishedContractMatchesRoutesIT`: start the context with `cocos.internal-tools.enabled=true`; collect every
  `(HTTP method, path pattern)` of `RequestMappingHandlerMapping` whose path starts with `/api/`; parse the
  published YAML (SnakeYAML is on the classpath) and collect `(method, path)` from `paths`; assert both sets are
  equal. In this spec both are empty — the test still runs and must pass.

## Configuration

Add to `src/main/resources/application.yml` (default document, at the end of the `spring:` block and as new
top-level keys):

```yaml
spring:
  web:
    locale: en
    locale-resolver: fixed
  jackson:
    deserialization:
      fail-on-unknown-properties: true      # DEC-24 (verified on Boot 4.1.3)

springdoc:
  swagger-ui:
    url: /openapi/openapi.yaml
```

Add to `pom.xml`, in the runtime dependencies:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.2.1</version>
</dependency>
```

## Probe endpoints (test sources only)

`conventions/ProbeControllers.java` is a `@TestConfiguration` imported **only** by the convention ITs. The probe
controllers are **static nested classes** of `ProbeControllers`, registered with `@Import` on it. Spring Boot's
test type-exclude filter keeps nested classes of a `@TestConfiguration` out of component scanning, so the probes
never appear in other test contexts (in particular, not in `PublishedContractMatchesRoutesIT`). It declares:

- `ConventionsProbeController` at `/test/conventions`:
  - `GET /user` with `@RequesterId UserId` → `{"userId": <id>}`;
  - `POST /idempotent` with `@IdempotencyKeyHeader String` → `{"key": "<key>"}` (201 via `IdempotentResponses.created`);
  - `POST /replayed` → 200 via `IdempotentResponses.replayed`;
  - `POST /validate` with a `@Valid` body `{ "amount": @NotNull @DecimalString String, "quantity": @NotNull @Positive Integer }`;
  - `GET /format` → `{"amounts": ["925.85", "50000.00", "-0.45"], "at": "2026-11-21T14:36:00Z"}` built with
    `ApiDecimals.format` and an `Instant`;
  - `GET /throw/{kind}` throwing `NotFoundException`, `IdempotencyConflictException("k1")`,
    `OrderNotCancellableException(1234, FILLED)`, `InvalidRequestException("...")`, or
    `IllegalStateException("secret internal detail")`;
  - `GET /log` → writes one INFO log line and returns 204 (for the MDC test).
- `InternalProbeController` at `/api/internal/probe`, annotated with `@ConditionalOnProperty` as in production
  → `GET` returns 204. The admin interceptor applies to it because it is registered by path (`/api/internal/**`).

If a probe ever shows up in a context that did not import `ProbeControllers`, stop and report: the exclusion is
not working as described.

## Tests per user story

| Test | Covers |
|---|---|
| `ErrorHandlingIT` | US1 scenarios 1–11 |
| `IdentityAndAdminIT` (`cocos.internal-tools.enabled=true`) | US2 1–3, US3 2–3 |
| `InternalToolsDisabledIT` (default properties) | US3 1 |
| `IdempotencyKeyIT` | US4 1–4 |
| `FormatsIT` | US5 1–4, plus: `{"quantity": 1.5}`, `{"quantity": "10"}` and `{"quantity": 10.0}` on the `/validate` probe → 400 `VALIDATION_ERROR` naming `quantity` |
| `ApiDecimalsTest`, `DecimalStringValidatorTest`, `MoneyTest`, `UserIdTest`, `RequestFingerprintTest` | US5, domain building blocks |
| `RequestIdIT` | US6 1–4 (use a log capture such as Spring Boot's `OutputCaptureExtension`) |
| `MicrometerBusinessMetricsTest` | US7 1–3 |
| `PublishedContractIsLatestSpecContractTest`, `PublishedContractMatchesRoutesIT`, plus a `GET /openapi/openapi.yaml` and `GET /swagger-ui/index.html` check in `FormatsIT` or a dedicated IT | US8 1–3 |

None of these tests carries a CU tag (conventions, not acceptance scenarios).
