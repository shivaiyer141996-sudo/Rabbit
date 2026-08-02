# M5.4 local institutional pilot execution

M5.4 runs one staff rehearsal and one live assessment on the approved Rabbit
computer. It uses only the local Docker Compose stack and the trusted local
network. The evidence tooling is read-only: it cannot create users, edit or
schedule an assessment, start or submit a Student attempt, publish results,
change the Pilot readiness register, or sign for the institution.

Repository tooling being present is not an M5.4 pass. A named institution UAT
lead and technical owner must execute and review both events on the designated
host.

## What is proved

| Stage | Automated local evidence | Human decision |
| --- | --- | --- |
| Freeze | Exact release commit/version, tenant, approved roster fingerprint, active roles, 50-user cap, prior readiness, operations health, schedule, duration, question count, assessment settings, independent approval, and question fingerprints | UAT lead confirms the people, content, timing, support window, and local network are approved |
| Event | Rabbit's normal UI and APIs conduct the assessment | Staff/Students complete the browser journey; support team records incidents and decides whether to stop |
| Reconciliation | Frozen commit/content/roster, attendance, attempts, completion, scoring, publication, CSV/PDF/XLSX, audit events, and incident closure | UAT lead explains absences, verifies output usefulness, and accepts or rejects the event |

The bundle uses hashed participant keys in ordinary JSON evidence. The three
restricted report exports contain names and scores because they are the governed
business outputs being reconciled. Keep the complete bundle only on the approved
local evidence/backup media.

## Prerequisites

Do not freeze a rehearsal until:

- the institution, cohort, assessment, designated host, private LAN, dates, and
  every operating owner are confirmed;
- M5.1 host/preflight and backup evidence has passed;
- M5.2 desktop, 200% zoom, accessibility, and physical Android evidence has passed;
- M5.3 performance, security, recovery, and rollback evidence has passed;
- the real Organisation Admin is active and every seeded demo identity is retired;
- Rabbit is healthy on the exact clean committed release; and
- a checksum-verified, quiesced backup of that commit exists on the approved
  separate local device and is no older than 24 hours; and
- no other assessment is active.

A **live** freeze additionally requires the Staff rehearsal row in Pilot
readiness to have passed with reviewed rehearsal evidence.

## Prepare protected inputs

From the repository root:

```bash
cp .env.pilot-m5-4.example .env.pilot-m5-4
cp infra/pilot/templates/m5-4-roster.csv.example .pilot-m5-4-roster.csv
cp infra/pilot/templates/m5-4-incidents.csv.example .pilot-m5-4-incidents.csv
chmod 600 .env.pilot-m5-4 .pilot-m5-4-roster.csv .pilot-m5-4-incidents.csv
```

Replace every placeholder in `.env.pilot-m5-4`. The Admin must be an active,
non-demo Organisation Admin in the approved tenant. Point the backup setting to
the latest verified backup directory on the separate local device. Use
`REHEARSAL` first.

Replace the sample roster emails with the exact approved Students. Before the
event, leave `attended` and `absence_reason` empty. Keep the header exactly as
committed:

```csv
email,attended,absence_reason
student@institution.example,,
```

Keep the incident register header even when no incident occurs. Never commit,
attach, or paste any of the three protected files into a ticket or chat.

## Freeze before the event opens

After the assessment has completed author/reviewer approval and has been
published/scheduled, run:

```bash
make pilot-m5-4-freeze
```

The freeze refuses an already-open schedule, a public target, dirty worktree,
failed prerequisite, invalid/stale backup, active demo user, unresolved roster member, ineligible
section, non-ready operations state, missing approval, content outside the
approved question/duration range, or more than one allowed attempt.

It writes a checksummed directory similar to:

```text
artifacts/pilot-m5-4/rabbit-m5.4-rehearsal-freeze-<UTC>/
```

Preserve `freeze-manifest.json`, `SHA256SUMS`, and `evidence-reference.txt`. Do
not edit the assessment, its schedule, question content, roster email set, or
release commit after a successful freeze. If any changes, create a new freeze.

## Conduct the rehearsal or live assessment

Use the normal Rabbit UI and the signed UAT journey:

1. Confirm the latest separate-device backup, operations health, named support
   channel, and rollback owner 30 minutes before start.
2. Confirm every expected Student can reach only the private-LAN Rabbit address.
3. Observe starts, saves, resumes, timer, submissions, and auto-submissions in
   the assessment monitor.
4. Stop immediately for S1; treat S2 as No-Go until fixed and retested.
5. Review completed evaluation, perform only governed/manual corrections with a
   reason, and confirm the audit history.
6. Publish results once, then verify Student and Teacher reports plus exports.
7. Record every incident with severity, owner, workaround, defect, due date, and
   closure information.

After the event, set every roster row's `attended` field to `yes` or `no`. A `no`
row requires an absence reason. Do not remove absentees from the frozen roster.

## Reconcile the completed event

Run against the exact passed freeze:

```bash
make pilot-m5-4-reconcile \
  PILOT_FREEZE_MANIFEST=artifacts/pilot-m5-4/rabbit-m5.4-rehearsal-freeze-<UTC>/freeze-manifest.json
```

Reconciliation fails if:

- the commit, tenant, roster, assessment, schedule, or question fingerprints
  differ from the freeze;
- an attendee has no attempt, an absentee has an attempt, an unexpected Student
  has an attempt, or a Student has more than one attempt;
- an attempt remains in progress;
- evaluated, published, report, and attendance counts differ;
- CSV, PDF, or Excel cannot be generated;
- submission/publication audit events do not cover every attempt; or
- an incident row is malformed or any S1/S2 incident remains open.

The result includes another checksummed directory and a
`urn:rabbit-evidence:...` reference. This local reference can be pasted into the
Pilot readiness Evidence field; it avoids inventing a cloud/public file link.
The application does not mark any row passed automatically.

## Rehearsal-to-live sequence

1. Complete and review a successful `REHEARSAL` freeze and reconciliation.
2. In Pilot readiness, mark **Staff rehearsal** passed using its local evidence
   reference and the named institution tester.
3. Create/approve/schedule the separately approved live assessment.
4. Change `.env.pilot-m5-4` to `PILOT_M5_4_EVENT_TYPE=LIVE` and set the live
   assessment ID; keep the approved live roster.
5. Create a new live freeze, conduct the live event, and reconcile it.
6. After human review, mark **Live assessment**, **Pilot reconciliation**, and
   **Incident closure** passed using the live evidence reference.

M5.4 passes only after both events are accepted and no S1/S2 defect remains
open. M5.5 institutional sign-off remains a separate, deliberate action.

## Failure policy

- Freeze failure: correct the configuration/content/owner/readiness issue and
  create a new freeze. Never weaken the check or use an old manifest.
- Rehearsal failure: fix only S1/S2 blockers, retest the affected journey, and
  rerun the rehearsal when required. Put S3/S4 in the owned backlog.
- Live S1/S2: stop publication/expansion, preserve evidence, follow the tested
  local rollback or restore process, and issue a No-Go.
- Local host capacity failure: reduce or stop the cohort. Do not introduce AWS,
  another cloud service, or a public tunnel.
