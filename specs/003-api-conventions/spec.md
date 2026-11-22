# Feature Specification: API conventions

**Feature Branch**: `003-api-conventions-<slice>` (one branch per PR slice, see `tasks.md`)

**Created**: 2026-11-21

**Status**: Approved for implementation

**Input**: RFC TEC-18 (identity and admin), TEC-19 (errors and messages), TEC-20 (money as text), TEC-22
(contract-first), TEC-27 (correlation id), TEC-31 (business metrics port); DEC-17, DEC-18, DEC-23.

## Context

This spec builds the cross-cutting pieces that every business endpoint of specs 004–009 reuses, so that no
feature invents its own error format, identity handling, money format or idempotency header handling. It adds
**no business endpoint**. Each convention is verified through test-only probe endpoints that exist only in the
test sources.

## User Scenarios & Testing *(mandatory)*

The "users" are API clients (a BFF or frontend) and the developers of later specs.

### User Story 1 - Every error has the same shape, a stable code and an English message (Priority: P1)

**Why this priority**: clients decide what to do from the `code`; people understand the problem from the
`detail` (TEC-19).

**Independent Test**: probe endpoints throw each kind of error; every response is `application/problem+json`
with `type`, `title`, `status`, `code`, `detail` and `instance`, and the `X-Request-Id` header.

**Acceptance Scenarios**:

1. **Given** a request body with an invalid field, **When** it is sent, **Then** the response is 400,
   `code` `VALIDATION_ERROR`, and `errors` lists each invalid field with an English message.
2. **Given** a malformed JSON body, **Then** 400 `VALIDATION_ERROR` with detail `Malformed JSON request body.`
3. **Given** a domain `NotFoundException`, **Then** 404 `NOT_FOUND` with the exception's detail.
4. **Given** an unknown route under `/api/`, **Then** 404 `NOT_FOUND` with detail `Resource not found.`
5. **Given** a domain `IdempotencyConflictException` for key `k1`, **Then** 422 `IDEMPOTENCY_CONFLICT` with detail
   `Idempotency key 'k1' was already used for a different request.`
6. **Given** a domain `OrderNotCancellableException` for order 1234 in status `FILLED`, **Then** 409
   `ORDER_NOT_CANCELLABLE` with detail
   `Order 1234 cannot be cancelled because its status is FILLED. Only NEW orders can be cancelled.`
7. **Given** a domain `InvalidRequestException`, **Then** 400 `VALIDATION_ERROR` with the exception's detail.
8. **Given** an unsupported HTTP method, **Then** 405 `METHOD_NOT_ALLOWED`; an unsupported content type → 415
   `UNSUPPORTED_MEDIA_TYPE`; an unacceptable `Accept` → 406 `NOT_ACCEPTABLE`.
9. **Given** an unexpected exception whose message contains internal details, **Then** 500 `INTERNAL_ERROR` with
   detail `An unexpected error occurred.` and **none** of the internal message in the body.
10. **Given** a request with header `Accept-Language: es`, **When** validation fails, **Then** all messages are
    still in English.
11. **Given** a request body with an unknown field (`{"amount": "1.00", "quantity": 1, "userId": 1}`), **Then**
    400 `VALIDATION_ERROR`, detail `Request validation failed: 'userId' is not a recognized field.` (DEC-24).
12. **Given** an endpoint with a required integer URL parameter `count`, **When** the parameter is missing, **Then**
    400 `VALIDATION_ERROR`, detail `Request validation failed: 'count' is required.`; **when** it is `abc`, **then**
    400 `VALIDATION_ERROR`, detail `Request validation failed: 'count' has an invalid type or format.` (DEC-25).
    Neither response has an `errors` list.

---

### User Story 2 - The requesting user comes only from `X-User-Id` (Priority: P1)

**Why this priority**: data isolation by construction (DEC-17, TEC-18).

**Acceptance Scenarios**:

1. **Given** a request to an endpoint that needs the user, **When** `X-User-Id: 1` is sent, **Then** the endpoint
   receives user id 1.
2. **When** `X-User-Id` is missing, **Then** 400 `VALIDATION_ERROR`, detail `The 'X-User-Id' header is required.`
3. **When** `X-User-Id` is `abc`, `0`, `-5` or greater than 2147483647, **Then** 400 `VALIDATION_ERROR`, detail
   `The 'X-User-Id' header must be a positive integer.`

---

### User Story 3 - Internal endpoints exist only when enabled and only for an administrator (Priority: P1)

**Acceptance Scenarios**:

1. **Given** internal tools are disabled (default), **When** any `/api/internal/**` endpoint is called, even with
   `X-Role: ADMIN`, **Then** 404 `NOT_FOUND`, detail `Resource not found.`
2. **Given** internal tools are enabled, **When** an internal endpoint is called with `X-Role: ADMIN`, **Then** it
   executes.
3. **Given** internal tools are enabled, **When** an internal endpoint is called without `X-Role` or with any value
   other than `ADMIN` (including `admin`), **Then** 404 `NOT_FOUND`, detail `Resource not found.` — the same
   response as a missing route.

---

### User Story 4 - Operations with effect require a valid `Idempotency-Key` (Priority: P1)

**Acceptance Scenarios**:

1. **Given** an endpoint that requires the key, **When** `Idempotency-Key: k-1` is sent, **Then** the endpoint
   receives `k-1`.
2. **When** the header is missing or blank, **Then** 400 `MISSING_IDEMPOTENCY_KEY`, detail
   `The 'Idempotency-Key' header is required for this operation.`
3. **When** the key has more than 255 characters, or contains spaces or non-ASCII characters, **Then** 400
   `VALIDATION_ERROR`, detail `The 'Idempotency-Key' header must be 1 to 255 visible ASCII characters.`
4. **Given** an endpoint returns a replayed result, **Then** the response carries `Idempotent-Replayed: true`;
   otherwise the header is absent.

---

### User Story 5 - Money, percentages and timestamps have one format (Priority: P1)

**Acceptance Scenarios**:

1. **Given** a response with the amount 925.85, 50000 and -0.45, **Then** they are serialized as `"925.85"`,
   `"50000.00"` and `"-0.45"` (JSON strings, exactly 2 decimals).
2. **Given** a request amount `"925.855"`, `"1e3"`, `"12,5"`, `""` or `925.85` (a JSON number), **Then** 400
   `VALIDATION_ERROR` naming the field.
3. **Given** a request amount `"925.8"` or `"925"`, **Then** it is accepted as 925.80 / 925.00.
4. **Given** a response with a timestamp, **Then** it is an ISO 8601 UTC string ending in `Z`
   (`2026-11-21T14:36:00Z`), never a number.
5. **Given** an integer field (for example a share quantity) sent as `1.5`, `10.0` or `"10"`, **Then** 400
   `VALIDATION_ERROR` naming the field — never a silent truncation to `1` or `10`.

---

### User Story 6 - Every request has a correlation id (Priority: P2)

**Acceptance Scenarios**:

1. **Given** a request without `X-Request-Id`, **Then** the response carries a generated `X-Request-Id`.
2. **Given** a request with a valid `X-Request-Id: abc-123`, **Then** the response echoes `abc-123`.
3. **Given** an invalid `X-Request-Id` (more than 64 characters or characters outside `[A-Za-z0-9._-]`), **Then**
   a new one is generated (never reflected).
4. **Then** log lines written while handling the request contain the correlation id (MDC key `requestId`).

---

### User Story 7 - Business facts are recorded as metrics (Priority: P2)

**Acceptance Scenarios**:

1. **Given** the `BusinessMetrics` port, **When** `orderProcessed(BUY, MARKET, REJECTED, INSUFFICIENT_FUNDS)` is
   recorded, **Then** counter `cocos.orders.processed` with tags `side=BUY`, `type=MARKET`, `status=REJECTED`,
   `rejection_reason=INSUFFICIENT_FUNDS` increments by 1.
2. **When** a non-rejected order is recorded, **Then** its tag is `rejection_reason=NONE`.
3. **Then** no meter has a tag that identifies a user, an order, an idempotency key or a ticker.

---

### User Story 8 - The published API documentation is the contract (Priority: P2)

**Acceptance Scenarios**:

1. **Given** the application is running, **Then** `GET /openapi/openapi.yaml` returns the contract and
   `GET /swagger-ui/index.html` shows it.
2. **Then** the published contract is byte-identical to the contract of the latest spec
   (`specs/NNN-*/contracts/openapi.yaml` with the highest `NNN`).
3. **Then** every `/api/**` route of the application is documented in the published contract, and every documented
   route exists (no undocumented routes, no phantom routes).

### Edge Cases

- `X-User-Id` with leading/trailing spaces (`" 1 "`) is accepted as 1; `"1.0"` is invalid.
- `Idempotency-Key` is compared exactly (case-sensitive); it is not trimmed — a key with surrounding spaces is
  invalid.
- Amount strings are not trimmed: `" 1.00"` is invalid.
- Negative amounts are syntactically valid money; each endpoint decides whether it accepts them (orders and cash
  movements will require positive values in their own specs).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Error responses MUST follow plan.md § Error catalog exactly (code, HTTP status, title, type, detail).
- **FR-002**: The user MUST be resolved only from `X-User-Id` through the `@RequesterId` argument resolver.
- **FR-003**: Internal controllers MUST live in `infrastructure.adapter.in.rest.internal`, be annotated with
  `@ConditionalOnProperty(name = "cocos.internal-tools.enabled", havingValue = "true")`, and be guarded by the
  admin interceptor.
- **FR-004**: `Idempotency-Key` MUST be read only through the `@IdempotencyKeyHeader` argument resolver.
- **FR-005**: Money and percentages MUST cross the API only as strings, using `ApiDecimals` (format) and the
  `@DecimalString` constraint (parse/validate).
- **FR-006**: All API texts MUST be in English regardless of `Accept-Language` or the JVM locale.
- **FR-007**: The `BusinessMetrics` port and its Micrometer adapter MUST implement plan.md § Business metrics.
- **FR-008**: The published OpenAPI file MUST be a byte-identical copy of the latest spec contract.

### Key Entities

Domain building blocks created here and reused later: `UserId`, `Money`, `OrderSide`, `OrderType`, `OrderStatus`,
`RejectionReason`, `RequestFingerprint`, and the domain exceptions of plan.md § Domain exceptions.

## Success Criteria *(mandatory)*

- **SC-001**: Every acceptance scenario above has at least one test; `./mvnw verify` green.
- **SC-002**: The ArchUnit rules of spec 001 still pass with the new classes.

## Assumptions

- No business endpoint exists yet; conventions are tested through probe controllers in `src/test`.
