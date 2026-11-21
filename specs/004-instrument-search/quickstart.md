# Quickstart: verify spec 004

```bash
./mvnw verify
```

Expected: `BUILD SUCCESS`; the Cucumber summary includes the CU-02 scenarios passing; `TraceabilityTest` passes with
`CU-02` in scope.

```bash
docker compose up --build
curl -s "http://localhost:8080/api/v1/instruments?q=banco"
curl -s "http://localhost:8080/api/v1/instruments?q=pamp"
curl -s "http://localhost:8080/api/v1/instruments?name=ba"
```

Expected: `BBAR, BHIP, BMA, BPAT`; PAMP with `"lastPrice":"925.85"` and `"dailyChangePct":"0.44"`; a 400 with
`"code":"SEARCH_TERM_TOO_SHORT"`. Or use `http/004-instrument-search.http`.
