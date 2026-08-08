#!/usr/bin/env python3
"""Static acceptance contract for the post-M6 functional enhancement package."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
FAILURES: list[str] = []


def check(condition: bool, label: str) -> None:
    print(("PASS  " if condition else "FAIL  ") + label)
    if not condition:
        FAILURES.append(label)


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


migration = text("backend/src/main/resources/db/migration/V10__assessment_student_sections_enhancements.sql")
assessment = text("backend/src/main/java/com/rabbit/aip/assessment/AssessmentService.java")
attempt = text("backend/src/main/java/com/rabbit/aip/attempt/AttemptService.java")
dashboard = text("backend/src/main/java/com/rabbit/aip/dashboard/DashboardService.java")
review = text("frontend/src/components/approval-workspace.tsx")
student_nav = text("frontend/src/components/app-shell.tsx")
section_controller = text("backend/src/main/java/com/rabbit/aip/academic/SectionController.java")
section_service = text("backend/src/main/java/com/rabbit/aip/academic/SectionService.java")
result = text("backend/src/main/java/com/rabbit/aip/attempt/AttemptService.java")

check("CREATE TABLE assessment_subject_ids" in migration, "Multi-subject persistence exists")
check("DUPLICATE_ASSESSMENT_SUBJECT" in assessment and "DUPLICATE_ASSESSMENT_QUESTION" in assessment, "Subject and question duplicates are rejected")
check("QUESTION_SUBJECT_NOT_SELECTED" in assessment, "Assessment questions are constrained to selected subjects")
check("StudentAssessmentClassifier.classify" in attempt and "attemptService.catalog()" in dashboard, "Dashboard and assessment page share one status model")
check(all(value in text("backend/src/main/java/com/rabbit/aip/attempt/AttemptDtos.java") for value in ("AVAILABLE_NOW", "UPCOMING", "COMPLETED", "MISSED_CLOSED")), "All four student assessment statuses are represented")
check("selectAllRef.current.indeterminate" in review and "Clear all" in review, "Review Select All, Clear All, and indeterminate states exist")
check("reason.trim().length < 10" in review and review.count("reason.trim().length < 10") >= 2, "Return and Reject require comments")
check("ranking_enabled BOOLEAN NOT NULL DEFAULT FALSE" in migration, "Ranking defaults off at the institute boundary")
check("settings.rankingEnabled() ? rank" in result and "settings.rankingEnabled() ? topperScore" in result, "Rank and topper score are removed from the API when ranking is disabled")
check("questionResults" in result and "reveal" in result and "Map.of()" in result, "Question answers remain publication-gated")
check(all(route in student_nav for route in ('label: "Dashboard"', 'label: "Assessments"', 'label: "My Results"', 'label: "Notifications"', 'label: "Profile"')), "Student navigation exposes the requested five destinations")
check("studentPortalRouteAllowed" in student_nav, "Direct student access to staff UI routes is blocked")
check("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')" in section_controller, "Section APIs are staff-authorized")
check(all(action in section_controller for action in ('/{id}/activate', '/{id}/deactivate', '/{id}/archive')), "Section lifecycle endpoints are complete")
check("SECTION_NAME_EXISTS" in section_service and "section_name_per_programme_batch" in migration, "Section duplicates are blocked by API and database")
check("section_academic_master_tenant" in migration and "assessment_subject_tenant" in migration, "New relationships have database tenant guards")
check("@RequestMapping(\"/api/v1/student\")" in text("backend/src/main/java/com/rabbit/aip/attempt/StudentAssessmentController.java") and "hasRole('STUDENT')" in text("backend/src/main/java/com/rabbit/aip/attempt/StudentAssessmentController.java"), "Student delivery APIs remain student-only")
check("hasRole('STUDENT')" in text("backend/src/main/java/com/rabbit/aip/report/ReportController.java"), "My Results APIs remain student-only")
check("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY','REVIEWER')" in text("backend/src/main/java/com/rabbit/aip/question/QuestionController.java"), "Students cannot access question-bank APIs")

if FAILURES:
    print(f"\n{len(FAILURES)} enhancement contract check(s) failed.", file=sys.stderr)
    raise SystemExit(1)
print("\nAll enhancement contract checks passed.")
