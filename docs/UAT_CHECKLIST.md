# Release 1.0 controlled-pilot UAT

Record the tester, date, environment, tenant, evidence link, result, and defect ID for every row.

| Area | Acceptance check | Expected result |
| --- | --- | --- |
| Identity | Login, lockout, refresh, organisation selection, logout | Access follows active membership and role |
| Tenant isolation | Attempt cross-tenant URL and relationship access | Request and database contract reject it |
| Question governance | Author, validate, review, return, approve, version | Only approved questions enter assessments |
| Assessment governance | Draft, review, approve, publish, schedule | Creator/reviewer separation remains enforced |
| Delivery | Start, save, refresh, resume, timeout, submit | No accepted answer is lost or duplicated |
| Evaluation | Score both MCQ types, re-evaluate, publish | Students see only explicitly published results |
| Reports | Open dashboards and assessment drill-down | Metrics use only published tenant data |
| Exports | Download CSV, PDF, and XLSX | Files open and match the on-screen report |
| Operations | Review dependencies, traffic, backlog, capacity | Readiness state and warnings match evidence |
| Feature flags | Change rollout and disable an export | Audit event exists and behaviour changes safely |
| Accessibility | Keyboard-only, focus order, labels, zoom 200%, reduced motion | Core journey remains understandable and operable |
| Mobile web | Complete student journey at 360 px width | No blocked action or horizontal page overflow |
| Recovery | Restore latest backup in a non-production environment | Data/assets validate within four-hour RTO |
| Performance | Run the k6 read profile at pilot load | Failure rate <1%, p95 <500 ms, p99 <1 s |
| Security | Verify headers, rate limits, secret guard, dependency review | No critical unresolved finding |

## Exit criteria

- No open Severity 1 or Severity 2 defect.
- All identity, isolation, delivery, evaluation, publication, export, backup, and accessibility rows pass.
- Any warning in the operations console has a named owner and due date.
- The institution authorises pilot expansion in writing.
