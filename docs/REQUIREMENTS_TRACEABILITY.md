# Milestone 2 requirements traceability

Status meanings:

- **Implemented** — a working backend/API/interface vertical slice exists.
- **Foundation** — the core workflow exists; production-scale or provider-specific hardening remains for Milestone 3.
- **GA planned** — deliberately reserved for Release 1.0 GA hardening.

| Requirement group | Status | Evidence |
| --- | --- | --- |
| AUTH login, tenant selection, refresh, logout | Implemented | Auth API, JWT gateway, authentication audit events |
| AUTH password recovery and password history | GA planned | Requires production email provider and final security policy |
| ORG tenant isolation | Implemented | Signed tenant context and tenant-scoped repositories across M1/M2 modules |
| ORG academic structure | Implemented | Subject/topic master APIs and settings interface |
| USR role/status management | Implemented | Membership roles, method security, account state APIs |
| USR CSV bulk import | GA planned | Validation and import hardening retained for M3 |
| DSH role-specific intelligence | Implemented | Dashboard API and responsive intelligence dashboard |
| QB Single/Multiple Correct MCQ | Implemented | Authoring domain, validation, versioning, bank |
| QB bulk import and rich asset editor | GA planned | MinIO foundation exists; operational workflow retained for M3 |
| QRV checklist and decision history | Implemented | Seven-point checklist, self-review block, review history table/API/UI |
| QRV SLA escalation | Foundation | Critical workflow notifications and queue age are represented; production SLA scheduler remains M3 |
| ASM governed approval | Implemented | Draft → Ready for Review → Approved → Published → Scheduled |
| ASM approved-question invariant | Implemented | Service validation and database relationships |
| DEL eligibility, recovery, timer, submit | Implemented | Student player and tenant/section/window checks |
| DEL live monitoring | GA planned | Operational scale dashboard retained for M3 |
| EVL objective auto-evaluation | Implemented | Negative/partial marking, grade bands, counts, evaluation timestamp/version |
| EVL result publication | Implemented | Pending/published states, faculty/admin action, student score gating |
| EVL governed re-evaluation | Implemented | Reason, before/after audit, version increment, publication reset |
| RPT institution overview | Implemented | Average, pass rate, completion, score distribution, trend, at-risk rule |
| RPT assessment/student/faculty reports | Implemented | Tenant/role-scoped APIs and responsive interfaces |
| RPT question analytics | Implemented | Usage, correct rate, difficulty and discrimination indices, quality flag |
| RPT CSV export | Implemented | Timestamped governed assessment and audit exports |
| RPT PDF/native Excel export | GA planned | Reserved for Milestone 3 per product roadmap |
| NTF in-app notification centre | Implemented | Inbox, unread state, preferences, critical override, event producers |
| NTF email/SMS adapters | Foundation | Preferences/status/retry model exists; production providers remain M3 |
| ADM immutable audit exploration | Implemented | Actor, role, IP, trace, filters, before/after, CSV; no mutation endpoints |
| SET organisation settings | Implemented | Locale, defaults, thresholds, retention, branding API/UI |
| SET contiguous grading bands | Implemented | Backend/frontend validation and tests enforce complete 0–100 coverage |
| SET academic masters | Implemented | Subject/topic APIs; subject-in-use guard |
| Release 1.0 exclusions | Enforced | No AI, parent portal, subjective evaluation, native app, or external LMS integration |
