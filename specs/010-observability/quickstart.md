# Quickstart: verify spec 010

```bash
./mvnw verify      # includes DashboardsTest, DashboardSqlIT, ObservabilityIT, TelemetryUnavailableIT
```

```bash
docker compose down -v && docker compose up --build
# generate traffic: run http/005-order-placement.http (or a few curl calls)
open http://localhost:3000      # Grafana (no login) → Dashboards → folder "Cocos"
```

- **Cocos · Application health**: requests, errors, p95/p99 per endpoint, JVM, database pool, CPU.
- **Cocos · Business**: operations per minute, orders by status, rejections by reason, replays, conflicts,
  cancellations, lock wait; historical totals and rates from the ledger.
- **Explore → Loki**: `{service_name="cocos-broker-api"}`; open a line and follow its `trace_id` to Tempo.
