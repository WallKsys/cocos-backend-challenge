# Quickstart: verify spec 002

```bash
./mvnw verify
```

Expected: `BUILD SUCCESS`, including `OriginalScriptIsUnchangedTest`, `SeedDataIT`, `LedgerReferenceQueriesIT`,
`SchemaConstraintsIT`, `TimestampsAndSearchColumnsIT` and `IndexesIT`.

```bash
docker compose up --build
docker compose exec postgres psql -U cocos -d cocos -c "select version, description, success from flyway_schema_history order by installed_rank"
docker compose exec postgres psql -U cocos -d cocos -c "select ticker, name_normalized from instruments where name_normalized like '%rio%'"
```

Expected: versions 1 to 5 with `success = t`; BHIP, MOLI, OEST and RICH.
