# Post-Milestone 6 Functional Enhancements

## Scope

This package enhances the current Milestone 6 branch without changing the M6 commercial boundary or the local-only Docker Compose architecture.

### Assessment creation

- Assessments accept one or more unique subject IDs.
- Every selected question must be approved/published, unique, tenant-owned, and belong to a selected subject.
- The authoring UI provides searchable subject selection and subject, topic, difficulty, question-type, and text filters.
- Adding a subject preserves existing question selections. Removing a subject requires confirmation when selected questions would be removed.
- The summary reports selected subjects, question/mark totals per subject, and overall totals.

### Review workspace

- Question criteria support Select All, Clear All, individual edits, and an accessible indeterminate state.
- Approval remains disabled until every mandatory criterion is selected.
- Return and Reject require at least ten characters of reviewer comments in both UI and API validation.

### Student experience

- Student navigation is limited to Dashboard, Assessments, My Results, Notifications, and Profile.
- Direct staff-route rendering is rejected by the student shell; staff APIs remain role-authorized on the server.
- The student dashboard contains student-only assessment, latest/average score, progress, and published trend data.
- A shared server catalogue classifies Available Now, Upcoming, Completed, and Missed/Closed. The dashboard and assessment tabs use the same catalogue.
- My Results shows published assessment scores and links to result detail. Detailed analytics cover subject, topic, chapter, difficulty, time, and Bloom level.
- Answer keys and explanations are absent from API responses until publication.
- Rank and topper score are absent unless organisation ranking is enabled.

### Section Management

- Route: `Organisation → Academic Masters → Sections` (`/organisations/sections`).
- Admins can create, edit, activate, deactivate, and archive sections. No hard-delete endpoint exists.
- Sections belong to an organisation, programme/course, academic year, and batch.
- Names are unique, case-insensitively, within organisation + programme + batch in both API and PostgreSQL.
- Counts expose students, teachers, and assigned assessments. Historical relationships remain intact after archival.
- Active sections flow through the existing live academic catalogue to user creation, assessment assignment, filters, and reports.

## Database changes

Flyway `V10__assessment_student_sections_enhancements.sql` adds:

- `assessment_subject_ids` with tenant trigger and migrated legacy subject values;
- `organisation_settings.ranking_enabled` defaulting to `FALSE`;
- `academic_programmes` and `academic_batches`;
- programme, academic-year, batch, status, and archive fields on `sections`;
- case-insensitive section uniqueness and academic-master tenant triggers.

The legacy `assessments.subject_id` remains as the first/primary subject for backward compatibility. New code uses `subjectIds` when subject membership matters.

## Validation commands

```bash
make enhancement-contract
make m6-contract
make architecture-check
cd frontend && npm run check && npm run build
cd backend && mvn test
```

The final two runtime gates—Flyway against PostgreSQL and authenticated UI evidence—must run on the designated Docker host before merge approval.

## Known limitations

- Programme/course, batch, and academic-year creation remain part of the existing academic-master administration backlog; this package manages Sections against those masters.
- Ranking uses simple descending percentage order; an institution-specific tie policy is not yet configurable.
- “Partially Correct” rendering is future-ready and appears when partial marks are actually awarded.
- Screenshots require a running authenticated Docker environment with representative Admin, Reviewer, and Student data.

## Remaining backlog

- Configurable rank tie-break rules and anonymised ranking policy.
- Dedicated programme, batch, and academic-year lifecycle screens.
- Server-side pagination for institutions that exceed the current catalogue-scale list views.
- Full browser E2E coverage against PostgreSQL for modal confirmation, keyboard traversal, and cross-role access denial.
