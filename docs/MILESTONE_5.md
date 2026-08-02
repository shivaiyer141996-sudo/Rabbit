# Milestone 5 — Institutional pilot and release acceptance

## Status

**M5.1 local-environment tooling is implemented; execution evidence is pending
on the designated host.** Milestone 5 is an environment, people, and evidence
milestone. It does not add product scope.

- Release candidate: `b6f6715`
- Milestone 4.2 automated gates: passed on GitHub Actions run `30699322752`
- `main`: remains at `2aaa056`
- Blocking release evidence: human desktop and 360 px Android visual acceptance
- Blocking pilot decisions: institution, named operating owners, final dates, and
  the designated local host/network/backup arrangement
- Final database decision: local PostgreSQL on the designated computer; AWS and
  managed cloud databases are rejected for Milestone 5
- Future portability: database connection and migration settings remain external
  to product code, with a backup-led path to another PostgreSQL instance if a
  later milestone approves it
- Binding architecture policy: zero-cost local Docker Compose with PostgreSQL,
  Redis, RabbitMQ, and MinIO; no cloud or paid runtime without explicit approval

Milestone 5 will use a local-only environment. Rabbit must not be deployed to AWS
or another cloud service, made publicly reachable, or connected to a paid hosting
account. This is a final Milestone 5 constraint, not a pending approval item.
Institution data may be loaded only after the relevant owner approves the remaining
pilot decisions below.

The permanent pre-production constraint is recorded in
[Local-only infrastructure policy](LOCAL_INFRASTRUCTURE_POLICY.md).

## Release 1.0 scope freeze

The pilot validates the completed web product only:

- Single Correct and Multiple Correct MCQ authoring and governance
- Assessment creation, review, approval, scheduling, delivery, and submission
- Objective evaluation, governed manual mark correction, publication, and audit
- Admin, Academic Head, Teacher, Reviewer, and Student workspaces
- Student and teacher analytics plus PDF/Excel exports
- Tenant administration, operations, recovery, and pilot evidence capture

The pilot excludes AI, subjective answers, parents, native apps, proctoring,
payments, and LMS/ERP/SIS integrations. New feature requests enter the post-pilot
backlog and cannot delay Milestone 5. Only release-blocking defects may change the
release candidate during the pilot.

## Proposed pilot baseline

| Decision | Proposed baseline | Status / owner |
| --- | --- | --- |
| Institution | One Chennai coaching centre, tuition centre, school, or training institute with a willing academic owner | **TBD — Shiva to confirm** |
| Cohort | 1 Admin, up to 3 Teachers, 1 Reviewer, and 30 Students; hard pilot cap of 50 total active users | Proposed |
| Academic content | 2 subjects, at least 4 topics, and at least 100 approved MCQs | Proposed with institution |
| Assessments | One staff rehearsal plus one live student assessment covering the full question-to-report journey | Proposed |
| Live assessment size | 30–45 questions, 45–60 minutes, maximum 30 concurrent students in the first live run | Proposed with institution |
| Hosting | One designated local laptop or desktop running Rabbit through Docker Compose; 16 GB RAM recommended, 8 GB minimum for rehearsal, 4 CPU cores, and at least 30 GB free SSD space | **Final — local only** |
| Database | Local PostgreSQL 16 container using the persistent `postgres-data` Docker volume on the designated computer | **Final — Shiva decided** |
| Future database move | Keep the connection configurable and retain portable backups; permit a controlled move to another PostgreSQL instance only after separate approval | **Provisioned, not active in M5** |
| Supporting storage | Uploaded assets, Redis, and RabbitMQ data stored in local Docker named volumes on the designated computer | **Final — local only** |
| Incremental hosting cost | ₹0 cloud cost; use the existing computer, power, and local network | **Approved direction** |
| User access | `localhost` for individual review; the same trusted Wi-Fi/LAN for multi-user pilot access; no public URL, port forwarding, or public tunnel | Proposed with institution |
| Backup | Daily checksum-verified backup to a separate local USB drive, external disk, or second approved computer; a copy on the same host disk is not sufficient | **TBD — device and owner required** |
| Notifications | In-app notifications enabled; provider-backed email and SMS disabled; activation URLs shared manually through an institution-approved channel | Recommended default |
| Data | Synthetic/rehearsal data first; minimum necessary real student data for the live assessment; no sensitive documents | Proposed with institution |
| Support window | Named technical and institution contacts available from 30 minutes before until 60 minutes after the live assessment | **TBD — owners required** |

"Local storage" means server-side data on the designated Rabbit computer. It does
not mean browser `localStorage`; assessment responses, scores, credentials, and
institution data must continue to be handled by Rabbit's backend and PostgreSQL.

## Local-only operating boundary

- The designated computer must remain powered on, awake, and connected throughout
  a rehearsal or live assessment.
- Students and staff must be on the same trusted local network unless they are
  using the application directly on that computer.
- Only Rabbit's web entry point may be reachable on the LAN. PostgreSQL, Redis,
  RabbitMQ, and MinIO ports must remain host-only or Docker-internal.
- Router port forwarding, public tunnels, dynamic DNS, and public Internet exposure
  are prohibited for this milestone.
- A host-disk failure can remove both the application data and Docker volumes.
  Therefore, the latest backup must also exist on a separate approved local device.
- AWS, managed cloud databases, and other cloud hosting will not be proposed or
  used during Milestone 5.
- The portability provision does not activate or approve an external database.
  See [Database portability](DATABASE_PORTABILITY.md) for the future boundary.

## Proposed dates

These dates are a working baseline and become binding only after the institution
and owners confirm availability.

| Phase | Proposed window | Exit result |
| --- | --- | --- |
| M5.0 — Finalise plan | 6–7 August 2026 | Decisions, owners, scope, cohort, and dates approved; M4.2 visual acceptance recorded |
| M5.1 — Pilot environment | 10–12 August 2026 | Local Docker environment, LAN controls, backup, monitoring, and rollback ready |
| M5.2 — Journey/UI validation | 13–17 August 2026 | Critical role journeys pass on desktop and Android; accessibility evidence recorded |
| M5.3 — Performance/security/recovery | 18–20 August 2026 | Pilot load, security review, backup and restore evidence pass |
| M5.4 — Institutional pilot | 21–28 August 2026 | Rehearsal and one complete live assessment executed |
| M5.5 — Approval and handover | 31 August–2 September 2026 | Institution acceptance, known issues, ownership, and Go/No-Go recorded |

## Required owners

Operational responsibility must remain with named people. An automated agent cannot
serve as an on-call, security, rollback, or institutional acceptance owner.

| Responsibility | Proposed owner | Confirmation |
| --- | --- | --- |
| Product and budget decision | Shiva Iyer | Pending |
| Institution sponsor / authorised signatory | TBD | Required |
| Institution UAT lead | TBD | Required |
| Technical deployment and release | TBD | Required |
| Live-assessment support and defect triage | TBD | Required |
| Backup restore and rollback | TBD | Required |
| Data/privacy approval | TBD institution representative | Required |

## M5.0 — Finalise the pilot plan

Exit criteria:

- The institution and authorised sponsor are named.
- User counts, subjects, assessment format, and live date are confirmed.
- The Release 1.0 scope freeze is accepted in writing.
- The local host computer, host owner, trusted LAN, access location, and backup
  device/path are approved.
- No cloud account, hosting budget, domain, public IP, or certificate is required,
  and none may be created for Milestone 5.
- Every operating responsibility has a reachable primary owner and backup.
- Email/SMS remains disabled unless a provider, credentials, consent wording,
  monitoring, and escalation owner are separately approved.
- Milestone 4.2 desktop and 360 px Android acceptance is recorded.

## M5.1 — Set up the pilot environment

Repository implementation now provides:

- loopback-only gateway binding by default and explicit private-LAN binding for
  an approved multi-user pilot;
- a Docker-internal data network with no host-published PostgreSQL, Redis,
  RabbitMQ, MinIO, or administration-console ports;
- generated non-default local credentials in a protected, Git-ignored `.env`;
- a guarded administrator handover that requires a proven replacement Admin,
  suspends all seeded demo identities, revokes their sessions, and audits the act;
- a hardened local-pilot Compose overlay with restart policies, read-only
  application containers, temporary filesystems, and bounded local logs;
- an architecture-policy verifier and redacted M5.1 host/runtime preflight bundle;
- checksum-verified PostgreSQL/MinIO backups and an isolated, non-destructive
  restore drill using temporary Docker volumes;
- locally tagged API/web images and a release manifest containing their exact
  deployed evidence when runtime preflight executes.

Prepare and validate the designated host with:

```bash
./infra/pilot/prepare-local-env.sh [approved-private-lan-ip]
# Replace only the PILOT_* owner and separate-backup placeholders in .env.
make pilot-up
make pilot-preflight
```

Tooling availability is not M5.1 acceptance. This stage passes only when the
generated preflight bundle from the actual host has zero failures and the backup
plus isolated restore evidence is attached to `/pilot-readiness`.

Required evidence:

- Immutable release commit/image references and deployment record
- Local host inventory, available RAM/CPU/disk, Docker Engine/Desktop version, and
  Docker Compose version
- Secrets stored outside Git; local default/demo passwords replaced or demo
  accounts removed before institution data is loaded
- Host firewall permits Rabbit access only from the approved trusted LAN; database,
  queue, cache, object-store, and admin-console ports remain host-only/private
- PostgreSQL and MinIO data persist in named local Docker volumes
- Database and object backups written to a separate approved local device with
  checksums; one restore is proven before the live assessment
- Local health checks, operations-dashboard review, disk-space checks, and support
  contacts agreed by named owners
- Documented rollback to the last verified release and a maintenance-message owner
- Environment inventory and access register; no cloud cost tracker is required

## M5.2 — Validate screens and user journeys

Test each role using real browser interactions, not API calls alone:

- Admin: organisation, users, masters, dashboards, reports, operations, pilot register
- Teacher: question lifecycle, assessment lifecycle, monitoring, reports, exports
- Reviewer: question and assessment work queues, return/approval separation
- Student: instructions, start/resume, save, flag, timer, submit, history, published result and analytics
- Re-evaluation: manual question review, bounded mark change, mandatory reason,
  pending-publication reset, audit history, and republish
- Desktop: current Chrome and Edge at 100% and 200% zoom
- Android: Chrome at 360 px and one representative physical device
- Accessibility: keyboard, focus, labels, contrast, reduced motion, and screen-reader
  spot checks on the critical journey

## M5.3 — Test performance, security, and recovery

Exit criteria:

- Load test represents the approved concurrent-student count plus 50% headroom.
- Error rate is below 1%, p95 API latency below 500 ms, and p99 below 1 second.
- No unresolved critical security finding or exposed secret exists.
- Backup manifest verifies and a restore succeeds in a separate non-production target.
- Restored login, assets, assessment, result, export, and audit data reconcile.
- Actual recovery stays within the four-hour RTO and 24-hour RPO.
- A rollback rehearsal identifies the exact owner, command, and communication path.
- If the designated local host cannot meet the approved load target, reduce the
  pilot cohort or stop the pilot; do not move to cloud hosting.

## M5.4 — Run the institutional pilot

1. Import/create the approved academic structure and minimal user cohort.
2. Conduct the staff rehearsal and resolve only Severity 1/2 blockers.
3. Freeze assessment content and publish the live schedule.
4. Confirm support contacts, incident channel, backup, monitoring, and rollback.
5. Run the complete assessment through result publication and reports.
6. Reconcile attendance, submissions, scoring, publication, exports, and audit events.
7. Record every issue with severity, owner, workaround, and due date.

## M5.5 — Final approval and handover

The pilot is **Go** only when:

- Every mandatory row in `/pilot-readiness` is passed with genuine evidence.
- No Severity 1 or Severity 2 defect remains open.
- The institution signs the locked acceptance record.
- Known Severity 3/4 issues have owners, workarounds, and target dates.
- Support, monitoring, backup, restore, incident, and rollback ownership is accepted.
- Pilot data and backups remain only on approved local media, and no cloud hosting
  or public exposure was introduced.

Any failed mandatory condition produces a **No-Go** or a time-boxed conditional
retest. A successful pilot is not permission to add post–Release 1.0 scope.

## Severity and change policy

| Severity | Definition | Pilot action |
| --- | --- | --- |
| S1 | Security/data loss, cross-tenant exposure, or system unavailable during the live assessment | Stop pilot; rollback or restore; executive notification |
| S2 | Critical journey blocked with no safe workaround, wrong scoring, or result confidentiality failure | No-Go until fixed and retested |
| S3 | Non-critical function impaired with a safe workaround | Record owner and due date; pilot may continue with approval |
| S4 | Cosmetic, wording, or minor usability issue | Add to post-pilot backlog |

Every release-candidate change must identify the defect, affected journey, focused
retest, regression evidence, and exact deployed commit. Feature requests are not
eligible for release-candidate changes.

## Decision record to complete next

1. Institution name and authorised sponsor
2. Final cohort and subject/assessment details
3. Designated local computer, operating system, RAM, CPU, free disk, and host owner
4. Access mode: same computer only or trusted institution Wi-Fi/LAN
5. Separate local backup device/path and backup/restore owner
6. Named technical, support, rollback, UAT, and data/privacy owners
7. Confirmed dates and live assessment time
8. Explicit confirmation that email/SMS stays disabled
