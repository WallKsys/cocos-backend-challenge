# Quickstart: verify spec 008

```bash
./mvnw verify
```

Expected: `BUILD SUCCESS`; the 17 CU-04/CU-07 scenarios pass; `CancelVersusExecutionIT` and `CancellationLoadIT` green.

```bash
docker compose up --build
curl -s "http://localhost:8080/api/v1/orders?limit=5" -H 'X-User-Id: 1'          # 11, 10, 9, 8, 7 + nextCursor
curl -s -X POST http://localhost:8080/api/v1/orders/7/cancel -H 'X-User-Id: 1'   # CANCELLED, alreadyCancelled false
curl -s -X POST http://localhost:8080/api/v1/orders/7/cancel -H 'X-User-Id: 1'   # CANCELLED, alreadyCancelled true
curl -s -X POST http://localhost:8080/api/v1/orders/2/cancel -H 'X-User-Id: 1'   # 409 ORDER_NOT_CANCELLABLE
```

Or use `http/008-cancellation-history.http`.
