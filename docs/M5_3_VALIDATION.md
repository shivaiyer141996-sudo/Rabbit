# M5.3 local performance, security, and recovery validation

M5.3 is a technical evidence stage on the designated Rabbit computer. It uses
only the local Docker Engine, Rabbit's Docker Compose stack, the separate local
backup device, and free one-off evidence containers. It does not use a public
URL, CI runner, registry deployment, cloud account, or paid scanner.

Repository tooling being present is not an M5.3 pass. Formal acceptance requires
a clean run on the actual pilot host plus review by the named people in Rabbit's
**Pilot readiness** screen.

## What the run proves

| Gate | Automated evidence | Acceptance threshold |
| --- | --- | --- |
| Performance | Student assessment list, dashboard, and analytics through the private Compose network | Approved concurrent students + 50% headroom; errors <1%; p95 <500 ms; p99 <1 s; checks >99% |
| Security | Local architecture, protected-file containment, private ports, unprivileged containers, read-only app roots, headers, CORS, authentication, TRACE, login rate limiting, and exact active-image Trivy scans | No failed mandatory control and no critical image finding |
| Backup | Quiesced PostgreSQL custom dump, MinIO archive, source counts, release manifest, and SHA-256 checksums on the approved separate device | Version 2 manifest and every checksum valid |
| Recovery | A uniquely named temporary Compose project restores the backup, reconciles data/assets, and checks login, assessment, result, analytics, PDF/XLSX, audit, and MinIO | Backup age ≤24 hours; isolated recovery ≤4 hours; every reconciliation and product check passes |
| Rollback | Named owner/tester/channel plus active and previous local image pairs and the rendered `--no-build` Compose command | Previous images exist locally and the human tabletop confirmation is `yes` |

The load test deliberately uses an approved non-demo Student and read-only
business endpoints. It creates and revokes one authentication session. It runs
against the backend on Docker's private `app` network so one
k6 container is not mistaken for dozens of student IP addresses by Nginx's edge
limit. Application authentication and per-user rate limiting remain active; the
profile calculates safe pacing and records it in the summary.

## One-time preparation

M5.1 must already pass on the designated host. Rabbit must be running and there
must be no active assessment during the quiesced backup window.

1. Create a protected M5.3 input file:

   ```bash
   cp .env.pilot-m5-3.example .env.pilot-m5-3
   chmod 600 .env.pilot-m5-3
   ```

2. Replace every placeholder. Use:

   - one active, non-demo Organisation Admin;
   - one active, non-demo Student in the same organisation;
   - a Student who has at least one **published synthetic rehearsal result**;
   - the approved concurrent-student count and a test duration of at least two minutes;
   - a previous Rabbit API/web image tag already retained on this computer;
   - named security/rollback testers and the real maintenance communication path.

3. The credentials file must stay on the designated host. Never attach it to
   Pilot readiness, copy it into evidence, paste it into a ticket, or commit it.

4. Set `PILOT_ROLLBACK_REHEARSAL_CONFIRMED=yes` only after the named tester has
   read the exact command and talked through stop, notification, forensic backup,
   verification, and reopen steps with the rollback owner.

The first M5.3 run may download the pinned free k6, Python, Alpine, and Trivy
images and the current Trivy vulnerability database. Rabbit itself remains local,
and no institution data leaves the designated computer.

## Run M5.3

From the Rabbit repository root:

```bash
make pilot-m5-3
```

The run performs these actions in order:

1. Re-runs M5.1 host/runtime preflight.
2. Executes the approved load plus 50% headroom.
3. Reviews local runtime and image security.
4. Briefly stops the API, web, and gateway, writes a consistent backup to the
   configured separate local device, and restarts only those stopped services.
5. Restores that new backup into temporary project-scoped volumes, runs read-only
   functional checks, and deletes only the temporary project and volumes.
6. Verifies and records the rollback tabletop without changing the live release.

Allow at least 30 minutes and run it in a maintenance window. The backup pause is
brief but no Student or staff member should use Rabbit during it.

## Evidence and sign-off

The run writes a checksummed bundle under:

```text
artifacts/pilot-m5-3/rabbit-m5.3-<UTC timestamp>/
```

The key files are:

- `m5-3-summary.md` and `m5-3-manifest.txt` — overall gate results;
- `performance-summary.json` — observed load, latency, errors, and threshold decisions;
- `security-review.md`, `security-http.json`, and `trivy-*.json` — security evidence;
- `functional-restore-drill.txt`, `restored-reconciliation.txt`, and
  `functional-recovery.json` — recovery, RPO/RTO, and product reconciliation;
- `rollback-rehearsal.md` — exact local rollback command and ownership;
- `SHA256SUMS` — integrity of the complete bundle.

A named tester reviews the bundle and records four mandatory rows in
`/pilot-readiness`: **Performance**, **Security review**, **Backup/restore**, and
**Operating ownership**. A pass requires a tester and an institution-approved
local evidence reference. Do not mark a row passed from repository files alone.

## Failure policy

- Performance failure: reduce the approved pilot cohort or fix the measured local
  bottleneck and rerun. Do not move Rabbit to cloud hosting.
- Security failure: keep institution data and live users blocked until every
  critical finding or failed control is resolved and rescanned.
- Backup/recovery failure: do not run the pilot; preserve both the failed evidence
  and live data, correct the procedure, create a new backup, and rerun.
- Rollback failure: retain the current release and name/prepare a valid local
  previous image pair, owner, tester, and communication path.
- Any unresolved Severity 1 or Severity 2 defect is a No-Go.

M5.3 does not update Rabbit's evidence register automatically and does not claim
institutional approval. M5.4 can begin only after the host evidence passes and the
remaining institution, cohort, owner, network, backup-device, and date decisions
are confirmed.
