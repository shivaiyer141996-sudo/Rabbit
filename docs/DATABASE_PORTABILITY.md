# Database portability

## Current decision

Rabbit Milestone 5 runs on the PostgreSQL 16 container in `docker-compose.yml`.
Its data persists on the designated computer in the `postgres-data` Docker
volume. No external or cloud database is enabled.

## Portability provided now

The backend receives its connection and pool configuration from environment
variables. Docker Compose now honours those variables instead of forcing the
bundled database address.

| Setting | Local default | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://postgres:5432/rabbit` | JDBC address of the selected PostgreSQL database |
| `DATABASE_USERNAME` | Local `POSTGRES_USER` | Application database user |
| `DATABASE_PASSWORD` | Local `POSTGRES_PASSWORD` | Application database secret |
| `DATABASE_MAX_POOL_SIZE` | `20` | Maximum backend database connections |
| `DATABASE_MIN_IDLE` | `2` | Minimum idle connections |
| `DATABASE_*_TIMEOUT_MS` | Safe local defaults | Connection and validation timeouts |
| `DATABASE_MIGRATION_LOCATIONS` | `classpath:db/migration` | Flyway migration source |

This allows a future move to another PostgreSQL 16-or-newer instance—such as a
new local computer, an institution-owned server, or a later approved managed
service—without changing Rabbit's product code. The dormant template is
`infra/database/external-postgres.env.example`; its real secret-bearing copy is
Git-ignored.

The matching dormant Compose override is
`infra/database/compose.external-postgres.yml`. It removes the backend's local
PostgreSQL startup dependency and places the bundled database service behind an
inactive profile. This prevents Rabbit from silently waiting for or writing to
the wrong local database after an approved move. The normal `docker compose up`
path does not load this override and remains fully local.

Spring Data/JPA keeps repositories separate from database connection details,
and Flyway keeps the schema versioned with the application. The current backup
already uses PostgreSQL's portable custom dump format with ownership and
privileges excluded.

## Future PostgreSQL move procedure

This is a future runbook, not permission to move Milestone 5 data now.

1. Announce downtime and stop new logins, assessment starts, saves, and result
   publication.
2. Run `./infra/backup/backup.sh <separate-backup-directory>` and verify the
   generated `SHA256SUMS` file.
3. Record the current Rabbit commit, PostgreSQL version, Flyway schema version,
   source row counts, and MinIO backup manifest.
4. Create an empty target PostgreSQL database and a least-privilege Rabbit user.
5. Restore `postgres.dump` with `pg_restore --no-owner --no-privileges
   --exit-on-error`.
6. Copy `infra/database/external-postgres.env.example` to the ignored
   `infra/database/external-postgres.env`, then set the target JDBC URL, username,
   password, SSL mode, and connection-pool limit.
7. Review the merged configuration, then start Rabbit in external PostgreSQL
   mode (Docker Compose 2.24.4 or newer is required for `!override`):

   ```bash
   docker compose \
     --env-file .env \
     --env-file infra/database/external-postgres.env \
     -f docker-compose.yml \
     -f infra/database/compose.external-postgres.yml \
     config

   docker compose \
     --env-file .env \
     --env-file infra/database/external-postgres.env \
     -f docker-compose.yml \
     -f infra/database/compose.external-postgres.yml \
     up -d --build
   ```

   Confirm that the merged `backend.depends_on` contains Redis, RabbitMQ, and
   MinIO but not PostgreSQL. Flyway must validate the restored history and apply
   only newer approved migrations.
8. Reconcile tenant and table counts, then verify login, question assets, one
   assessment, scoring, publication, reports, exports, audit events, and tenant
   isolation.
9. Keep the original local database stopped but recoverable until the migration
   owner signs acceptance. Roll back by restoring the original connection
   settings; do not write to both databases.

Uploaded files are stored separately in MinIO. A full environment move must also
restore `minio-data.tar.gz`; changing only the database does not move assets.

## Honest compatibility boundary

Release 1.0 supports PostgreSQL as its database engine. The domain and service
code use JPA abstractions, but the Flyway migrations and database-level tenant
guards intentionally use PostgreSQL triggers and PL/pgSQL.

Therefore:

- Moving from local PostgreSQL to another PostgreSQL instance is a configuration
  and controlled data-migration exercise.
- Moving to MySQL, SQL Server, Oracle, or a non-relational database is a separate
  engineering project. It requires a compatible driver, vendor-specific Flyway
  migrations, equivalent tenant-integrity controls, data conversion, performance
  testing, backup/restore changes, and full regression/UAT evidence.

This boundary prevents a future database change from weakening organisation
isolation, scoring integrity, or audit history merely to claim engine neutrality.
