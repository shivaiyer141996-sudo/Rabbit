# Rabbit AiP Release 1.0 API

Base path: `/api/v1`

All tenant-owned endpoints derive the organisation from the signed access token. Client-provided tenant identifiers cannot override it.

## Authentication and foundation

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/login` | Verify credentials and return tokens or organisation choices |
| POST | `/auth/select-organisation` | Exchange a selection token for tenant-scoped tokens |
| POST | `/auth/refresh` | Rotate refresh token and issue a new access token |
| POST | `/auth/logout` | Revoke the current refresh token |
| POST | `/auth/invitations/validate` | Validate an expiring one-time invitation token |
| POST | `/auth/invitations/activate` | Set the invited user's password and atomically activate account and membership |
| GET | `/auth/me` | Return current user, organisation, and role |
| GET/POST | `/organisations` | Organisation administration |
| GET/POST | `/users` | List users or create an invitation with a one-time activation URL |
| POST | `/users/{id}/invitation` | Invalidate and replace an invited user's activation URL |
| PATCH | `/users/{id}/status` | Change membership state |
| GET | `/academic-catalog` | Role-safe academic years, departments, sections, subjects, and topics |

## Questions and approvals

| Method | Path | Purpose |
| --- | --- | --- |
| GET/POST | `/questions` | Search/list or author an MCQ |
| GET/PUT | `/questions/{id}` | Read or edit a draft/version |
| POST | `/questions/{id}/submit` | Move a draft to review |
| GET | `/questions/review-queue` | Return questions awaiting review |
| POST | `/questions/{id}/review` | Checklist-based approve, return, or reject |
| GET | `/questions/{id}/reviews` | Immutable decision history |

## Assessments and delivery

| Method | Path | Purpose |
| --- | --- | --- |
| GET/POST | `/assessments` | List or create a draft |
| POST | `/assessments/{id}/submit` | Submit a valid draft for independent review |
| GET | `/assessments/review-queue` | Return assessments awaiting approval |
| POST | `/assessments/{id}/review` | Approve, return, or reject |
| GET | `/assessments/{id}/reviews` | Immutable assessment decision history |
| POST | `/assessments/{id}/publish` | Publish an approved assessment |
| POST | `/assessments/{id}/schedule` | Define window and eligible sections |
| GET | `/student/assessments` | Eligible scheduled assessments |
| GET | `/student/assessments/{id}` | Instructions, delivery settings, server time, and attempt readiness |
| POST | `/student/assessments/{id}/attempts` | Start or resume an attempt |
| PUT | `/student/attempts/{id}/responses` | Upsert one response |
| POST | `/student/attempts/{id}/submit` | Submit and auto-evaluate |
| GET | `/student/attempts/history` | Current student's in-progress, submitted, and published attempt history |
| GET | `/student/results/{attemptId}` | Pending state or published detailed result |

Question and option ordering is deterministic per attempt when shuffle is enabled, so
refresh and resume preserve the same presentation. A server worker locks, evaluates,
and auto-submits expired attempts independently of the browser.

## Evaluation and intelligence

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/evaluation/assessments/{id}/results` | Evaluation and publication queue |
| GET | `/evaluation/assessments/{id}/monitor` | Live persisted attempt progress, state, and remaining server time |
| POST | `/evaluation/assessments/{id}/publish` | Publish all evaluated pending results |
| POST | `/evaluation/attempts/{id}/re-evaluate` | Governed MCQ re-evaluation with reason |
| GET | `/evaluation/attempts/{id}/review` | Manual answer, marks, and evaluation audit review |
| POST | `/evaluation/attempts/{id}/score` | Reason-gated question-level score update and version reset |
| GET | `/dashboard` | Role-specific metrics, trend, and attention data |
| GET | `/reports/overview` | Organisation intelligence overview |
| GET | `/reports/assessments/{id}` | Assessment/student/question report |
| GET | `/reports/students/me` | Current student progress report |
| GET | `/reports/students/me/analytics` | Current student's subject, topic, difficulty, time, and question analysis |
| GET | `/reports/students` | Consolidated students with subject, type, department, section, date, and text filters |
| GET | `/reports/students/{id}` | Role-scoped student report |
| GET | `/reports/students/{id}/analytics` | Staff drill-down into published student analytics |
| GET | `/reports/teacher` | Ownership-scoped batch, student-comparison, and weak-topic analytics |
| GET | `/reports/questions` | Question quality analytics |
| GET | `/reports/faculty` | Faculty contribution report |
| GET | `/reports/assessments/{id}/export` | Assessment CSV snapshot |
| GET | `/reports/assessments/{id}/export.pdf` | Styled governed PDF report |
| GET | `/reports/assessments/{id}/export.xlsx` | Native Excel workbook with summary, students, and questions |
| GET | `/reports/teacher/export.pdf` | Teacher analytics PDF for the authorised scope |
| GET | `/reports/teacher/export.xlsx` | Four-sheet teacher analytics workbook |

## Notifications, audit, and settings

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/notifications` | User inbox and unread count |
| PATCH | `/notifications/{id}/read` | Mark one notification read |
| PATCH | `/notifications/read-all` | Mark the inbox read |
| GET/PUT | `/notifications/preferences` | Channel and event preferences |
| GET | `/audit-events` | Filter tenant audit events |
| GET | `/audit-events/export` | Export filtered immutable events to CSV |
| GET | `/settings` | Settings, grades, subjects, and topics |
| PUT | `/settings/general` | Update organisation configuration |
| PUT | `/settings/grade-bands` | Replace contiguous 0–100 grade bands |
| POST | `/settings/subjects` | Create a subject |
| PATCH | `/settings/subjects/{id}/deactivate` | Deactivate unused subject |
| POST | `/settings/topics` | Create a topic |
| GET | `/operations/readiness` | Admin-only dependency, traffic, capacity, workflow, and GA gate snapshot |
| GET | `/feature-flags` | Admin-only tenant feature rollout state |
| PATCH | `/feature-flags/{key}` | Audit and update enabled state and rollout percentage |
| GET | `/pilot-readiness` | Tenant UAT evidence, summary, immutable decision history, and institutional Go sign-off |
| PUT | `/pilot-readiness/checks/{key}` | Record a pilot result, tester, evidence, defect, and notes |
| POST | `/pilot-readiness/decisions` | Record an immutable Go, Conditional Retest, or No-Go decision; only an eligible Go locks the register |
| POST | `/pilot-readiness/sign-off` | Compatibility alias using the same complete M5.5 decision payload and validation |

Passing pilot rows accept either an absolute HTTP/HTTPS evidence URL or a
checksummed local `urn:rabbit-evidence:...` reference. The local reference is the
default for Milestone 5 so no cloud evidence host or public endpoint is needed.
Final M5.5 decisions accept only a checksummed local `urn:rabbit-evidence:...`
reference and its 64-character SHA-256. Conditional Retest requires a future
deadline. Go additionally requires every mandatory row, accepted operating
ownership, local-only data/media confirmation, and the Release 1.0 scope freeze.

## Commercial readiness and local support

Commercial controls default off and require the final M5.6 local evidence reference
plus the exact release commit before activation.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/commercial-access` | Role-safe effective plan/status/entitlements for navigation and API clients |
| GET | `/commercial/catalog` | Approved Basic/Pro/Legend prices and entitlements |
| GET | `/commercial/overview` | Admin subscription, usage, event, invoice, payment, receipt, and support snapshot |
| POST | `/commercial/trial` | Start the one-time 20-day Legend trial for the current organisation |
| POST | `/commercial/onboarding` | Super Admin atomic tenant/admin/defaults/trial onboarding |
| POST | `/commercial/invoices` | Super Admin issue one approved monthly manual invoice |
| POST | `/commercial/invoices/{id}/void` | Void an unpaid issued invoice with a reason |
| POST | `/commercial/payments` | Record an exact verified offline payment and create its receipt/subscription transition |
| POST | `/commercial/subscription/suspend` | Super Admin suspend current access with a required reason |
| POST | `/commercial/subscription/restore` | Super Admin restore access only inside the original paid/trial window |
| POST | `/commercial/support-cases` | Create a tenant-local support case |
| PATCH | `/commercial/support-cases/{id}` | Assign, progress, resolve, or close a support case |

Non-entitled paid actions use status `402`. Expired organisations retain core-record,
result-history, billing, and support access; plan-specific analytics remain gated.
Response saving and submission for an already-started attempt are deliberately not
interrupted.

All API responses include `X-Trace-Id`. Rate-limited responses use status `429`,
include `Retry-After` and `X-RateLimit-*` headers, and retain the standard error
envelope. Failed credentials persist in an independent transaction; the default
fifth failure locks the account for 30 minutes. Invitation tokens are stored only
as SHA-256 hashes, expire after 72 hours by default, and are consumed once.

## Error envelope

```json
{
  "timestamp": "2026-07-30T10:30:00Z",
  "status": 422,
  "code": "REVIEW_CHECKLIST_INCOMPLETE",
  "message": "Complete every academic review check before approval.",
  "path": "/api/v1/questions/00000000-0000-0000-0000-000000000000/review",
  "fieldErrors": null,
  "traceId": "..."
}
```
# Post-M6 API additions

## Assessments

`POST /api/v1/assessments` now accepts `subjectIds: UUID[]` instead of a single authoring subject. Responses retain `subjectId` as the backward-compatible primary subject and add `subjectIds`.

## Student assessments and results

- `GET /api/v1/student/assessments` returns every eligible scheduled assessment with `AVAILABLE_NOW`, `UPCOMING`, `COMPLETED`, or `MISSED_CLOSED` and `remainingDays`.
- `GET /api/v1/student/results/{attemptId}` adds publication-gated subject/topic names, chapter, Bloom level, answer status, and optional rank/topper score.
- `GET /api/v1/reports/students/me/analytics` adds chapter and Bloom-level breakdowns.

## Academic sections

- `GET /api/v1/academic-masters/sections`
- `POST /api/v1/academic-masters/sections`
- `PUT /api/v1/academic-masters/sections/{id}`
- `PATCH /api/v1/academic-masters/sections/{id}/activate`
- `PATCH /api/v1/academic-masters/sections/{id}/deactivate`
- `PATCH /api/v1/academic-masters/sections/{id}/archive`

Section mutations require `SUPER_ADMIN` or `ORG_ADMIN`, an active assessment entitlement, tenant-owned matching masters, and a unique name within programme + batch.
