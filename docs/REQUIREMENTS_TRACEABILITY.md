# Release 1.0 requirements traceability

Status meanings:

- **Implemented** — working code, API/interface, and relevant automated evidence exist.
- **Provider gate** — the application control exists; a production provider and operating approval are environment-specific.
- **Pilot evidence** — repository tooling exists; institutional execution/sign-off is still required.
- **Post–Release 1.0** — explicitly excluded from this release.

| Requirement group | Status | Evidence |
| --- | --- | --- |
| AUTH login, tenant selection, refresh, logout, lockout | Implemented | JWT gateway, rotating hashed refresh tokens, BCrypt, independently committed/row-locked failure counters, configurable lock expiry, full-stack PostgreSQL smoke |
| AUTH invited-user activation | Implemented | Hashed expiring one-time tokens, strong password set, atomic account/membership activation, first-login E2E |
| AUTH password recovery/provider delivery | Provider gate | Recovery interface and notification foundation; provider/consent approval required before activation |
| ORG tenant isolation | Implemented | Signed tenant scope, tenant repositories, PostgreSQL relationship triggers, negative CI contract |
| ORG academic structure | Implemented | Organisation, subject, topic, section, and academic-year foundation |
| ORG live academic catalog | Implemented | Role-safe catalog API and live question/assessment/user/organisation interfaces |
| USR role/status management | Implemented | Membership roles, method security, account state APIs, invitation issue/reissue controls |
| USR/QB bulk import | Provider gate | `BULK_IMPORTS` defaults off pending institution template/data-governance approval |
| DSH role-specific intelligence | Implemented | Distinct Admin, Academic Head, Faculty, Reviewer, and Student metrics, actions, queues, and role-safe navigation |
| QB Single/Multiple Correct MCQ | Implemented | Authoring, validation, versioning, bank, and database type constraint |
| QB rich assets | Implemented | MinIO-ready asset service boundary and backup coverage; pilot validates final media limits |
| QRV/ASM governed approval | Implemented | Checklist/history, creator-reviewer separation, review queues, 48-hour operations signal |
| DEL eligibility, recovery, timer, submit | Implemented | Instructions, tenant/section/window checks, stable per-attempt shuffle, persisted recovery, row-locked saves, server expiry worker, idempotent submit, and attempt history |
| DEL live monitoring | Implemented | Dedicated assessment monitor shows persisted answer progress, attempt state, and remaining server time |
| EVL objective scoring and re-evaluation | Implemented | Both supported MCQ types, marking rules, grades, automatic recalculation, manual question-level score review, reason gate, versioning, publication reset, and attempt audit trail |
| EVL result publication | Implemented | Pending/published states and explicit faculty/admin publication |
| RPT institution/assessment/student/faculty/question reports | Implemented | Published-data-only APIs/UI, consolidated filters, subject/topic/difficulty/time/question student drill-down, teacher batch/student/weak-topic analytics, and department/section comparison |
| RPT CSV, PDF, and Excel | Implemented | Audited downloads; assessment PDF/three-sheet XLSX and teacher PDF/four-sheet XLSX |
| NTF in-app centre and retry state | Implemented | Inbox, preferences, critical override, worker state |
| NTF email/SMS adapters | Provider gate | `EXTERNAL_DELIVERY` defaults off pending approved provider and monitoring |
| ADM immutable audit | Implemented | Actor, role, IP, trace, before/after, filters, CSV, feature/export events |
| SET settings, grading, academic masters | Implemented | Validated APIs/UI and contiguous 0–100 grade tests |
| OPS observability and readiness | Implemented | Liveness/readiness, Prometheus, dependency probes, traffic/capacity/backlogs |
| OPS rate limiting and security headers | Implemented | Redis + local fallback, Nginx edge zones, secure headers, production secret guard |
| OPS software composition and secret assurance | Implemented | Clean production npm audit, Trivy API/web image SCA, dependency-review critical gate, Gitleaks history scan |
| OPS feature rollout | Implemented | Tenant flags, deterministic percentage bucket, UI, audit |
| OPS backup, restore, rollback | Pilot evidence | Quiesced checksummed backup, source reconciliation, isolated functional restore with RPO/RTO, and named rollback tabletop; execute on the pilot host |
| NFR scale | Pilot evidence | Configurable k6 profile at approved concurrency plus 50% headroom with fixed error/p95/p99/check thresholds; execute on the pilot host |
| Accessibility | Pilot evidence | Skip navigation, focus state, semantic labels, reduced motion, responsive/print views; human audit required |
| PIL controlled-pilot evidence and final decision | Implemented | Tenant-scoped check register, local evidence capture, immutable Go/Conditional Retest/No-Go history, future retest gate, database-enforced decision immutability, Go-only sign-off lock, and audit trail |
| PIL institutional execution | Pilot evidence | Local rehearsal/live freeze and reconciliation tooling covers release, cohort, content, attendance, attempts, publication, exports, audit, and incidents; named institution must execute and accept both events |
| PIL institutional approval and handover | Pilot evidence | Local M5.5 prepare/finalize tooling binds signed acceptance, exact release, readiness, backup, incidents, owned S3/S4 issues, operating owners, local media, decision, and audit; named institution must sign and choose the outcome |
| REL Release 1.0 closure | Pilot evidence | M5.6 requires exact immutable Go evidence, mandatory local URNs, live lock/health, OCI commit labels, seven-image local export, fast-forward main, annotated tag, and checksummed prepare/final bundles; human/Git host execution remains required |
| COM Basic/Pro/Legend catalogue and entitlements | Implemented, activation gated | Exact ₹599/₹999/₹1,499 Basic, ₹899/₹1,399/₹1,899 Pro, and ₹1,499/₹1,999/₹2,499 Legend matrix locked in Java/PostgreSQL; server-side action/report/capacity gates and role-safe navigation |
| COM 20-day trial and subscription validity | Implemented, activation gated | One-time Legend trial, exact database duration, server expiry, read-only expired state, immediate upgrade and end-of-period renewal/downgrade |
| COM onboarding and manual billing | Implemented, activation gated | Atomic organisation/admin/default/trial onboarding; tenant invoices, exact offline payments, receipts, immutable subscription events, audit |
| SUP local support administration | Implemented, activation gated | Tenant S1–S4 case register, assignment, response target, governed resolution/closure, no external CRM |
| Release 1.0 exclusions | Post–Release 1.0 | AI, predictive models, parent portal, subjective grading, native app, proctoring, LMS/ERP/SIS/payment integrations remain outside the currently authorised Milestone 6 boundary |
# Post-M6 enhancement traceability

| Requirement | Primary implementation | Automated evidence |
|---|---|---|
| Multi-subject assessment creation | `Assessment`, `AssessmentService`, `assessment-author-form.tsx`, Flyway V10 | `enhancement-rules.test.ts`, enhancement contract |
| Review Select/Clear/Partial | `approval-workspace.tsx`, existing `QuestionService` mandatory checklist | `enhancement-rules.test.ts`, enhancement contract |
| Student-only dashboard/navigation | `DashboardService`, `app-shell.tsx`, `intelligence-dashboard.tsx` | route-rule test, enhancement contract |
| Consistent assessment status | `StudentAssessmentClassifier`, `AttemptService` | `StudentAssessmentClassifierTest`, enhancement contract |
| Published student results/analytics | `AttemptService`, `ReportService`, result and analytics views | publication contract plus existing scoring/report tests |
| Section Management | academic section package, section UI, Flyway V10 | enhancement contract; PostgreSQL/Maven CI required |
| Tenant isolation/authorization | controller annotations, tenant repository lookups, V10 triggers | enhancement contract; authenticated E2E pending host |
