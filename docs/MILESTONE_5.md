# Milestone 5 — Institutional pilot and release acceptance

## Status

**M5.0 pilot preparation is in progress.** Milestone 5 is an environment, people,
and evidence milestone. It does not add product scope.

- Release candidate: `b6f6715`
- Milestone 4.2 automated gates: passed on GitHub Actions run `30699322752`
- `main`: remains at `2aaa056`
- Blocking release evidence: human desktop and 360 px Android visual acceptance
- Blocking pilot decisions: institution, named operating owners, final dates, and
  approved infrastructure budget

No production account, paid service, DNS record, certificate, or institution data
may be created until the relevant owner approves the decision register below.

## Release 1.0 scope freeze

The pilot validates the completed web product only:

- Single Correct and Multiple Correct MCQ authoring and governance
- Assessment creation, review, approval, scheduling, delivery, and submission
- Objective evaluation, governed manual mark correction, publication, and audit
- Admin, Academic Head, Teacher, Reviewer, and Student workspaces
- Student and teacher analytics plus PDF/Excel exports
- Tenant administration, operations, recovery, and pilot evidence capture

The pilot excludes AI, subjective answers, parents, native apps, proctoring,
payments, and LMS/ERP/SIS integrations. New feature requests enter the post-pilot
backlog and cannot delay Milestone 5. Only release-blocking defects may change the
release candidate during the pilot.

## Proposed pilot baseline

| Decision | Proposed baseline | Status / owner |
| --- | --- | --- |
| Institution | One Chennai coaching centre, tuition centre, school, or training institute with a willing academic owner | **TBD — Shiva to confirm** |
| Cohort | 1 Admin, up to 3 Teachers, 1 Reviewer, and 30 Students; hard pilot cap of 50 total active users | Proposed |
| Academic content | 2 subjects, at least 4 topics, and at least 100 approved MCQs | Proposed with institution |
| Assessments | One staff rehearsal plus one live student assessment covering the full question-to-report journey | Proposed |
| Live assessment size | 30–45 questions, 45–60 minutes, maximum 30 concurrent students in the first live run | Proposed with institution |
| Hosting | AWS Lightsail Linux instance in Mumbai; begin with 4 GB RAM, 2 vCPU, 80 GB SSD, then move to 8 GB only if the pilot load gate fails | Proposed |
| Monthly infrastructure ceiling | ₹3,500 for the 4 GB pilot baseline; contingency ceiling ₹5,500 if 8 GB is required, including backup allowance and taxes/FX variance | **TBD — Shiva to approve** |
| Public access | Institution-approved subdomain, HTTPS only, secure cookies, no direct database/object-store/admin-console exposure | Proposed |
| Notifications | In-app notifications enabled; provider-backed email and SMS disabled; activation URLs shared manually through an institution-approved channel | Recommended default |
| Data | Synthetic/rehearsal data first; minimum necessary real student data for the live assessment; no sensitive documents | Proposed with institution |
| Support window | Named technical and institution contacts available from 30 minutes before until 60 minutes after the live assessment | **TBD — owners required** |

The 4 GB Lightsail bundle is currently listed at USD 20 per month. At the
1 August 2026 reference rate of approximately ₹95.4/USD, compute is roughly
₹1,910 before tax, snapshots, off-host backup storage, domain, and FX charges.
Pricing and regional availability must be rechecked immediately before purchase.

## Proposed dates

These dates are a working baseline and become binding only after the institution
and owners confirm availability.

| Phase | Proposed window | Exit result |
| --- | --- | --- |
| M5.0 — Finalise plan | 6–7 August 2026 | Decisions, owners, scope, cohort, and dates approved; M4.2 visual acceptance recorded |
| M5.1 — Pilot environment | 10–12 August 2026 | Secure online environment, backup, monitoring, and rollback ready |
| M5.2 — Journey/UI validation | 13–17 August 2026 | Critical role journeys pass on desktop and Android; accessibility evidence recorded |
| M5.3 — Performance/security/recovery | 18–20 August 2026 | Pilot load, security review, backup and restore evidence pass |
| M5.4 — Institutional pilot | 21–28 August 2026 | Rehearsal and one complete live assessment executed |
| M5.5 — Approval and handover | 31 August–2 September 2026 | Institution acceptance, known issues, ownership, and Go/No-Go recorded |

## Required owners

Operational responsibility must remain with named people. An automated agent cannot
serve as an on-call, security, rollback, or institutional acceptance owner.

| Responsibility | Proposed owner | Confirmation |
| --- | --- | --- |
| Product and budget decision | Shiva Iyer | Pending |
| Institution sponsor / authorised signatory | TBD | Required |
| Institution UAT lead | TBD | Required |
| Technical deployment and release | TBD | Required |
| Live-assessment support and defect triage | TBD | Required |
| Backup restore and rollback | TBD | Required |
| Data/privacy approval | TBD institution representative | Required |

## M5.0 — Finalise the pilot plan

Exit criteria:

- The institution and authorised sponsor are named.
- User counts, subjects, assessment format, and live date are confirmed.
- The Release 1.0 scope freeze is accepted in writing.
- Hosting platform, budget ceiling, payer, domain, and data region are approved.
- Every operating responsibility has a reachable primary owner and backup.
- Email/SMS remains disabled unless a provider, credentials, consent wording,
  monitoring, and escalation owner are separately approved.
- Milestone 4.2 desktop and 360 px Android acceptance is recorded.

## M5.1 — Set up the pilot environment

Required evidence:

- Immutable release commit/image references and deployment record
- HTTPS certificate and external security-header verification
- Secrets stored outside Git; demo passwords replaced or demo accounts removed
- Firewall permits only required public ports; data services remain private
- Database and object backups written to a separate failure domain with checksums
- Monitoring/alert destinations and retention agreed by named owners
- Documented rollback to the last verified release and a maintenance-message owner
- Environment inventory, access register, and cost tracker

## M5.2 — Validate screens and user journeys

Test each role using real browser interactions, not API calls alone:

- Admin: organisation, users, masters, dashboards, reports, operations, pilot register
- Teacher: question lifecycle, assessment lifecycle, monitoring, reports, exports
- Reviewer: question and assessment work queues, return/approval separation
- Student: instructions, start/resume, save, flag, timer, submit, history, published result and analytics
- Re-evaluation: manual question review, bounded mark change, mandatory reason,
  pending-publication reset, audit history, and republish
- Desktop: current Chrome and Edge at 100% and 200% zoom
- Android: Chrome at 360 px and one representative physical device
- Accessibility: keyboard, focus, labels, contrast, reduced motion, and screen-reader
  spot checks on the critical journey

## M5.3 — Test performance, security, and recovery

Exit criteria:

- Load test represents the approved concurrent-student count plus 50% headroom.
- Error rate is below 1%, p95 API latency below 500 ms, and p99 below 1 second.
- No unresolved critical security finding or exposed secret exists.
- Backup manifest verifies and a restore succeeds in a separate non-production target.
- Restored login, assets, assessment, result, export, and audit data reconcile.
- Actual recovery stays within the four-hour RTO and 24-hour RPO.
- A rollback rehearsal identifies the exact owner, command, and communication path.

## M5.4 — Run the institutional pilot

1. Import/create the approved academic structure and minimal user cohort.
2. Conduct the staff rehearsal and resolve only Severity 1/2 blockers.
3. Freeze assessment content and publish the live schedule.
4. Confirm support contacts, incident channel, backup, monitoring, and rollback.
5. Run the complete assessment through result publication and reports.
6. Reconcile attendance, submissions, scoring, publication, exports, and audit events.
7. Record every issue with severity, owner, workaround, and due date.

## M5.5 — Final approval and handover

The pilot is **Go** only when:

- Every mandatory row in `/pilot-readiness` is passed with genuine evidence.
- No Severity 1 or Severity 2 defect remains open.
- The institution signs the locked acceptance record.
- Known Severity 3/4 issues have owners, workarounds, and target dates.
- Support, monitoring, backup, restore, incident, and rollback ownership is accepted.
- Actual monthly cost is within the approved ceiling.

Any failed mandatory condition produces a **No-Go** or a time-boxed conditional
retest. A successful pilot is not permission to add post–Release 1.0 scope.

## Severity and change policy

| Severity | Definition | Pilot action |
| --- | --- | --- |
| S1 | Security/data loss, cross-tenant exposure, or system unavailable during the live assessment | Stop pilot; rollback or restore; executive notification |
| S2 | Critical journey blocked with no safe workaround, wrong scoring, or result confidentiality failure | No-Go until fixed and retested |
| S3 | Non-critical function impaired with a safe workaround | Record owner and due date; pilot may continue with approval |
| S4 | Cosmetic, wording, or minor usability issue | Add to post-pilot backlog |

Every release-candidate change must identify the defect, affected journey, focused
retest, regression evidence, and exact deployed commit. Feature requests are not
eligible for release-candidate changes.

## Decision record to complete next

1. Institution name and authorised sponsor
2. Final cohort and subject/assessment details
3. AWS Lightsail and ₹3,500 monthly baseline approval or alternate platform/budget
4. Domain/subdomain and payer/account owner
5. Named technical, support, rollback, UAT, and data/privacy owners
6. Confirmed dates and live assessment time
7. Explicit confirmation that email/SMS stays disabled

## Reference pricing

- [AWS Lightsail pricing](https://aws.amazon.com/lightsail/pricing/)
- [AWS Lightsail billing guidance](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-frequently-asked-questions-faq-billing-and-account-management.html)
