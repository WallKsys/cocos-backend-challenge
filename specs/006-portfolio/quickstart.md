# Quickstart: verify spec 006

```bash
./mvnw verify
```

Expected: `BUILD SUCCESS`; the 14 CU-01 scenarios pass.

```bash
docker compose up --build
curl -s http://localhost:8080/api/v1/portfolio -H 'X-User-Id: 1'
curl -s http://localhost:8080/api/internal/users/1/portfolio -H 'X-Role: ADMIN'
```

Expected: `"totalValue":"889756.00"`, cash `753000.00 / 125500.00 / 627500.00`, positions BMA (with the
`NEGATIVE_POSITION` warning), METR and PAMP. The internal endpoint answers the same (the demo profile enables
internal tools). Or use `http/006-portfolio.http`.
