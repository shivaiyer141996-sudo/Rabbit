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
| PIL controlled-pilot evidence and sign-off | Implemented | Tenant-scoped check register, web/local-URN evidence capture, defect capture, mandatory gate, locked sign-off, audit trail |
| PIL institutional execution | Pilot evidence | Local rehearsal/live freeze and reconciliation tooling covers release, cohort, content, attendance, attempts, publication, exports, audit, and incidents; named institution must execute and accept both events |
| Release 1.0 exclusions | Post–Release 1.0 | AI, predictive models, parent portal, subjective grading, native app, proctoring, LMS/ERP/SIS/payment integrations |
