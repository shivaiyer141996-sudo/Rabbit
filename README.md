# Rabbit AiP

Rabbit is a **Student Progress & Academic Intelligence Platform**. Assessments are the mechanism; student success is the outcome.

This repository contains the Milestone 1 Foundation, Milestone 2 Intelligence,
Milestone 3 GA-readiness, Milestone 4 controlled-pilot implementation, and local
Milestone 5 pilot-readiness tooling for Rabbit AiP Release 1.0. Release 1.0 is
deliberately limited to web-based **Single Correct MCQ** and **Multiple Correct
MCQ** workflows. AI, parent access, subjective evaluation, native mobile apps,
proctoring, and LMS/ERP/SIS integrations are excluded.

## Milestone 1 delivered

- Secure login, organisation selection, JWT access/refresh tokens, and role-aware navigation
- Multi-tenant organisation, user, membership, subject, topic, section, and academic-year foundation
- Governed MCQ question bank with authoring, validation, review, approval, versioning, search, and filters
- Assessment drafting, approved-question selection, publishing, scheduling, and section eligibility
- Mobile-friendly student assessment player with timer, navigation, flags, 30-second auto-save, recovery, and submit confirmation
- Objective scoring for both supported MCQ types and basic student result views
- PostgreSQL migrations and demo data
- Docker Compose stack: Next.js, Spring Boot, PostgreSQL, Redis, RabbitMQ, MinIO, and Nginx
- CI, unit tests, health endpoints, API error format, architecture notes, and requirements traceability

## Milestone 2 delivered

- Role-specific intelligence dashboards built from published evaluation data
- Governed objective evaluation with grade assignment, result publication, rank, and re-evaluation history
- Institution, assessment, student, faculty, and question-quality reports
- Score distribution, performance trajectory, at-risk rules, difficulty index, and discrimination index
- Question review checklist, decision history, and separate assessment approval workflow
- In-app notification centre, user preferences, critical-alert override, and retry state
- Immutable audit explorer with actor, role, IP, trace ID, before/after values, filters, and CSV export
- Organisation settings, assessment defaults, grading bands, branding controls, and academic masters
- Responsive interfaces for dashboards, approvals, reports, settings, notifications, audit logs, and published student results
- PostgreSQL Milestone 2 migration, demonstration intelligence data, and additional backend/frontend tests

## Milestone 3 delivered

- Styled, governed PDF reports and native three-sheet Excel workbooks alongside CSV
- Tenant operations console for dependency health, traffic, capacity, workflow backlogs, and pilot release gates
- Audited tenant feature flags with deterministic percentage rollouts
- Redis-backed application rate limiting with safe in-process fallback and Nginx edge limits
- Prometheus metrics, request latency/error instrumentation, trace/tenant/user log context, and readiness/liveness probes
- Database tenant-integrity triggers and a PostgreSQL CI contract that rejects cross-tenant and cross-parent relationships
- Hardened security headers, graceful shutdown, production secret validation, pool/server limits, and no-store exports
- Reproducible PostgreSQL/MinIO backup and destructive-confirmation restore tooling
- Container-build CI, tagged GHCR release automation with SBOM/provenance, and automated dependency update configuration
- Controlled-pilot UAT, accessibility, load-test, backup/restore, rollback, and operations runbooks

Milestone 3 makes the codebase **GA-ready for controlled pilot validation**. Actual production sign-off still requires institution UAT evidence, an environment-specific security review, a successful restore drill, approved notification providers, and a named operating team.

## Milestone 4 delivered

- Removed all demo-data fallbacks from authenticated product screens; API failures now render explicit loading, empty, and error states
- Live signed-in identity and organisation context in the application shell
- Persisted Question Bank list/detail/create/edit/submit journeys backed by academic masters
- Persisted assessment list/create/review hand-off/publish/schedule lifecycle
- Live user and organisation administration with tenant-scoped academic structure
- End-to-end student assessment discovery, server attempt recovery, response saving, timed submission, and governed result viewing
- Institution-owned pilot evidence register with mandatory checks, tester/evidence/defect capture, locking sign-off, and immutable audit events
- Release 1.0 remains scope-frozen; Milestone 4 is a delivery-validation milestone, not a post–Release 1.0 feature expansion

Milestone 4 makes the interface suitable for genuine controlled-pilot execution. A deployment is not production-authorised until the institution records the mandatory evidence and completes sign-off in **Pilot readiness**.

## Milestone 4.1 release hardening delivered

- Failed-login counters persist despite rejected authentication transactions, with
  configurable threshold, timed lock, expiry reset, and concurrent row locking
- Invited users receive hashed, expiring, one-time activation links, set their own
  strong password, and activate both account and membership atomically
- Full-stack PostgreSQL smoke coverage proves invitation, first login, lockout, and
  timed unlock alongside the existing controlled-pilot journey
- Production npm advisories are resolved with tested transitive overrides
- GitHub Actions now enforce production dependency audit, API/web image SCA,
  full-history secret scanning, and critical dependency-review gates

Milestone 4.1 is release hardening only. It does not begin Milestone 5.

## Milestone 4.2 functional and UI completion

- Stable per-attempt question and option shuffle, with server-side expiry submission
  even after the student's browser closes
- Distinct Admin, Academic Head, Faculty, Reviewer, and Student dashboards and
  role-appropriate navigation/actions
- Consolidated student reporting with subject, assessment type, department, section,
  date, and text filters plus department/section comparisons
- Student subject/topic/difficulty/time analysis and published question review
- Teacher batch analytics, student comparison, weak-topic analysis, and PDF/Excel exports
- Manual answer review with bounded score updates, reason gate, versioning,
  publication reset, and an in-screen audit trail
- Student instructions and attempt history, plus live staff assessment monitoring
- Expanded Docker smoke coverage for timeout, monitoring, confidentiality,
  re-evaluation, republishing, history, and filtered reporting

Milestone 4.2 exact commit `b6f6715` has passed branch CI. It remains unpublished to
`main` while manual desktop/Android Docker acceptance is pending.

## Milestone 5 local pilot readiness

M5.1 tooling prepares a zero-cost local pilot host, generates protected secrets,
keeps PostgreSQL/Redis/RabbitMQ/MinIO Docker-internal, captures redacted preflight
evidence, and proves backups in temporary restore volumes. No cloud account,
managed database, public tunnel, or paid runtime is used. Actual M5.1 acceptance
still requires the designated computer, private LAN, separate backup device, and
named human owners. See [docs/MILESTONE_5.md](docs/MILESTONE_5.md) and the binding
[local infrastructure policy](docs/LOCAL_INFRASTRUCTURE_POLICY.md).

M5.2 adds a local, read-only browser evidence runner for role routes,
accessibility, responsive overflow, reduced motion, and screenshots. It does not
replace signed state-changing journey checks or physical Android/Edge review. See
[docs/M5_2_UI_VALIDATION.md](docs/M5_2_UI_VALIDATION.md).

M5.3 adds local approved-load, security, isolated functional-recovery, and
rollback-tabletop evidence. It uses a protected non-demo account file, a separate
local backup device, temporary project-scoped restore volumes, and exact local
release images. It does not use CI or cloud infrastructure and never restores
into the live pilot volumes. See [docs/M5_3_VALIDATION.md](docs/M5_3_VALIDATION.md).

M5.4 adds local rehearsal/live freeze and reconciliation evidence. It proves the
exact cohort, content, release, attendance, attempts, publication, exports,
audit, and incident closure without mutating the event or using a cloud evidence
link. See [docs/M5_4_PILOT_EXECUTION.md](docs/M5_4_PILOT_EXECUTION.md).

M5.5 adds an immutable Go, Conditional Retest, or No-Go decision plus a local
prepare/finalize handover workflow. It binds the signed institutional acceptance,
exact release, readiness snapshot, current backup, incidents, owned S3/S4 issues,
named operating owners, local media, audit event, and no-cloud attestations into
checksummed local evidence. Only Go locks the register; automation never chooses
or records the institution's decision. See
[docs/M5_5_APPROVAL_HANDOVER.md](docs/M5_5_APPROVAL_HANDOVER.md).

## Run with Docker

For ordinary loopback-only development:

```bash
cp .env.example .env
docker compose up --build
```

Open:

- Portal: `http://localhost`
- API health: `http://localhost/api/actuator/health`
- Operations console: `http://localhost/operations`

PostgreSQL, Redis, RabbitMQ, and MinIO have no host-published ports in the base
stack. They remain reachable to the backend through Docker's internal data
network.

For the controlled local pilot:

```bash
# Omit the argument for loopback-only use, or provide the host's approved
# private-LAN IPv4 address for same-Wi-Fi access.
./infra/pilot/prepare-local-env.sh [192.168.x.x]
# Fill the PILOT_* owner and separate-backup placeholders in .env.
make pilot-up
# Invite and activate a real Organisation Admin, verify that login, then retire
# the four seeded users as described in docs/OPERATIONS_RUNBOOK.md.
make pilot-preflight
```

For Android pilot testing on the same trusted Wi-Fi, generate `.env` with the
laptop's explicit private-LAN address and open that same address in Chrome. The
pilot generator rejects public IPs and `0.0.0.0`. Local HTTP intentionally uses
`SESSION_COOKIE_SECURE=false`; no port forwarding or public tunnel is allowed.

After M5.1 host readiness and M5.2 journey preparation, configure and run the
local M5.3 technical evidence workflow during a maintenance window:

```bash
cp .env.pilot-m5-3.example .env.pilot-m5-3
chmod 600 .env.pilot-m5-3
# Replace every placeholder, then:
make pilot-m5-3
```

Demo accounts use the password `Rabbit@123`:

| Role | Email |
| --- | --- |
| Organisation Admin | `admin@demo.rabbit.local` |
| Faculty | `faculty@demo.rabbit.local` |
| Reviewer | `reviewer@demo.rabbit.local` |
| Student | `student@demo.rabbit.local` |

The demo organisation code is `DEMO`.

## Local development

Requirements: Node.js 22+, npm 10+, Java 17+, Maven 3.9+, PostgreSQL 16+, Redis 7+, RabbitMQ 4+, and an S3-compatible MinIO service.

```bash
# Frontend
cd frontend
npm install
npm run dev

# Backend (separate terminal)
cd backend
mvn spring-boot:run
```

For local processes outside Docker, start the dependencies with:

```bash
docker compose \
  -f docker-compose.yml \
  -f infra/development/compose.host-ports.yml \
  up postgres redis rabbitmq minio
```

That optional development override binds dependency and administration ports to
`127.0.0.1` only. It is not used for the pilot.

## Database mode

Milestone 5 uses the bundled local PostgreSQL 16 container and persistent
`postgres-data` Docker volume. The backend connection is environment-configured,
so a future approved move to another PostgreSQL instance can use
`DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` without product-code
changes. No external database is enabled by default.

See [Database portability](docs/DATABASE_PORTABILITY.md) for the migration and
rollback procedure. PostgreSQL is the supported Release 1.0 engine; changing to a
different database engine requires vendor-specific migrations and tenant-safety
validation. The dormant external-PostgreSQL environment and Compose override are
not loaded by the normal local startup command.

## Repository map

```text
Rabbit/
├── frontend/            Next.js portal and assessment player
├── backend/             Spring Boot API and database migrations
├── infra/               Reverse proxy, backup, deployment, database, and load tools
├── docs/                Architecture, milestones, API, and traceability
├── .github/workflows/   Continuous integration
└── docker-compose.yml   Complete local environment
```

## Source of truth and scope resolution

The implementation is derived from:

- `Rabbit-AiP-BRD-v10.pdf`
- `Rabbit-AiP-PRD-v10.pdf`
- `Rabbit-AiP-FSD-v10.pdf`

Where the older PRD roadmap mentions AI in its third milestone, the CPO-approved FSD's explicit Release 1.0 constraint wins: **there are no AI features in Release 1.0**. AI capabilities are treated as post–Release 1.0.

See [docs/MILESTONES.md](docs/MILESTONES.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/REQUIREMENTS_TRACEABILITY.md](docs/REQUIREMENTS_TRACEABILITY.md), and [docs/UAT_CHECKLIST.md](docs/UAT_CHECKLIST.md) for the implementation boundary and controlled-pilot exit gates.
