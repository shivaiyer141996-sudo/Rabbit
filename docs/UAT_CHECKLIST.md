# Release 1.0 controlled-pilot UAT

Record the tester, date, environment, tenant, evidence link, result, and defect ID for every row.

The Organisation Admin records these rows in **Pilot readiness** (`/pilot-readiness`).
A passing row requires a tester and evidence link. A failed or blocked row requires
a defect ID or explanatory notes. The application permits an immutable Conditional
Retest or No-Go at any evidence state, but prevents Go until all mandatory rows pass
and locks the evidence register only after Go authorisation.

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
| Recovery | Run the M5.3 quiesced backup and isolated functional restore; inspect source/restored reconciliation and product probe | Data/assets/login/assessment/result/export/audit validate within four-hour RTO from a backup no older than 24 hours |
| Performance | Run the M5.3 k6 profile at the approved student count plus 50% headroom | Failure rate <1%, p95 <500 ms, p99 <1 s, checks >99% |
| Security | Review M5.3 architecture, runtime, HTTP, secret-containment, and exact active-image scan evidence | No critical unresolved finding or failed mandatory control |
| Operating ownership | Confirm release, support, incident, and rollback owners | Every operating role has a named, reachable owner |
| Staff rehearsal | Complete the frozen staff rehearsal from access through reports | Exact local release/cohort/content is used and the institution UAT lead accepts the rehearsal evidence |
| Live assessment | Complete the frozen institutional assessment from access through reports | Approved attendees complete one governed attempt and no live journey blocker remains |
| Pilot reconciliation | Compare roster/attendance with attempts, evaluation, publication, reports, exports, and audit | Counts and identities reconcile to the M5.4 bundle with no unfinished or unexpected attempt |
| Incident closure | Review the complete rehearsal/live incident register | No S1/S2 remains open; every S3/S4 has owner, workaround, and due date |

## Exit criteria

- No open Severity 1 or Severity 2 defect.
- All identity, isolation, delivery, evaluation, publication, export, backup, and accessibility rows pass.
- Any warning in the operations console has a named owner and due date.
- The institution authorises pilot expansion in writing.
- The M5.5 signed local acceptance PDF, exact release commit/version, institution,
  UAT lead, all operating owners, handover recipient, known issues, evidence
  reference/SHA-256, decision reason, and architecture/data/scope attestations
  match the immutable Rabbit decision.
- Both M5.4 event bundles pass, use unchanged freeze manifests, and are referenced
  in the four mandatory Pilot execution rows.
- M5.5 prepare and finalize bundles pass on the designated computer. Release
  expansion requires Go; Conditional Retest and No-Go remain blocked outcomes.
- M5.6 preparation binds that Go to the exact active API/web image revisions and
  exports all seven runtime images to checksummed separate local media.
- The exact branch and verification-only tag runs pass; `main`, `origin/main`,
  and the annotated `v1.0.0` tag resolve to the same Go-approved commit; M5.6
  finalization passes without a registry, cloud runtime, or public endpoint.

## Milestone 6 commercial activation UAT

Run this section only after every M5.6 exit criterion above passes.

| Area | Acceptance check | Expected result |
| --- | --- | --- |
| Activation guard | Try enabling M6 without final evidence, with a wrong commit, and with the exact final inputs | Both invalid starts fail closed; exact local evidence starts successfully |
| Catalogue | Compare all nine UI/API/Java/PostgreSQL price points | ₹599–₹2,499 matrix agrees exactly; client cannot override it |
| Trial | Start once, test day 19/day 20, and attempt a second trial | Legend works for exactly 20 days; second trial is rejected |
| Capacity | Invite Students at 50, 150, and 500 limits | Boundary invite succeeds; next invite returns the plan-capacity error |
| Entitlements | Exercise Basic, Pro, Legend, expired, and suspended states | API and navigation match the documented plan matrix |
| Live attempt protection | Expire access while a Student has an in-progress attempt | New start is blocked; existing response saves and submit still work |
| Invoice | Try a wrong price, invalid capacity, non-monthly period, duplicate number, and valid invoice | Invalid records are rejected; valid subtotal is server-derived |
| Payment/receipt | Try partial, excess, duplicate-reference, and exact payments | Only exact verified payment commits invoice, receipt, plan event, and audit together |
| Upgrade/downgrade | Apply immediate upgrade and paid next-period downgrade | Upgrade is immediate; downgrade preserves current access until period end |
| Suspension | Suspend with a reason; restore before and after the original end | Before-end restore succeeds; no action extends or revives the access window |
| Onboarding | Create one tenant and activate the invited Organisation Admin | Defaults, memberships, trial, audit, and tenant isolation are correct |
| Support | Create S1–S4 cases; assign, resolve, and close | Targets derive from severity; resolution text is mandatory |
| Recovery | Back up and restore all V9 tables in isolation | Subscription, billing, receipt, support, events, and audit reconcile |
| Responsive UI | Complete `/commercial` at desktop, 200% zoom, and physical Android width | No unavailable action is hidden incorrectly and no horizontal blocker appears |
