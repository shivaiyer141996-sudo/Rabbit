# Release 1.0 operations runbook

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

The tag-triggered `Release images` workflow publishes SBOM/provenance-enabled API and web images to GitHub Container Registry. Production deployment remains an explicit environment-approved action.

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
- `INVITATION_ACTIVATION_BASE_URL` must be the public HTTPS `/activate` route.

Failed-login counters are intentionally committed even when authentication returns
an error. Do not wrap or merge the independent login-attempt transaction into the
outer authentication transaction. Invitation links are capability secrets: share
them only through an institution-approved channel, never place them in tickets or
logs, and reissue immediately if disclosure is suspected.

## Backup

Run from the repository root:

```bash
./infra/backup/backup.sh /secure/rabbit-backups
```

The backup contains a custom-format PostgreSQL dump, MinIO asset archive, manifest, and checksums. Store a copy in a separate failure domain. The target operating objective is:

- Database and assets RPO: 24 hours
- Service RTO: 4 hours
- Restore drill: quarterly and before a major schema release

## Restore

Restore is destructive and requires an explicit confirmation flag:

```bash
./infra/backup/restore.sh /secure/rabbit-backups/rabbit-YYYYMMDDTHHMMSSZ \
  --confirm-destructive-restore
```

After restore, verify readiness, login, question assets, one student assessment, result publication, report export, audit search, and notification creation before reopening access.

## Rollback

1. Stop new assessment starts and communicate the maintenance window.
2. Route traffic to the previously verified image tag.
3. If the migration is backward-compatible, retain the database and validate.
4. If data restoration is required, preserve a forensic backup first, then use the tested restore procedure.
5. Record incident timestamps, affected tenants, trace IDs, actions, and final closure in the incident log.

## Pilot closure

A controlled pilot is not GA sign-off by itself. Close it only when the UAT checklist, accessibility review, load-test evidence, restore evidence, security review, and operational ownership are recorded in `/pilot-readiness`, and an authorised institution representative completes the locked sign-off. Provider-backed email/SMS remains disabled until credentials, consent wording, delivery monitoring, and escalation ownership are approved.
