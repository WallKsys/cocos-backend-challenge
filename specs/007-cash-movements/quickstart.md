# Quickstart: verify spec 007

```bash
./mvnw verify
```

Expected: `BUILD SUCCESS`; the 17 CU-05/CU-06 scenarios pass; `CashIdempotencyIT` green.

```bash
docker compose up --build
curl -s -X POST http://localhost:8080/api/v1/cash/deposits -H 'Content-Type: application/json' \
  -H 'X-User-Id: 2' -H "Idempotency-Key: $(uuidgen)" -d '{"amount":"100000.00"}'
# 201: CASH_IN FILLED 100000.00
curl -s -X POST http://localhost:8080/api/v1/cash/withdrawals -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' -H "Idempotency-Key: $(uuidgen)" -d '{"amount":"700000.00"}'
# 201: CASH_OUT REJECTED INSUFFICIENT_FUNDS (reserved cash cannot be withdrawn)
```

Or use `http/007-cash-movements.http`.
