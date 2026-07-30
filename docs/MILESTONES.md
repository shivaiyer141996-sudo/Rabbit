# Rabbit AiP delivery milestones

The source documents contain two different uses of “milestone”: the product roadmap and the repository bootstrap discussed before coding. This repository treats the product roadmap as authoritative while delivering the engineering foundation needed to evolve it safely.

## Milestone 1 — Foundation

Product slices:

- Authentication and organisation selection
- Organisation and user foundations with role-based access
- Question bank and MCQ authoring
- Minimal governed review/approval needed to uphold “approved questions only”
- Assessment creation, publishing, scheduling, and eligibility
- Student assessment player and response recovery
- Objective scoring and basic result views

Engineering slices:

- Next.js frontend and Spring Boot backend
- PostgreSQL migrations and seed data
- Redis-ready caching/session infrastructure
- RabbitMQ-ready asynchronous event infrastructure
- MinIO-ready question asset storage
- Nginx reverse proxy, Docker Compose, CI, tests, and documentation

The code is a functional foundation, not a claim that every FSD screen and every production NFR is GA-complete. The traceability document labels what is implemented now and what needs hardening.

## Milestone 2 — Intelligence (delivered)

- Rich role-specific dashboards and analytics
- Full evaluation administration and governed re-evaluation
- Assessment, student, faculty, question, and institution reports
- Notification centre and delivery workers
- Immutable audit exploration and export
- Organisation settings and master-data administration
- Full question and assessment approval work queues

Implementation boundary:

- Evaluation remains objective MCQ evaluation only. “Manual evaluation” in an older roadmap is superseded by the approved Release 1.0 constraint excluding subjective evaluation.
- Academic intelligence is deterministic and rules-based. Predictive models and AI recommendations remain post–Release 1.0.
- Milestone 2 exports operational CSV files. Complete styled PDF and native Excel generation remains Milestone 3.
- In-app notifications are fully represented. Email/SMS adapters require environment-specific providers during GA hardening.

## Milestone 3 — Release 1.0 GA readiness (delivered)

- Performance, security, accessibility, backup, and disaster-recovery hardening
- Complete PDF/Excel exports and tenant operational dashboards
- Production observability, dual-layer rate limiting, audited feature flags, and tagged image automation
- PostgreSQL cross-tenant integrity contracts, container builds, and a repeatable k6 load profile
- Controlled-pilot UAT checklist, release gates, rollback procedure, and operating runbook

Delivery boundary:

- The implementation and automated gates are complete in the repository.
- A controlled institutional pilot is an environment-and-people activity. It is not claimed complete until the UAT checklist, restore drill, load evidence, accessibility review, and security review are signed off.
- Email/SMS delivery remains feature-flagged off until an institution approves a provider, credentials, consent wording, monitoring, and escalation ownership.

## Milestone 4 — Controlled pilot execution (delivered)

This is an engineering delivery milestone after the three-stage product roadmap. It
does not change the frozen Release 1.0 product scope.

- Replace authenticated preview fallbacks with explicit live loading, empty, and
  failure states
- Connect the question, assessment, user, organisation, student attempt, and result
  interfaces to their tenant-scoped APIs
- Restore student attempts and answers from the server, persist every response, and
  submit through the objective evaluation workflow
- Expose the live academic catalog to authorised staff without granting settings
  administration
- Record every UAT row with result, tester, evidence, defect, notes, and timestamp
- Block institutional sign-off until all mandatory checks pass; lock and audit the
  evidence register after sign-off

Delivery boundary:

- Repository automation verifies the code and database contract.
- Institutions still own the evidence itself: accessibility review, security review,
  load evidence, restore evidence, named operators, and written authorisation cannot
  be truthfully manufactured by the application.
- Post–Release 1.0 capabilities remain excluded.

## Post–Release 1.0

- AI-assisted question generation or recommendation
- Predictive intervention and adaptive learning
- Parent portal
- Subjective evaluation
- Native mobile apps
- Video proctoring
- LMS, ERP, SIS, and payment integrations
