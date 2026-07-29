# Milestone 1 requirements traceability

Status meanings:

- **Implemented** — a working vertical slice exists in this repository.
- **Foundation** — core domain/API/UI exists; the full screen set or operational hardening continues later.
- **Planned** — deliberately outside this Milestone 1 commit.

| Requirement group | Status | Evidence |
| --- | --- | --- |
| AUTH login and role redirect | Implemented | Auth API, JWT service, login gateway and screen |
| AUTH organisation selector | Implemented | Selection token flow and organisation selector |
| AUTH forgot password / OTP | Planned | Milestone 2 notification delivery dependency |
| AUTH lockout and password history | Foundation | Schema fields and policy configuration; full admin unlock UI planned |
| ORG tenant isolation | Implemented | Tenant claim/context and tenant-scoped service queries |
| ORG academic structure | Foundation | Schema and seed for academic years, departments, sections, subjects and topics |
| USR role model and suspension | Implemented | Membership roles/status and method security |
| USR CSV bulk import | Planned | Milestone 2 administration workflow |
| DSH contextual experience | Implemented | Role-aware application shell and Foundation dashboard |
| QB Single Correct / Multiple Correct | Implemented | Authoring UI, entity model, validation and API |
| QB rich text and images | Foundation | Asset-ready schema/MinIO; advanced editor upload planned |
| QB bulk import | Planned | Milestone 2 |
| QB versioning and retirement | Foundation | Version/status model; full history UI planned |
| QRV submit and decision | Implemented | Question state transitions and reviewer endpoint |
| QRV checklist/SLA/escalation | Planned | Milestone 2 |
| ASM create from approved questions | Implemented | Service invariant and assessment form |
| ASM publish and schedule | Implemented | API transitions and eligibility model |
| ASM full approval/version history | Foundation | Status/version model; complete work queue planned |
| DEL scheduled eligibility | Implemented | Tenant, membership section, schedule window checks |
| DEL countdown/navigation/flags | Implemented | Student assessment player |
| DEL 30-second auto-save and recovery | Implemented | Client interval plus persisted response API |
| DEL live monitoring | Planned | Milestone 2; alerts are explicitly out of Release 1.0 |
| EVL objective scoring/basic results | Implemented | Scoring service, submission endpoint and result view |
| EVL governed re-evaluation | Planned | Milestone 2 |
| RPT advanced reports | Planned | Milestone 2 |
| NTF delivery engine | Foundation | RabbitMQ infrastructure; producers/workers planned |
| ADM immutable audit viewer | Foundation | Audit table; full event coverage/viewer planned |
| SET configuration screens | Planned | Milestone 2 |
| No AI / no parent / no subjective / no app | Enforced | Types, routes and roadmap intentionally exclude them |
