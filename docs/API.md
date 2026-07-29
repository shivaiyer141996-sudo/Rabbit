# Milestone 1 API

Base path: `/api/v1`

## Authentication

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/login` | Verify credentials and return tokens or organisation choices |
| POST | `/auth/select-organisation` | Exchange a selection token for tenant-scoped tokens |
| POST | `/auth/refresh` | Rotate refresh token and issue a new access token |
| POST | `/auth/logout` | Revoke the current refresh token |
| GET | `/auth/me` | Return current user, organisation, and role |

## Foundation administration

| Method | Path | Roles |
| --- | --- | --- |
| GET/POST | `/organisations` | Super Admin |
| GET | `/organisations/current` | Authenticated |
| GET/POST | `/users` | Organisation Admin |
| PATCH | `/users/{id}/status` | Organisation Admin |

## Questions

| Method | Path | Purpose |
| --- | --- | --- |
| GET/POST | `/questions` | Search/list or author an MCQ |
| GET/PUT | `/questions/{id}` | Read or edit a draft |
| POST | `/questions/{id}/submit` | Move draft to review |
| POST | `/questions/{id}/review` | Approve, return, or reject |

## Assessments

| Method | Path | Purpose |
| --- | --- | --- |
| GET/POST | `/assessments` | List or create a draft |
| GET | `/assessments/{id}` | Assessment detail |
| POST | `/assessments/{id}/publish` | Publish a valid draft |
| POST | `/assessments/{id}/schedule` | Define window and eligible sections |

## Student attempt

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/student/assessments` | Eligible scheduled assessments |
| POST | `/student/assessments/{id}/attempts` | Start or resume an attempt |
| PUT | `/student/attempts/{id}/responses` | Upsert one response |
| POST | `/student/attempts/{id}/submit` | Submit and score |
| GET | `/student/results/{attemptId}` | Basic result detail |

## Error envelope

```json
{
  "timestamp": "2026-07-29T10:30:00Z",
  "status": 422,
  "code": "QUESTION_VALIDATION_FAILED",
  "message": "Multiple Correct MCQ requires at least two correct options.",
  "path": "/api/v1/questions",
  "fieldErrors": {
    "options": "Select at least two correct options."
  },
  "traceId": "..."
}
```
