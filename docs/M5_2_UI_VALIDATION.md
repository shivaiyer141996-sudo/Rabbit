# M5.2 — Local journey and UI validation

## Acceptance boundary

M5.2 validates the frozen Rabbit Release 1.0 candidate on the designated local
Docker host. It does not add product scope and it never uses a public tunnel,
cloud browser, hosted database, or paid service.

The automated evidence runner is deliberately read-only. It proves authorised
role routes, live API rendering, serious/critical automated accessibility
findings, page errors, responsive overflow, reduced-motion handling, and local
screenshots. It cannot approve visual quality, consume a live attempt, make an
academic decision, or sign institution acceptance.

## One-time setup on the designated host

1. Complete M5.1 host preflight and keep Rabbit running with `make pilot-up`.
2. Create and activate five non-demo UAT users in one pilot organisation:
   Organisation Admin, Academic Head, Faculty, Reviewer, and Student.
3. Copy `.env.pilot-ui.example` to `.env.pilot-ui`, replace every placeholder,
   and restrict it to the designated tester (`chmod 600 .env.pilot-ui` on macOS
   or Linux; use the equivalent user-only permission on Windows).
4. Install the local Chromium evidence runtime:

```bash
make pilot-ui-install
```

No credentials are written to the evidence bundle. Playwright tracing and video
are disabled because they can retain sensitive form and response data.

## Generate automated evidence

```bash
make pilot-ui-evidence
```

For visible local observation or a single project:

```bash
./infra/pilot/ui-evidence.sh --headed
./infra/pilot/ui-evidence.sh --project android-360
```

The command refuses public targets and writes a checksum-protected bundle under
`artifacts/pilot-ui/`. A pass is supporting evidence only. Upload or reference the
approved bundle when recording the relevant rows in `/pilot-readiness`.

## Mandatory human journey record

Record tester, date/time, account, input data, expected result, actual result,
screenshot/file reference, result, and defect ID for every row.

| Role / area | Browser interaction | Expected result |
| --- | --- | --- |
| Admin | Login, organisation/users/masters, dashboard, reports, operations, pilot register, logout | Every item is authorised, useful, and backed by live tenant data |
| Academic Head | Dashboard, question/assessment queues, institution reports | Academic scope is institution-wide without Admin-only controls |
| Teacher | Create and submit one Single Correct and one Multiple Correct question | Validation, version, author ownership, and review state are correct |
| Reviewer | Review those questions; return one with reason and approve one after all checks | Creator/reviewer separation, reason gate, history, and notifications work |
| Teacher + Reviewer | Create, review, approve, schedule, and publish a rehearsal assessment | Only approved content enters the scheduled assessment and every transition is audited |
| Student | Read instructions, acknowledge rules, start, answer, flag, move, refresh, resume, and submit | Timer/order stay stable; saved responses and flags survive refresh; one submission is recorded |
| Staff monitor | Observe the active rehearsal attempt | Live counts and status reconcile with the student session |
| Publication | Inspect evaluated result before and after publication | Student sees no unpublished score and sees the exact published result afterward |
| Re-evaluation | Open manual review, make one bounded mark change with a reason, inspect audit, republish | Version increments, publication resets to pending, audit retains before/after/reason, and republished score reconciles |
| Student analytics | Open subject, topic, difficulty, time, and question review | Only published attempts appear and totals reconcile to question marks |
| Teacher analytics | Open batch, student comparison, and weak-topic analysis | Faculty sees only owned assessments; Admin/Academic Head see approved wider scope |
| Exports | Download PDF and Excel from the teacher/report views | Files open locally and match filters, rows, totals, and publication state shown in the UI |
| Keyboard | Complete login and the critical role navigation without a mouse | Focus is visible, ordered, not trapped, and every action is reachable |
| Screen reader | Spot-check login, dashboard, assessment instructions/player, reports, and manual review | Headings, labels, errors, status changes, tables, and controls are announced meaningfully |
| Chrome zoom | Repeat critical staff and student actions at 100% and 200% | Content reflows without lost information, overlap, or blocked action |
| Edge | Repeat login, dashboard, assessment, reports, export, and logout in current Edge | Behaviour and layout match current Chrome |
| Physical Android | Complete the rehearsal student journey in current Chrome at approximately 360 px CSS width | No horizontal page overflow, blocked control, obscured timer, or lost response |
| Reduced motion | Repeat login/dashboard/player with OS/browser reduced motion enabled | Non-essential motion is suppressed and no state relies on animation alone |
| Empty/error/loading | Review representative empty, slow, expired-session, validation, and server-error states | Each state explains what happened and offers a safe next action |

## Defect and exit rule

- Severity 1 or 2: stop M5.2; fix on the feature branch and rerun the affected
  journey plus regression evidence.
- Severity 3 or 4: record owner, workaround, and target date; institution decides
  whether the pilot may continue.
- Mark `ACCESSIBILITY` and `MOBILE_WEB` as passed in `/pilot-readiness` only after
  both automated evidence and the relevant signed human rows pass.
- M5.2 is complete only when the desktop, physical Android, accessibility, and
  state-changing journey records identify the exact release commit and have no
  open Severity 1 or 2 defect.
