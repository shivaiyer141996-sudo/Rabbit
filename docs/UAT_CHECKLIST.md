# Release 1.0 controlled-pilot UAT

Record the tester, date, environment, tenant, evidence link, result, and defect ID for every row.

The Organisation Admin records these rows in **Pilot readiness** (`/pilot-readiness`).
A passing row requires a tester and evidence link. A failed or blocked row requires
a defect ID or explanatory notes. The application prevents final sign-off until all
mandatory rows pass and locks the evidence register after authorisation.

CI also runs `infra/pilot/smoke.sh` against the complete Docker stack. It verifies
readiness, role-specific dashboards, the academic catalog, pilot register, student
instructions, stable attempt resume, response persistence, live monitoring,
server-enforced timeout submission, result confidentiality, reason-gated
re-evaluation, publication, attempt history, and filtered student reports. This
automated smoke is supporting evidence; it does not replace institution UAT.

| Area | Acceptance check | Expected result |
| --- | --- | --- |
| Identity | Login, lockout, refresh, organisation selection, logout | Access follows active membership and role |
| Tenant isolation | Attempt cross-tenant URL and relationship access | Request and database contract reject it |
| Question governance | Author, validate, review, return, approve, version | Only approved questions enter assessments |
| Assessment governance | Draft, review, approve, publish, schedule | Creator/reviewer separation remains enforced |
| Delivery | Read instructions; start, save, refresh, resume, close at timeout, submit | Order remains stable; no accepted answer is lost or duplicated; the server submits an expired closed-browser attempt |
| Evaluation | Score both MCQ types; open manual review; adjust bounded question marks with a reason; inspect audit; republish | Version increments, audit captures before/after/reason, and students see only explicitly published results |
| Student reports | Open subject, topic, difficulty, time, and question review as Student and authorised staff | Only published results appear and every breakdown reconciles to question-level marks |
| Teacher reports and exports | Compare batches/students/weak topics; switch authorised teacher scope; download PDF and XLSX | Faculty scope contains only owned assessments; Admin/Academic Head scope and exports match the UI |
| Role workspace | Open every dashboard and navigation item as Admin, Academic Head, Faculty, Reviewer, and Student | Metrics, actions, and links are authorised and useful for that role |
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
