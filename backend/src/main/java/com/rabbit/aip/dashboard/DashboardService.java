package com.rabbit.aip.dashboard;

import com.rabbit.aip.assessment.AssessmentRepository;
import com.rabbit.aip.assessment.AssessmentStatus;
import com.rabbit.aip.attempt.AssessmentAttemptRepository;
import com.rabbit.aip.dashboard.DashboardDtos.DashboardAttention;
import com.rabbit.aip.dashboard.DashboardDtos.DashboardMetric;
import com.rabbit.aip.dashboard.DashboardDtos.DashboardResponse;
import com.rabbit.aip.dashboard.DashboardDtos.DashboardTrend;
import com.rabbit.aip.notification.NotificationRepository;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.question.QuestionStatus;
import com.rabbit.aip.report.ReportDtos.IntelligenceOverview;
import com.rabbit.aip.report.ReportDtos.StudentPerformanceReport;
import com.rabbit.aip.report.ReportService;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.user.OrganisationMembershipRepository;
import com.rabbit.aip.user.UserAccount;
import com.rabbit.aip.user.UserAccountRepository;
import com.rabbit.aip.user.UserRole;
import java.util.ArrayList;
import java.util.List;
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

    public DashboardService(
            ReportService reports,
            QuestionRepository questions,
            AssessmentRepository assessments,
            AssessmentAttemptRepository attempts,
            OrganisationMembershipRepository memberships,
            NotificationRepository notifications,
            UserAccountRepository users,
            CurrentSession session
    ) {
        this.reports = reports;
        this.questions = questions;
        this.assessments = assessments;
        this.attempts = attempts;
        this.memberships = memberships;
        this.notifications = notifications;
        this.users = users;
        this.session = session;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        return session.role() == UserRole.STUDENT
                ? studentDashboard()
                : staffDashboard();
    }

    private DashboardResponse staffDashboard() {
        IntelligenceOverview overview = reports.overview();
        long pendingQuestions = questions
                .findAllByOrganisationIdOrderByUpdatedAtDesc(session.organisationId())
                .stream()
                .filter(item -> item.getStatus() == QuestionStatus.UNDER_REVIEW)
                .count();
        long pendingAssessments = assessments
                .findAllByOrganisationIdOrderByUpdatedAtDesc(session.organisationId())
                .stream()
                .filter(item -> item.getStatus() == AssessmentStatus.READY_FOR_REVIEW)
                .count();
        long pendingResults = attempts
                .findAllByOrganisationIdOrderBySubmittedAtDesc(session.organisationId())
                .stream()
                .filter(item -> item.getSubmittedAt() != null)
                .filter(item -> item.getResultStatus()
                        == com.rabbit.aip.attempt.ResultPublicationStatus.PENDING_PUBLICATION)
                .count();
        long activeStudents = memberships
                .findAllByOrganisationIdOrderByCreatedAtDesc(session.organisationId())
                .stream()
                .filter(item -> item.getRole() == UserRole.STUDENT)
                .count();
        List<DashboardAttention> attention = new ArrayList<>();
        attention.add(new DashboardAttention(
                "Questions waiting for review",
                "Complete the academic checklist before making a decision.",
                pendingQuestions,
                pendingQuestions > 0 ? "WARNING" : "NEUTRAL",
                "/approvals"
        ));
        attention.add(new DashboardAttention(
                "Assessments waiting for approval",
                "Creator and reviewer separation is enforced.",
                pendingAssessments,
                pendingAssessments > 0 ? "WARNING" : "NEUTRAL",
                "/approvals"
        ));
        attention.add(new DashboardAttention(
                "Evaluated results to publish",
                "Students cannot see scores until publication.",
                pendingResults,
                pendingResults > 0 ? "INFO" : "NEUTRAL",
                "/reports"
        ));
        return new DashboardResponse(
                session.role().name(),
                greeting(),
                "Academic performance, governance queues, and interventions in one view.",
                List.of(
                        new DashboardMetric(
                                "Active students",
                                String.valueOf(activeStudents),
                                "Current organisation",
                                "PRIMARY",
                                "/users"
                        ),
                        new DashboardMetric(
                                "Average score",
                                overview.averageScore() + "%",
                                "Published results",
                                "SUCCESS",
                                "/reports"
                        ),
                        new DashboardMetric(
                                "Pass rate",
                                overview.passRate() + "%",
                                "Configured grade threshold",
                                "INFO",
                                "/reports"
                        ),
                        new DashboardMetric(
                                "At-risk students",
                                String.valueOf(overview.atRiskStudents()),
                                "Below threshold twice",
                                overview.atRiskStudents() > 0 ? "DANGER" : "SUCCESS",
                                "/reports"
                        )
                ),
                overview.performanceTrend().stream()
                        .map(item -> new DashboardTrend(item.label(), item.value()))
                        .toList(),
                attention,
                unread()
        );
    }

    private DashboardResponse studentDashboard() {
        StudentPerformanceReport performance = reports.myPerformance();
        long available = assessments
                .findAllByOrganisationIdOrderByUpdatedAtDesc(session.organisationId())
                .stream()
                .filter(item -> item.getStatus() == AssessmentStatus.SCHEDULED)
                .count();
        return new DashboardResponse(
                session.role().name(),
                greeting(),
                "Track published results and focus on the next assessment.",
                List.of(
                        new DashboardMetric(
                                "Upcoming assessments",
                                String.valueOf(available),
                                "Scheduled for your organisation",
                                "PRIMARY",
                                "/student/assessments"
                        ),
                        new DashboardMetric(
                                "Average score",
                                performance.averagePercentage() + "%",
                                "Published results",
                                "SUCCESS",
                                "/reports"
                        ),
                        new DashboardMetric(
                                "Best score",
                                performance.bestPercentage() + "%",
                                "Personal best",
                                "INFO",
                                "/reports"
                        ),
                        new DashboardMetric(
                                "Progress",
                                performance.trajectory(),
                                performance.atRisk()
                                        ? "Academic support recommended"
                                        : "Based on recent results",
                                performance.atRisk() ? "DANGER" : "SUCCESS",
                                "/reports"
                        )
                ),
                performance.results().stream()
                        .map(item -> new DashboardTrend(
                                item.assessmentTitle(), item.percentage()
                        ))
                        .toList(),
                List.of(),
                unread()
        );
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
}
