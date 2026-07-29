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
| GET | `/auth/me` | Return current user, organisation, and role |
| GET/POST | `/organisations` | Organisation administration |
| GET/POST | `/users` | Organisation user administration |
| PATCH | `/users/{id}/status` | Change membership state |

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
| POST | `/student/assessments/{id}/attempts` | Start or resume an attempt |
| PUT | `/student/attempts/{id}/responses` | Upsert one response |
| POST | `/student/attempts/{id}/submit` | Submit and auto-evaluate |
| GET | `/student/results/{attemptId}` | Pending state or published detailed result |

## Evaluation and intelligence

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/evaluation/assessments/{id}/results` | Evaluation and publication queue |
| POST | `/evaluation/assessments/{id}/publish` | Publish all evaluated pending results |
| POST | `/evaluation/attempts/{id}/re-evaluate` | Governed MCQ re-evaluation with reason |
| GET | `/dashboard` | Role-specific metrics, trend, and attention data |
| GET | `/reports/overview` | Organisation intelligence overview |
| GET | `/reports/assessments/{id}` | Assessment/student/question report |
| GET | `/reports/students/me` | Current student progress report |
| GET | `/reports/students/{id}` | Role-scoped student report |
| GET | `/reports/questions` | Question quality analytics |
| GET | `/reports/faculty` | Faculty contribution report |
| GET | `/reports/assessments/{id}/export` | Assessment CSV snapshot |

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
