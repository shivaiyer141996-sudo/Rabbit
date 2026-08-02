# Release 1.0 operations runbook

## Milestone 5 local-only deployment

The institutional pilot does not use hosted images, public endpoints, AWS, or
another cloud runtime. Prepare one approved local computer with:

```bash
./infra/pilot/prepare-local-env.sh [approved-private-lan-ip]
# Fill the PILOT_* ownership and separate-backup placeholders in .env.
make architecture-check
make pilot-up
make pilot-preflight
```

The base stack publishes only Nginx. PostgreSQL, Redis, RabbitMQ, MinIO, and both
administration consoles remain Docker-internal. The gateway is loopback-only by
default; same-LAN access requires one explicit private address. Never use
`0.0.0.0`, router port forwarding, a public tunnel, or a public URL.

The generated preflight bundle under `artifacts/pilot-preflight/` contains no
secrets. Attach its checksummed report, manifest, service/image inventory, and
readiness response to the institution's pilot evidence register.

Before institution data is loaded, sign in with the seeded Admin only long enough
to invite and activate a real replacement Organisation Admin. Prove the new Admin
can sign in, then retire every seeded identity:

```bash
PILOT_REPLACEMENT_ADMIN_EMAIL=admin@institution.example \
CONFIRM_RETIRE_DEMO_USERS=yes \
make pilot-retire-demo-users
```

The guarded command refuses a demo email, proves the replacement account is an
active Admin in the tenant, suspends the four seeded users, revokes their refresh
tokens, invalidates their published passwords, and writes an audit event. It does
not delete academic demonstration data.

## Deployment gates

Release only an immutable `v*` tag after:

1. CI frontend, backend, PostgreSQL contract, secret scan, dependency audit, image
   SCA, and container-build jobs pass.
2. The UAT checklist is signed off for the pilot tenant.
3. Production secrets are stored outside Git and the `production` profile starts cleanly.
4. Database and MinIO backups complete and their SHA-256 manifest verifies.
5. A named rollback owner and pilot support contact are on duty.
6. The tenant's `/pilot-readiness` register shows every mandatory check as passed
   and contains the institution's locked sign-off.

The tag-triggered `Release images` workflow is retained for a future explicitly
approved production architecture. It is not used by Milestone 5; the local pilot
builds and tags its images on the designated computer and records their local
identifiers in preflight evidence.

Start an approved immutable tag with:

```bash
docker compose \
  --env-file infra/deploy/production.env \
  -f docker-compose.yml \
  -f infra/deploy/compose.images.yml \
  up -d --no-build
```

Create `infra/deploy/production.env` from the example using a secret manager or protected host file. The real file is Git-ignored.

## Health and observability

- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
- Prometheus metrics: `/actuator/prometheus` (admin-authenticated)
- Tenant operations console: `/operations`

Alert when readiness is down for two consecutive minutes, five-minute server error rate exceeds 1%, p95 API latency exceeds 500 ms, the notification failed backlog is non-zero for 15 minutes, or a governance review exceeds 48 hours.

## Identity hardening controls

- `LOGIN_MAX_FAILED_ATTEMPTS` defaults to `5`.
- `LOGIN_LOCK_DURATION` defaults to `PT30M`.
- `INVITATION_TTL` defaults to `PT72H`.
- In production, `INVITATION_ACTIVATION_BASE_URL` must be the approved HTTPS
  `/activate` route. In the local pilot it must match the selected loopback or
  private-LAN HTTP origin exactly.

Failed-login counters are intentionally committed even when authentication returns
an error. Do not wrap or merge the independent login-attempt transaction into the
outer authentication transaction. Invitation links are capability secrets: share
them only through an institution-approved channel, never place them in tickets or
logs, and reissue immediately if disclosure is suspected.

## Backup

Run from the repository root:

```bash
./infra/backup/backup.sh
```

The no-argument pilot command reads `PILOT_BACKUP_DIRECTORY` from the protected
`.env`. An explicit approved path may still be supplied for an operator-controlled
one-off backup.

The backup contains a custom-format PostgreSQL dump, MinIO asset archive, manifest, and checksums. Store a copy in a separate failure domain. The target operating objective is:

- Database and assets RPO: 24 hours
- Service RTO: 4 hours
- Restore drill: quarterly and before a major schema release

Verify a backup without changing data:

```bash
./infra/backup/verify.sh /approved-external-disk/rabbit-YYYYMMDDTHHMMSSZ
```

Prove restoration into temporary, isolated PostgreSQL and MinIO Docker volumes:

```bash
./infra/backup/restore-drill.sh \
  /approved-external-disk/rabbit-YYYYMMDDTHHMMSSZ
```

The restore drill records database reconciliation and asset counts beside the
backup, then removes only its uniquely named temporary containers and volumes.
It never modifies the live `postgres-data` or `minio-data` volumes.

## Restore

Restore is destructive and requires an explicit confirmation flag:

```bash
./infra/backup/restore.sh /secure/rabbit-backups/rabbit-YYYYMMDDTHHMMSSZ \
  --confirm-destructive-restore
```

After restore, verify readiness, login, question assets, one student assessment, result publication, report export, audit search, and notification creation before reopening access.

## M5.3 technical evidence

Run M5.3 only after local host preflight passes, demo identities are retired, a
non-demo Admin and Student rehearsal account are ready, the Student has a
published synthetic result, a separate local backup device is connected, and no
assessment is active:

```bash
cp .env.pilot-m5-3.example .env.pilot-m5-3
chmod 600 .env.pilot-m5-3
# Replace every placeholder and record the rollback tabletop confirmation.
make pilot-m5-3
```

The workflow briefly quiesces API/web access for a consistent backup. It restores
only into a unique temporary Compose project, verifies product and data recovery,
and removes only that project's volumes. It does not execute the recorded
rollback command. Review [M5.3 local validation](M5_3_VALIDATION.md) before use.

## M5.4 institutional pilot evidence

M5.4 uses the same local stack after M5.1-M5.3 have passed. Prepare the protected
Admin, institution, owner, roster, incident, and assessment inputs described in
[M5.4 local pilot execution](M5_4_PILOT_EXECUTION.md). Before each event opens:

```bash
make pilot-m5-4-freeze
```

After the institution completes the event, fills attendance, resolves S1/S2,
publishes results, and verifies reports:

```bash
make pilot-m5-4-reconcile PILOT_FREEZE_MANIFEST=<passed-freeze-manifest.json>
```

Run rehearsal and live assessment as separate freezes. The runner is read-only
and accepts only loopback/private-LAN targets. It writes restricted local exports
and a checksummed `urn:rabbit-evidence:...` reference; it never changes Rabbit's
evidence register or signs on behalf of an institution.

## Future database move

Milestone 5 remains on the bundled local PostgreSQL database. Rabbit's backend
connection is nevertheless externalized through `DATABASE_URL`,
`DATABASE_USERNAME`, and `DATABASE_PASSWORD`, with pool and Flyway settings also
configurable. A future approved move to another PostgreSQL instance must follow
[Database portability](DATABASE_PORTABILITY.md), begin from a verified backup,
and retain the original local database as the rollback target until acceptance.

A different database engine is not an environment-only switch because Rabbit's
tenant-integrity triggers use PostgreSQL. It requires a separately approved
engineering and migration project.

## Rollback

1. Stop new assessment starts and communicate the maintenance window.
2. Route traffic to the previously verified image tag.
3. If the migration is backward-compatible, retain the database and validate.
4. If data restoration is required, preserve a forensic backup first, then use the tested restore procedure.
5. Record incident timestamps, affected tenants, trace IDs, actions, and final closure in the incident log.

## Pilot closure

A controlled pilot is not GA sign-off by itself. Close it only when the UAT checklist, accessibility review, load-test evidence, restore evidence, security review, and operational ownership are recorded in `/pilot-readiness`, and an authorised institution representative completes the locked sign-off. Provider-backed email/SMS remains disabled until credentials, consent wording, delivery monitoring, and escalation ownership are approved.
