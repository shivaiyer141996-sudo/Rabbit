# Release 1.0 requirements traceability

Status meanings:

- **Implemented** — working code, API/interface, and relevant automated evidence exist.
- **Provider gate** — the application control exists; a production provider and operating approval are environment-specific.
- **Pilot evidence** — repository tooling exists; institutional execution/sign-off is still required.
- **Post–Release 1.0** — explicitly excluded from this release.

| Requirement group | Status | Evidence |
| --- | --- | --- |
| AUTH login, tenant selection, refresh, logout, lockout | Implemented | JWT gateway, rotating hashed refresh tokens, BCrypt, login audit, rate limits |
| AUTH password recovery/provider delivery | Provider gate | Recovery interface and notification foundation; provider/consent approval required before activation |
| ORG tenant isolation | Implemented | Signed tenant scope, tenant repositories, PostgreSQL relationship triggers, negative CI contract |
| ORG academic structure | Implemented | Organisation, subject, topic, section, and academic-year foundation |
| ORG live academic catalog | Implemented | Role-safe catalog API and live question/assessment/user/organisation interfaces |
| USR role/status management | Implemented | Membership roles, method security, account state APIs |
| USR/QB bulk import | Provider gate | `BULK_IMPORTS` defaults off pending institution template/data-governance approval |
| DSH role-specific intelligence | Implemented | Role dashboard API and responsive intelligence interface |
| QB Single/Multiple Correct MCQ | Implemented | Authoring, validation, versioning, bank, and database type constraint |
| QB rich assets | Implemented | MinIO-ready asset service boundary and backup coverage; pilot validates final media limits |
| QRV/ASM governed approval | Implemented | Checklist/history, creator-reviewer separation, review queues, 48-hour operations signal |
| DEL eligibility, recovery, timer, submit | Implemented | Student player, tenant/section/window checks, persisted response recovery, idempotent submit |
| DEL live monitoring | Implemented | Tenant operations console exposes active attempts and workload |
| EVL objective scoring and re-evaluation | Implemented | Both supported MCQ types, marking rules, grades, versioned governed recalculation |
| EVL result publication | Implemented | Pending/published states and explicit faculty/admin publication |
| RPT institution/assessment/student/faculty/question reports | Implemented | Published-data-only APIs and responsive interfaces |
| RPT CSV, PDF, and Excel | Implemented | Audited downloads; valid multi-page PDF and native three-sheet XLSX tests |
| NTF in-app centre and retry state | Implemented | Inbox, preferences, critical override, worker state |
| NTF email/SMS adapters | Provider gate | `EXTERNAL_DELIVERY` defaults off pending approved provider and monitoring |
| ADM immutable audit | Implemented | Actor, role, IP, trace, before/after, filters, CSV, feature/export events |
| SET settings, grading, academic masters | Implemented | Validated APIs/UI and contiguous 0–100 grade tests |
| OPS observability and readiness | Implemented | Liveness/readiness, Prometheus, dependency probes, traffic/capacity/backlogs |
| OPS rate limiting and security headers | Implemented | Redis + local fallback, Nginx edge zones, secure headers, production secret guard |
| OPS feature rollout | Implemented | Tenant flags, deterministic percentage bucket, UI, audit |
| OPS backup, restore, rollback | Pilot evidence | Checksum backup/restore scripts and runbook; quarterly drill must be evidenced |
| NFR scale | Pilot evidence | k6 thresholds, pool/server limits, container-build gate; execute at target pilot load |
| Accessibility | Pilot evidence | Skip navigation, focus state, semantic labels, reduced motion, responsive/print views; human audit required |
| PIL controlled-pilot evidence and sign-off | Implemented | Tenant-scoped check register, evidence/defect capture, mandatory gate, locked sign-off, audit trail |
| PIL institutional execution | Pilot evidence | Named institution must perform checks and provide genuine evidence before sign-off |
| Release 1.0 exclusions | Post–Release 1.0 | AI, predictive models, parent portal, subjective grading, native app, proctoring, LMS/ERP/SIS/payment integrations |
