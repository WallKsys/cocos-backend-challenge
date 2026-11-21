# Quickstart: verify spec 009

```bash
./mvnw verify              # includes PlanGuardIT and the 9 CU-I scenarios; the volume test does NOT run
./mvnw verify -Pvolume     # loads 10M orders (~2 min) and prints p50/p95/p99 for M-08 to M-11
```

Demo tools (internal, demo profile only):

```bash
docker compose up --build
curl -s -X POST http://localhost:8080/api/internal/orders/5/execute -H 'X-Role: ADMIN'      # LOMA limit buy → FILLED
curl -s -X PUT http://localhost:8080/api/internal/instruments/47/market-data -H 'X-Role: ADMIN' \
  -H 'Content-Type: application/json' -d '{"close":"1000.00"}'                               # PAMP at 1000
curl -s -X POST http://localhost:8080/api/internal/reset -H 'X-Role: ADMIN'                 # back to the seed
```

Or use `http/009-internal-tools.http`.
