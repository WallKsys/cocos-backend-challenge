# Quickstart: verify spec 005

```bash
./mvnw verify
```

Expected: `BUILD SUCCESS`; Cucumber shows the 42 CU-03 scenarios passing; `ConcurrentOrdersIT` and
`IdempotencyLoadIT` green.

```bash
docker compose up --build
KEY=$(uuidgen)
curl -s -X POST http://localhost:8080/api/v1/orders -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' -H "Idempotency-Key: $KEY" \
  -d '{"instrumentId":47,"side":"BUY","type":"MARKET","amount":"50000.00"}'
# 201: FILLED, quantity 54, totalAmount 49995.90, remainingAmount 4.10

curl -s -i -X POST http://localhost:8080/api/v1/orders -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' -H "Idempotency-Key: $KEY" \
  -d '{"instrumentId":47,"side":"BUY","type":"MARKET","amount":"50000.00"}'
# 200 with Idempotent-Replayed: true and the same orderId
```

Or use `http/005-order-placement.http`.
