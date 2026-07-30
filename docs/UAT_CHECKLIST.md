# Release 1.0 controlled-pilot UAT

Record the tester, date, environment, tenant, evidence link, result, and defect ID for every row.

The Organisation Admin records these rows in **Pilot readiness** (`/pilot-readiness`).
A passing row requires a tester and evidence link. A failed or blocked row requires
a defect ID or explanatory notes. The application prevents final sign-off until all
mandatory rows pass and locks the evidence register after authorisation.

CI also runs `infra/pilot/smoke.sh` against the complete Docker stack. It verifies
readiness, authenticated staff pages and APIs, the academic catalog, pilot register,
student assessment discovery, attempt creation, response persistence, submission,
admin publication, and the student's published result. This automated smoke is
supporting evidence; it does not replace institution UAT.

| Area | Acceptance check | Expected result |
| --- | --- | --- |
| Identity | Login, lockout, refresh, organisation selection, logout | Access follows active membership and role |
| Tenant isolation | Attempt cross-tenant URL and relationship access | Request and database contract reject it |
| Question governance | Author, validate, review, return, approve, version | Only approved questions enter assessments |
| Assessment governance | Draft, review, approve, publish, schedule | Creator/reviewer separation remains enforced |
| Delivery | Start, save, refresh, resume, timeout, submit | No accepted answer is lost or duplicated |
| Evaluation | Score both MCQ types, re-evaluate, publish | Students see only explicitly published results |
| Reports and exports | Open dashboards and drill-downs; download CSV, PDF, and XLSX | Published-tenant metrics and exported files match |
| Operations | Review dependencies, traffic, backlog, capacity | Readiness state and warnings match evidence |
| Feature flags | Change rollout and disable an export | Audit event exists and behaviour changes safely |
| Accessibility | Keyboard-only, focus order, labels, zoom 200%, reduced motion | Core journey remains understandable and operable |
| Mobile web | Complete student journey at 360 px width | No blocked action or horizontal page overflow |
| Recovery | Restore latest backup in a non-production environment | Data/assets validate within four-hour RTO |
| Performance | Run the k6 read profile at pilot load | Failure rate <1%, p95 <500 ms, p99 <1 s |
| Security | Verify headers, rate limits, secret guard, dependency review | No critical unresolved finding |
| Operating ownership | Confirm release, support, incident, and rollback owners | Every operating role has a named, reachable owner |

## Exit criteria

- No open Severity 1 or Severity 2 defect.
- All identity, isolation, delivery, evaluation, publication, export, backup, and accessibility rows pass.
- Any warning in the operations console has a named owner and due date.
- The institution authorises pilot expansion in writing.
- The authorised person, title, support contact, rollback owner, and release version
  are recorded in the locked pilot sign-off.
