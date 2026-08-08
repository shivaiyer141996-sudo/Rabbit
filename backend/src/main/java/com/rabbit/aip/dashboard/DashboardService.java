package com.rabbit.aip.dashboard;

import com.rabbit.aip.assessment.Assessment;
import com.rabbit.aip.assessment.AssessmentRepository;
import com.rabbit.aip.assessment.AssessmentStatus;
import com.rabbit.aip.attempt.AssessmentAttempt;
import com.rabbit.aip.attempt.AssessmentAttemptRepository;
import com.rabbit.aip.attempt.AttemptStatus;
import com.rabbit.aip.attempt.AttemptService;
import com.rabbit.aip.attempt.AttemptDtos.StudentAssessmentStatus;
import com.rabbit.aip.attempt.ResultPublicationStatus;
import com.rabbit.aip.dashboard.DashboardDtos.DashboardAttention;
import com.rabbit.aip.dashboard.DashboardDtos.DashboardMetric;
import com.rabbit.aip.dashboard.DashboardDtos.DashboardResponse;
import com.rabbit.aip.dashboard.DashboardDtos.DashboardTrend;
import com.rabbit.aip.notification.NotificationRepository;
import com.rabbit.aip.question.Question;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.question.QuestionStatus;
import com.rabbit.aip.report.ReportDtos.IntelligenceOverview;
import com.rabbit.aip.report.ReportDtos.StudentPerformanceReport;
import com.rabbit.aip.report.ReportService;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.user.AccountStatus;
import com.rabbit.aip.user.OrganisationMembership;
import com.rabbit.aip.user.OrganisationMembershipRepository;
import com.rabbit.aip.user.UserAccount;
import com.rabbit.aip.user.UserAccountRepository;
import com.rabbit.aip.user.UserRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final ReportService reports;
    private final QuestionRepository questions;
    private final AssessmentRepository assessments;
    private final AssessmentAttemptRepository attempts;
    private final OrganisationMembershipRepository memberships;
    private final NotificationRepository notifications;
    private final UserAccountRepository users;
    private final CurrentSession session;
    private final AttemptService attemptService;

    public DashboardService(
            ReportService reports,
            QuestionRepository questions,
            AssessmentRepository assessments,
            AssessmentAttemptRepository attempts,
            OrganisationMembershipRepository memberships,
            NotificationRepository notifications,
            UserAccountRepository users,
            CurrentSession session,
            AttemptService attemptService
    ) {
        this.reports = reports;
        this.questions = questions;
        this.assessments = assessments;
        this.attempts = attempts;
        this.memberships = memberships;
        this.notifications = notifications;
        this.users = users;
        this.session = session;
        this.attemptService = attemptService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        return switch (session.role()) {
            case STUDENT -> studentDashboard();
            case REVIEWER -> reviewerDashboard();
            case FACULTY -> facultyDashboard();
            case ACADEMIC_HEAD -> academicHeadDashboard();
            case SUPER_ADMIN, ORG_ADMIN -> administratorDashboard();
        };
    }

    private DashboardResponse administratorDashboard() {
        IntelligenceOverview overview = reports.overview();
        DashboardCounts counts = counts();
        return response(
                "Institution performance, governance, publication, and learner risk in one view.",
                List.of(
                        metric("Active students", counts.activeStudents(), "Current organisation", "PRIMARY", "/users"),
                        metric("Average score", overview.averageScore() + "%", "Published results", "SUCCESS", "/reports"),
                        metric("Pass rate", overview.passRate() + "%", "Organisation threshold", "INFO", "/reports"),
                        metric("At-risk students", overview.atRiskStudents(), "Below threshold twice", overview.atRiskStudents() > 0 ? "DANGER" : "SUCCESS", "/reports")
                ),
                overview.performanceTrend().stream()
                        .map(item -> new DashboardTrend(item.label(), item.value()))
                        .toList(),
                governanceAttention(counts)
        );
    }

    private DashboardResponse academicHeadDashboard() {
        IntelligenceOverview overview = reports.overview();
        DashboardCounts counts = counts();
        return response(
                "Academic quality, approval queues, learner outcomes, and interventions for your institution.",
                List.of(
                        metric("Questions to review", counts.pendingQuestions(), "Academic governance", counts.pendingQuestions() > 0 ? "WARNING" : "SUCCESS", "/approvals"),
                        metric("Assessments to approve", counts.pendingAssessments(), "Creator-reviewer control", counts.pendingAssessments() > 0 ? "WARNING" : "SUCCESS", "/approvals"),
                        metric("Results to publish", counts.pendingResults(), "Evaluated, not visible", counts.pendingResults() > 0 ? "INFO" : "SUCCESS", "/reports"),
                        metric("At-risk students", overview.atRiskStudents(), "Intervention candidates", overview.atRiskStudents() > 0 ? "DANGER" : "SUCCESS", "/reports")
                ),
                overview.performanceTrend().stream()
                        .map(item -> new DashboardTrend(item.label(), item.value()))
                        .toList(),
                governanceAttention(counts)
        );
    }

    private DashboardResponse facultyDashboard() {
        UUID userId = session.userId();
        List<Question> authoredQuestions = organisationQuestions().stream()
                .filter(item -> item.getAuthorUserId().equals(userId))
                .toList();
        List<Assessment> authoredAssessments = organisationAssessments().stream()
                .filter(item -> item.getCreatedBy().equals(userId))
                .toList();
        Set<UUID> assessmentIds = authoredAssessments.stream()
                .map(Assessment::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<AssessmentAttempt> facultyAttempts = organisationAttempts().stream()
                .filter(item -> assessmentIds.contains(item.getAssessmentId()))
                .toList();
        List<AssessmentAttempt> published = facultyAttempts.stream()
                .filter(item -> item.getResultStatus() == ResultPublicationStatus.PUBLISHED)
                .filter(item -> item.getPercentage() != null)
                .toList();
        long pendingPublication = facultyAttempts.stream()
                .filter(item -> item.getStatus() != AttemptStatus.IN_PROGRESS)
                .filter(item -> item.getResultStatus() == ResultPublicationStatus.PENDING_PUBLICATION)
                .count();
        long activeDeliveries = authoredAssessments.stream()
                .filter(item -> item.isOpenAt(Instant.now()))
                .count();
        List<DashboardTrend> trend = authoredAssessments.stream()
                .map(assessment -> new DashboardTrend(
                        assessment.getTitle(),
                        average(published.stream()
                                .filter(item -> item.getAssessmentId().equals(assessment.getId()))
                                .map(AssessmentAttempt::getPercentage)
                                .toList())
                ))
                .filter(item -> item.value().signum() > 0)
                .limit(6)
                .toList();
        List<DashboardAttention> attention = List.of(
                attention("Draft assessments", "Complete and submit your draft assessments for review.", authoredAssessments.stream().filter(item -> item.getStatus() == AssessmentStatus.DRAFT).count(), "WARNING", "/assessments"),
                attention("Live assessments", "Monitor students currently inside an open delivery window.", activeDeliveries, "INFO", "/assessments"),
                attention("Results awaiting publication", "Review evaluated submissions before students see them.", pendingPublication, "INFO", "/reports/teacher")
        );
        return response(
                "Your question authoring, assessment delivery, and learner outcomes in one place.",
                List.of(
                        metric("Questions authored", authoredQuestions.size(), "Your question bank", "PRIMARY", "/question-bank"),
                        metric("Assessments created", authoredAssessments.size(), "Your lifecycle queue", "INFO", "/assessments"),
                        metric("Student submissions", published.size(), "Published evaluations", "SUCCESS", "/reports/teacher"),
                        metric("Average score", average(published.stream().map(AssessmentAttempt::getPercentage).toList()) + "%", "Your assessments", "SUCCESS", "/reports/teacher")
                ),
                trend,
                attention
        );
    }

    private DashboardResponse reviewerDashboard() {
        Instant overdueBefore = Instant.now().minus(Duration.ofHours(48));
        long pendingQuestions = questions.countByOrganisationIdAndStatus(
                session.organisationId(), QuestionStatus.UNDER_REVIEW
        );
        long pendingAssessments = assessments.countByOrganisationIdAndStatus(
                session.organisationId(), AssessmentStatus.READY_FOR_REVIEW
        );
        long overdueQuestions = questions.countByOrganisationIdAndStatusAndUpdatedAtBefore(
                session.organisationId(), QuestionStatus.UNDER_REVIEW, overdueBefore
        );
        long overdueAssessments = assessments.countByOrganisationIdAndStatusAndUpdatedAtBefore(
                session.organisationId(), AssessmentStatus.READY_FOR_REVIEW, overdueBefore
        );
        return response(
                "Independent academic review queues, ageing, and quality controls for your role.",
                List.of(
                        metric("Question reviews", pendingQuestions, "Waiting for decision", pendingQuestions > 0 ? "WARNING" : "SUCCESS", "/approvals"),
                        metric("Assessment reviews", pendingAssessments, "Waiting for decision", pendingAssessments > 0 ? "WARNING" : "SUCCESS", "/approvals"),
                        metric("Overdue questions", overdueQuestions, "Older than 48 hours", overdueQuestions > 0 ? "DANGER" : "SUCCESS", "/approvals"),
                        metric("Overdue assessments", overdueAssessments, "Older than 48 hours", overdueAssessments > 0 ? "DANGER" : "SUCCESS", "/approvals")
                ),
                List.of(
                        new DashboardTrend("Questions", BigDecimal.valueOf(pendingQuestions)),
                        new DashboardTrend("Assessments", BigDecimal.valueOf(pendingAssessments))
                ),
                List.of(
                        attention("Questions waiting for review", "Apply the academic checklist and record a governed decision.", pendingQuestions, pendingQuestions > 0 ? "WARNING" : "NEUTRAL", "/approvals"),
                        attention("Assessments waiting for review", "Creator and reviewer separation is enforced.", pendingAssessments, pendingAssessments > 0 ? "WARNING" : "NEUTRAL", "/approvals")
                )
        );
    }

    private DashboardResponse studentDashboard() {
        StudentPerformanceReport performance = reports.myPerformance();
        var assessmentCatalog = attemptService.catalog();
        long available = assessmentCatalog.stream()
                .filter(item -> item.status() == StudentAssessmentStatus.AVAILABLE_NOW).count();
        long upcoming = assessmentCatalog.stream()
                .filter(item -> item.status() == StudentAssessmentStatus.UPCOMING).count();
        String latestScore = performance.results().isEmpty()
                ? "—"
                : performance.results().get(performance.results().size() - 1).percentage() + "%";
        return response(
                "Your assessments and published progress.",
                List.of(
                        metric("Upcoming assessments", upcoming + available, available + " available now", "PRIMARY", "/student/assessments"),
                        metric("Average score", performance.averagePercentage() + "%", "Published results", "SUCCESS", "/student/reports"),
                        metric("Latest score", latestScore, "Most recently published", "INFO", "/student/reports"),
                        metric("Progress", performance.trajectory(), "Published performance trend", "SUCCESS", "/student/reports")
                ),
                performance.results().stream()
                        .map(item -> new DashboardTrend(
                                item.assessmentTitle(), item.percentage()
                        ))
                        .toList(),
                List.of()
        );
    }

    private DashboardResponse response(
            String description,
            List<DashboardMetric> metrics,
            List<DashboardTrend> trend,
            List<DashboardAttention> attention
    ) {
        return new DashboardResponse(
                session.role().name(), workspaceTitle(), greeting(), description,
                metrics, trend,
                attention, unread()
        );
    }

    private String workspaceTitle() {
        return switch (session.role()) {
            case SUPER_ADMIN, ORG_ADMIN -> "Admin Dashboard";
            case ACADEMIC_HEAD -> "Academic Head Dashboard";
            case FACULTY -> "Teacher Dashboard";
            case REVIEWER -> "Reviewer Dashboard";
            case STUDENT -> "Student Dashboard";
        };
    }

    private DashboardCounts counts() {
        List<OrganisationMembership> organisationMemberships = memberships
                .findAllByOrganisationIdOrderByCreatedAtDesc(session.organisationId());
        long pendingResults = organisationAttempts().stream()
                .filter(item -> item.getSubmittedAt() != null)
                .filter(item -> item.getResultStatus() == ResultPublicationStatus.PENDING_PUBLICATION)
                .count();
        return new DashboardCounts(
                organisationMemberships.stream()
                        .filter(item -> item.getRole() == UserRole.STUDENT)
                        .filter(item -> item.getStatus() == AccountStatus.ACTIVE)
                        .count(),
                questions.countByOrganisationIdAndStatus(
                        session.organisationId(), QuestionStatus.UNDER_REVIEW
                ),
                assessments.countByOrganisationIdAndStatus(
                        session.organisationId(), AssessmentStatus.READY_FOR_REVIEW
                ),
                pendingResults
        );
    }

    private List<DashboardAttention> governanceAttention(DashboardCounts counts) {
        return List.of(
                attention("Questions waiting for review", "Complete the academic checklist before making a decision.", counts.pendingQuestions(), counts.pendingQuestions() > 0 ? "WARNING" : "NEUTRAL", "/approvals"),
                attention("Assessments waiting for approval", "Creator and reviewer separation is enforced.", counts.pendingAssessments(), counts.pendingAssessments() > 0 ? "WARNING" : "NEUTRAL", "/approvals"),
                attention("Evaluated results to publish", "Students cannot see scores until publication.", counts.pendingResults(), counts.pendingResults() > 0 ? "INFO" : "NEUTRAL", "/reports")
        );
    }

    private DashboardMetric metric(
            String label,
            Object value,
            String context,
            String tone,
            String href
    ) {
        return new DashboardMetric(label, String.valueOf(value), context, tone, href);
    }

    private DashboardAttention attention(
            String title,
            String description,
            long count,
            String severity,
            String href
    ) {
        return new DashboardAttention(title, description, count, severity, href);
    }

    private List<Question> organisationQuestions() {
        return questions.findAllByOrganisationIdOrderByUpdatedAtDesc(
                session.organisationId()
        );
    }

    private List<Assessment> organisationAssessments() {
        return assessments.findAllByOrganisationIdOrderByUpdatedAtDesc(
                session.organisationId()
        );
    }

    private List<AssessmentAttempt> organisationAttempts() {
        return attempts.findAllByOrganisationIdOrderBySubmittedAtDesc(
                session.organisationId()
        );
    }

    private BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        if (present.isEmpty()) return BigDecimal.ZERO.setScale(2);
        return present.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(present.size()), 2, RoundingMode.HALF_UP);
    }

    private String greeting() {
        UserAccount user = users.findById(session.userId()).orElseThrow();
        return "Welcome back, " + user.getFirstName();
    }

    private long unread() {
        return notifications
                .countByOrganisationIdAndRecipientUserIdAndReadAtIsNull(
                        session.organisationId(), session.userId()
                );
    }

    private record DashboardCounts(
            long activeStudents,
            long pendingQuestions,
            long pendingAssessments,
            long pendingResults
    ) {
    }
}
