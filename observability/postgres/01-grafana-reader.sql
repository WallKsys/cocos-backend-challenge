-- Read-only database user for the Grafana "Cocos Ledger" data source (spec 010, TEC-31).
-- Runs once, when the PostgreSQL container initializes an empty data directory, BEFORE the application creates the
-- tables with Flyway. The default privileges make every table later created by "cocos" readable (and only readable).
-- Demo credentials: this file is used only by docker compose.
CREATE ROLE grafana_reader LOGIN PASSWORD 'grafana_reader';
GRANT CONNECT ON DATABASE cocos TO grafana_reader;
GRANT USAGE ON SCHEMA public TO grafana_reader;
ALTER DEFAULT PRIVILEGES FOR ROLE cocos IN SCHEMA public GRANT SELECT ON TABLES TO grafana_reader;
