# PostgreSQL backup — Symmetry 0.0.1

- **File:** `symmetry-0.0.1-20261021.sql.gz`
- **Created:** 2026-10-21
- **Source database:** local Docker service `postgres` (database/user `symmetry`)
- **PostgreSQL:** 16.15
- **Contents:** schema + data (plain SQL, `--clean --if-exists`), gzip-compressed
- **SHA-256:** `9b2a3eeaeab57bde83296d1754c6c89402114ea1d63eeb7e19289cb4b8daac3f`

This is a **development snapshot** taken after the 0.0.1 UI/backend build. It
contains the seeded demo identities (`worker`, `publisher`, `admin`), the `acme`
organization, one demo contract (`module.demo.resize@1.0.0`), one global setting
and one config request. It contains no production credentials.

## Restore

Stop the backend first, then restore into the running Postgres container:

```bash
cd /home/arunmbabu246/projects_20261/oct/java/symmetry
gunzip -c data/pgsql/0.0.1/symmetry-0.0.1-20261021.sql.gz \
  | sg docker -c 'docker exec -i symmetry-postgres psql -U symmetry -d symmetry'
```

The dump uses `--clean --if-exists`, so it drops the existing tables before
recreating them. To start from an empty database instead:

```bash
sg docker -c 'docker compose -f deploy/docker-compose.yml down -v'
sg docker -c 'docker compose -f deploy/docker-compose.yml up -d postgres'
gunzip -c data/pgsql/0.0.1/symmetry-0.0.1-20261021.sql.gz \
  | sg docker -c 'docker exec -i symmetry-postgres psql -U symmetry -d symmetry'
```

`symmetry-postgres` must already be running for the restore command. Flyway sees
the restored `flyway_schema_history` and will not re-run V1/V2.
