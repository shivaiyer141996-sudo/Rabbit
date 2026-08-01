# Rabbit AiP

Rabbit is a **Student Progress & Academic Intelligence Platform**. Assessments are the mechanism; student success is the outcome.

This repository contains the Milestone 1 Foundation, Milestone 2 Intelligence, Milestone 3 GA-readiness, and Milestone 4 controlled-pilot implementation for Rabbit AiP Release 1.0. Release 1.0 is deliberately limited to web-based **Single Correct MCQ** and **Multiple Correct MCQ** workflows. AI, parent access, subjective evaluation, native mobile apps, proctoring, and LMS/ERP/SIS integrations are excluded.

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
- Reason-gated re-evaluation controls with version and publication-state visibility
- Student instructions and attempt history, plus live staff assessment monitoring
- Expanded Docker smoke coverage for timeout, monitoring, confidentiality,
  re-evaluation, republishing, history, and filtered reporting

Milestone 4.2 remains un-published while branch CI and manual desktop/Android Docker
acceptance are pending. It does not begin Milestone 5 or expand the frozen MCQ scope.

## Run with Docker

```bash
cp .env.example .env
docker compose up --build
```

Open:

- Portal: `http://localhost`
- API health: `http://localhost/api/actuator/health`
- Operations console: `http://localhost/operations`
- RabbitMQ console: `http://localhost:15672`
- MinIO console: `http://localhost:9001`

For Android pilot testing on the same Wi-Fi, open
`http://<laptop-ip-address>` in Chrome instead of `localhost`. The local
environment intentionally sets `SESSION_COOKIE_SECURE=false` for HTTP access;
deployments behind HTTPS must use the production value `true`.

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
docker compose up postgres redis rabbitmq minio
```

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
