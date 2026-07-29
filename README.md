# Rabbit AiP

Rabbit is a **Student Progress & Academic Intelligence Platform**. Assessments are the mechanism; student success is the outcome.

This repository contains the Milestone 1 Foundation and Milestone 2 Intelligence implementation for Rabbit AiP Release 1.0. Release 1.0 is deliberately limited to web-based **Single Correct MCQ** and **Multiple Correct MCQ** workflows. AI, parent access, subjective evaluation, native mobile apps, proctoring, and LMS/ERP/SIS integrations are excluded.

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

## Run with Docker

```bash
cp .env.example .env
docker compose up --build
```

Open:

- Portal: `http://localhost`
- API health: `http://localhost/api/actuator/health`
- RabbitMQ console: `http://localhost:15672`
- MinIO console: `http://localhost:9001`

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
├── infra/nginx/         Reverse proxy configuration
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

See [docs/MILESTONES.md](docs/MILESTONES.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), and [docs/REQUIREMENTS_TRACEABILITY.md](docs/REQUIREMENTS_TRACEABILITY.md) for the implementation boundary and remaining GA hardening.
