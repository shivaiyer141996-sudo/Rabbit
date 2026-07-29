# Rabbit AiP delivery milestones

The source documents contain two different uses of “milestone”: the product roadmap and the repository bootstrap discussed before coding. This repository treats the product roadmap as authoritative while delivering the engineering foundation needed to evolve it safely.

## Milestone 1 — Foundation (this commit)

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

## Milestone 2 — Intelligence

- Rich role-specific dashboards and analytics
- Full evaluation administration and governed re-evaluation
- Assessment, student, faculty, question, and institution reports
- Notification centre and delivery workers
- Immutable audit exploration and export
- Organisation settings and master-data administration
- Full question and assessment approval work queues

## Milestone 3 — Release 1.0 GA

- Performance, security, accessibility, backup, and disaster-recovery hardening
- Complete PDF/Excel exports and operational dashboards
- Production observability, rate limiting, feature flags, and deployment automation
- Scale and isolation verification against Release 1.0 NFRs
- UAT closure and controlled institutional pilot

## Post–Release 1.0

- AI-assisted question generation or recommendation
- Predictive intervention and adaptive learning
- Parent portal
- Subjective evaluation
- Native mobile apps
- Video proctoring
- LMS, ERP, SIS, and payment integrations
