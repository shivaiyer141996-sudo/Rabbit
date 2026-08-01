# Milestone 4.2 — Functional and UI completion

Status: **Implementation complete — branch verification pending**

Milestone 4.2 closes the functional and interface gaps found after the Milestone 4.1
release review. It does not add AI, subjective evaluation, proctoring, native mobile
applications, or external provider integrations.

## 1. Exam reliability

- Question shuffle changes the delivered question order when enabled.
- Option shuffle changes the delivered option order and display labels when enabled.
- Both orders are deterministic for an attempt and remain identical after refresh/resume.
- A server worker automatically evaluates every expired in-progress attempt even when
  the student has closed the browser.
- Manual submit and server auto-submit remain idempotent and cannot evaluate twice.
- Automated tests cover stable presentation ordering and expired-attempt processing.

## 2. Role-specific dashboards and navigation

- Organisation Admin, Academic Head, Faculty, Reviewer, and Student receive distinct
  metrics, attention items, primary actions, and quick actions.
- Every dashboard and navigation link is authorised and useful for the signed-in role.
- Student dashboard links only to student assessments, history, results, and notifications.
- Search is either functional or removed; no inactive control is presented as available.

## 3. Student reports and re-evaluation

- Staff can open a consolidated student-results report.
- Filters cover student text, subject, assessment type, department, section, and
  submission date range.
- The report includes filtered totals plus department and section comparisons.
- Staff can open an individual student's published performance history.
- Authorised staff can re-evaluate a completed attempt only after providing a reason of
  at least ten characters; the updated result returns to pending publication.
- The UI shows the evaluation version and current publication state.

## 4. Exam journey completion

- Students see an instructions/readiness screen before starting a new assessment.
- Students can see attempt history, including in-progress, submitted, auto-submitted,
  pending-publication, and published states.
- Faculty and authorised academic staff have a dedicated assessment monitoring screen
  showing live attempt progress and remaining time.
- Empty, loading, failure, narrow-screen, keyboard, and reduced-motion states are usable
  for every new journey.

## 5. Release evidence

- Backend verification, frontend lint/type-check/tests/build, production dependency
  audit, PostgreSQL V1–V7 migration/tenant contract, Docker image builds, Trivy scans,
  full-stack health, and authenticated smoke journeys pass on one exact commit.
- The main staff and student journeys are manually checked at desktop and 360 px Android
  viewport sizes using the Docker deployment.
- No Severity 1 or Severity 2 defect remains open.
- Milestone 4.2 is published only after verification; `main` remains protected while the
  implementation branch is under development.
