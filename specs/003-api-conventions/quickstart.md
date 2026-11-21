# Quickstart: verify spec 003

```bash
./mvnw verify
```

Expected: `BUILD SUCCESS` with `ErrorHandlingIT`, `IdentityAndAdminIT`, `InternalToolsDisabledIT`,
`IdempotencyKeyIT`, `FormatsIT`, `RequestIdIT`, `PublishedContractMatchesRoutesIT` and the unit tests of the domain
building blocks, formats, metrics and contract.

```bash
docker compose up --build
curl -s -i http://localhost:8080/api/v1/does-not-exist
```

Expected: `HTTP/1.1 404`, `Content-Type: application/problem+json`, an `X-Request-Id` header and a body with
`"code":"NOT_FOUND"` and `"detail":"Resource not found."`.

Open `http://localhost:8080/swagger-ui/index.html`: it shows the contract `Cocos Broker API 0.3.0`.
