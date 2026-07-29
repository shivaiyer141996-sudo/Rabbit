# Architecture

## Shape

Rabbit Milestone 1 uses a modular monolith. This preserves strong transactional boundaries while the product and domain model are still evolving.

```mermaid
flowchart TB
  Browser["Browser"] --> Nginx["Nginx"]
  Nginx --> Web["Next.js portal"]
  Nginx --> API["Spring Boot API"]
  API --> DB[("PostgreSQL")]
  API --> Cache[("Redis")]
  API --> Queue["RabbitMQ"]
  API --> Assets[("MinIO")]
```

## Core boundaries

| Boundary | Responsibility |
| --- | --- |
| `auth` | Credentials, organisation selection, JWT and refresh rotation |
| `organisation` | Tenant and academic structure |
| `user` | Global identity, membership, role, status |
| `question` | MCQ authoring, options, metadata, validation, governance |
| `assessment` | Drafts, approved question composition, publishing and schedule |
| `attempt` | Student session, auto-save, submission, scoring and basic result |
| `common` | Errors, audit fields, tenant context and API conventions |

## Multi-tenancy

- Identity is global; an organisation membership assigns role and tenant access.
- Tenant-owned tables carry `organisation_id`.
- The selected organisation is signed into the access token as `org_id`.
- Services obtain the tenant from the authenticated context and use tenant-scoped repository methods.
- Client-supplied organisation identifiers never override the signed tenant context.
- Database row-level security is a planned defence-in-depth control before production GA.

## Security

- Passwords are BCrypt-hashed with strength 12.
- Access tokens are short lived; refresh tokens rotate and are persisted as SHA-256 hashes.
- The Next.js gateway keeps tokens in secure, HttpOnly, same-site cookies.
- Method-level role checks guard administrative and academic workflows.
- API errors use one stable envelope and do not disclose stack traces.
- Secrets are injected through the environment and are never stored in source.

## Reliability

- Responses are saved on question navigation and every 30 seconds.
- Attempts are resumable from persisted responses.
- Submission is idempotent: an already-submitted attempt returns its stored result.
- Database constraints protect core invariants in addition to service validation.
- Redis, RabbitMQ, and MinIO are provisioned now; asynchronous notifications, caching, and file workflows are expanded in Milestone 2.
