package com.rabbit.aip.attempt;

import com.rabbit.aip.assessment.Assessment;
import com.rabbit.aip.assessment.AssessmentRepository;
import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.notification.NotificationService;
import com.rabbit.aip.notification.NotificationType;
import com.rabbit.aip.question.Question;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.settings.SettingsService;
import com.rabbit.aip.user.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttemptExpiryService {

    private final AssessmentAttemptRepository attempts;
    private final AssessmentRepository assessments;
    private final AttemptResponseRepository responses;
    private final QuestionRepository questions;
    private final EvaluationEngine evaluationEngine;
    private final SettingsService settings;
    private final AuditService audit;
    private final NotificationService notifications;

    public AttemptExpiryService(
            AssessmentAttemptRepository attempts,
            AssessmentRepository assessments,
            AttemptResponseRepository responses,
            QuestionRepository questions,
            EvaluationEngine evaluationEngine,
            SettingsService settings,
            AuditService audit,
            NotificationService notifications
    ) {
        this.attempts = attempts;
        this.assessments = assessments;
        this.responses = responses;
        this.questions = questions;
        this.evaluationEngine = evaluationEngine;
        this.settings = settings;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Scheduled(fixedDelayString = "${rabbit.attempt-expiry.fixed-delay-ms:5000}")
    @Transactional
    public void submitExpiredAttempts() {
        submitExpiredAttempts(Instant.now());
    }

    int submitExpiredAttempts(Instant cutoff) {
        List<AssessmentAttempt> expired = attempts.findExpiredForUpdate(
                AttemptStatus.IN_PROGRESS, cutoff
        );
        expired.forEach(this::submitExpiredAttempt);
        return expired.size();
    }

    private void submitExpiredAttempt(AssessmentAttempt attempt) {
        UUID organisationId = attempt.getOrganisationId();
        Assessment assessment = assessments.findByIdAndOrganisationId(
                        attempt.getAssessmentId(), organisationId
                )
                .orElseThrow();
        List<Question> assessmentQuestions = questions
                .findAllByIdInAndOrganisationId(
                        assessment.getQuestionIds(), organisationId
                );
        EvaluationEngine.EvaluationOutcome outcome = evaluationEngine.evaluate(
                assessment,
                assessmentQuestions,
                responses.findAllByAttemptId(attempt.getId())
        );
        attempt.submit(
                outcome.score(),
                outcome.maxScore(),
                outcome.percentage(),
                settings.resolveGrade(organisationId, outcome.percentage()),
                outcome.correctAnswers(),
                outcome.wrongAnswers(),
                outcome.unansweredAnswers(),
                true
        );
        audit.recordSystem(
                organisationId,
                attempt.getStudentUserId(),
                "DEL",
                "AUTO_SUBMIT_EXPIRED",
                "AssessmentAttempt",
                attempt.getId(),
                "IN_PROGRESS",
                AttemptStatus.AUTO_SUBMITTED.name()
        );
        audit.recordSystem(
                organisationId,
                attempt.getStudentUserId(),
                "EVL",
                "AUTO_EVALUATE_EXPIRED",
                "AssessmentAttempt",
                attempt.getId(),
                null,
                outcome.score() + "/" + outcome.maxScore()
        );
        notifications.notifyRolesForOrganisation(
                organisationId,
                Set.of(UserRole.FACULTY, UserRole.ACADEMIC_HEAD, UserRole.ORG_ADMIN),
                NotificationType.ASSESSMENT_SUBMITTED,
                "Assessment automatically submitted",
                assessment.getTitle() + " reached its server-enforced time limit.",
                "/reports/assessments/" + assessment.getId(),
                false
        );
    }
}
