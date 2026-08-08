# Release Notes — Post-M6 Functional Enhancements

## Added

- Multi-subject assessment authoring with preserved selections and per-subject totals.
- Unified Available/Upcoming/Completed/Missed-Closed student assessment lifecycle.
- My Results with published result list, expanded analytics, and question navigation.
- Optional institute-controlled rank and topper score.
- Complete Section Management lifecycle with usage counts and archival retention.
- Student Profile and a five-item student-only navigation model.

## Improved

- Review criteria now support Select All, Clear All, and partial selection.
- Return/Reject comment validation is visible before submission.
- Question review supports correct, incorrect, unanswered, and future partial-correct presentation.
- Responsive controls, pagination, empty states, confirmations, loading states, and success/error feedback were added to changed surfaces.

## Security and data integrity

- Student direct access to staff UI routes is blocked, and staff APIs retain server-side role enforcement.
- Assessment-subject and section-master tenant relationships are guarded in PostgreSQL.
- Answer keys remain server-hidden until result publication.
- Section records are archived, never hard deleted.
- V10 records are included in backup/restore reconciliation manifests.

## Upgrade note

Run Flyway V10 before starting the updated application. Keep commercial controls disabled unless the existing M5.6/M6 activation evidence has passed.
