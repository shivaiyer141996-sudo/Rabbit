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

## Milestone 4.1 — Release hardening (delivered)

Milestone 4.1 contains only the blocking findings from the Milestone 4 technical
review:

- Persist failed credential counters in an independent row-locked transaction,
  lock at the configured threshold, and reset cleanly after the configured expiry
- Issue cryptographically random, hashed, expiring, one-time invitation tokens
- Let invited users activate their account, set a strong password, and transition
  both account and membership from `INVITED` to `ACTIVE` atomically
- Consume the first-login marker exactly once and cover invitation, lockout, lock
  expiry, and first login in the full-stack PostgreSQL smoke journey
- Override vulnerable production `postcss` and `sharp` transitive versions and
  enforce a clean `npm audit --omit=dev`
- Add Gitleaks secret scanning, Trivy production-image SCA, and critical-severity
  dependency-review failure gates to GitHub Actions

Delivery boundary:

- This hardening release does not add Milestone 5 or post–Release 1.0 product scope.
- External email/SMS delivery remains provider-gated. An authorised administrator
  receives the one-time activation URL and shares it through an approved channel.
- Institution-owned pilot evidence and production operating approval remain required.

## Milestone 4.2 — Functional and UI completion (automated verification passed)

Milestone 4.2 closes the functional gaps identified by the post–Milestone 4.1 UI and
workflow review:

- Make configured question/option shuffle effective and stable for an attempt
- Auto-submit and evaluate expired attempts on the server independently of the browser
- Provide distinct dashboards, actions, and navigation for every Release 1.0 role
- Add consolidated filtered student reports, department/section comparisons, and
  student subject/topic/difficulty/time/question analysis
- Add ownership-scoped teacher batch analytics, student comparison, weak-topic
  analysis, and dedicated PDF/Excel exports
- Expose automatic recalculation plus reason-gated manual question review, bounded
  score updates, evaluation version, publication state, and attempt audit history
- Add student instructions/history and a dedicated live assessment monitor
- Expand the full-stack smoke journey over the completed delivery/reporting workflow

Delivery boundary:

- Exact commit `b6f6715` passed backend, frontend, PostgreSQL V1–V7, security,
  image, live-stack, and authenticated journey gates in CI run `30699322752`.
- Manual desktop and 360 px Android Docker acceptance remains institution-owned and is
  required before the milestone is published to `main`.
- AI, subjective assessment, proctoring, native apps, and external integrations remain
  outside Release 1.0.

## Milestone 5 — Institutional pilot and release acceptance (M5.1–M5.5 tooling ready)

- Finalise the named institution, cohort, scope, hosting, budget, dates, and owners
- Deploy the approved release candidate to a secure production-like environment
- Validate every role journey on desktop and Android with accessibility evidence
- Prove target performance, environment security, backup restore, and rollback
- Run a staff rehearsal and at least one complete institutional assessment
- Record written acceptance, known issues, support ownership, and a Go/No-Go decision
- Operate entirely on the approved local Docker Compose host with PostgreSQL,
  Redis, RabbitMQ, and MinIO; no cloud or paid runtime is permitted

Delivery boundary:

- Milestone 5 is an environment, people, and evidence milestone; it does not add
  product features or expand the frozen Release 1.0 MCQ scope.
- M5.0 preparation may continue while Milestone 4.2 visual acceptance is pending,
  but no production deployment or live pilot may start before that gate passes.
- Institution decisions and human operating ownership cannot be inferred or replaced
  by repository automation. See [MILESTONE_5.md](MILESTONE_5.md).
- Local environment tooling is implemented, but M5.1 does not pass until the
  designated host, private LAN, separate backup device, and named owners produce
  genuine preflight and restore evidence.
- Local browser evidence tooling is implemented, but M5.2 does not pass until
  state-changing journeys, current Chrome/Edge, 200% browser zoom, accessibility,
  and one representative physical Android device have signed human evidence.
- Local M5.3 tooling now covers approved load plus 50% headroom, runtime/HTTP/image
  security, a quiesced and reconciled PostgreSQL/MinIO backup, isolated functional
  recovery with RPO/RTO measurement, and a named rollback tabletop. It does not
  pass until the designated host produces a clean bundle and named testers record
  the mandatory evidence. See [M5_3_VALIDATION.md](M5_3_VALIDATION.md).
- Local M5.4 tooling freezes each rehearsal/live event and reconciles the roster,
  attendance, attempts, evaluation, publication, exports, audit, and incident
  closure. It does not pass until the institution runs and accepts both events.
  See [M5_4_PILOT_EXECUTION.md](M5_4_PILOT_EXECUTION.md).
- Local M5.5 tooling prepares and finalizes checksummed approval/handover
  evidence, while Rabbit records immutable Go, Conditional Retest, or No-Go
  decisions with database and audit enforcement. Only a named institution can
  sign and record the outcome. See
  [M5_5_APPROVAL_HANDOVER.md](M5_5_APPROVAL_HANDOVER.md).

## Post–Release 1.0

- AI-assisted question generation or recommendation
- Predictive intervention and adaptive learning
- Parent portal
- Subjective evaluation
- Native mobile apps
- Video proctoring
- LMS, ERP, SIS, and payment integrations
