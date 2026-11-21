# Quickstart: verify spec 001

## Prerequisites

- Java 25 (`sdk install java 25-tem && sdk use java 25-tem`)
- Docker running

## Automated verification

```bash
./mvnw verify
```

Expected: `BUILD SUCCESS`. The output shows the unit tests (Surefire: `SmokeUnitTest`, `ArchitectureTest`,
`TraceabilityTest`, `TraceabilityCheckerTest`), the integration tests (Failsafe: `HealthEndpointIT`,
`HealthDownWhenDatabaseUnavailableIT`) and the Cucumber summary (`1 Scenarios (1 passed)`).

```bash
./mvnw test -Dgroups=unit
```

Expected: only unit tests run, no Docker container is started.

## Manual verification

```bash
docker compose up --build
```

In another terminal (or with `http/000-health.http`):

```bash
curl -s http://localhost:8080/actuator/health
# {"status":"UP"}
```

Stop with `docker compose down`.
