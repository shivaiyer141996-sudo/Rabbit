package com.rabbit.aip.evaluation;

import com.rabbit.aip.assessment.Assessment;
import com.rabbit.aip.assessment.AssessmentRepository;
import com.rabbit.aip.attempt.AssessmentAttempt;
import com.rabbit.aip.attempt.AssessmentAttemptRepository;
import com.rabbit.aip.attempt.AttemptResponse;
import com.rabbit.aip.attempt.AttemptResponseRepository;
import com.rabbit.aip.attempt.AttemptStatus;
import com.rabbit.aip.attempt.EvaluationEngine;
import com.rabbit.aip.attempt.ResultPublicationStatus;
import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.evaluation.EvaluationDtos.AssessmentEvaluationSummary;
import com.rabbit.aip.evaluation.EvaluationDtos.EvaluationRow;
import com.rabbit.aip.evaluation.EvaluationDtos.PublicationResponse;
import com.rabbit.aip.notification.NotificationService;
import com.rabbit.aip.notification.NotificationType;
import com.rabbit.aip.question.Question;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.settings.SettingsService;
import com.rabbit.aip.user.UserAccount;
import com.rabbit.aip.user.UserAccountRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {

    private final AssessmentRepository assessments;
    private final AssessmentAttemptRepository attempts;
    private final AttemptResponseRepository responses;
    private final QuestionRepository questions;
    private final UserAccountRepository users;
    private final EvaluationEngine engine;
    private final SettingsService settings;
    private final CurrentSession session;
    private final AuditService audit;
    private final NotificationService notifications;

    public EvaluationService(
            AssessmentRepository assessments,
            AssessmentAttemptRepository attempts,
            AttemptResponseRepository responses,
            QuestionRepository questions,
            UserAccountRepository users,
            EvaluationEngine engine,
            SettingsService settings,
            CurrentSession session,
            AuditService audit,
            NotificationService notifications
    ) {
        this.assessments = assessments;
        this.attempts = attempts;
        this.responses = responses;
        this.questions = questions;
        this.users = users;
        this.engine = engine;
        this.settings = settings;
        this.session = session;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public AssessmentEvaluationSummary assessmentResults(UUID assessmentId) {
        Assessment assessment = findAssessment(assessmentId);
        List<AssessmentAttempt> evaluated = attempts
                .findAllByOrganisationIdAndAssessmentIdOrderBySubmittedAtAsc(
                        session.organisationId(), assessmentId
                ).stream()
                .filter(item -> item.getStatus() != AttemptStatus.IN_PROGRESS)
                .toList();
        BigDecimal average = evaluated.isEmpty()
                ? BigDecimal.ZERO
                : evaluated.stream()
                        .map(AssessmentAttempt::getPercentage)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(
                                BigDecimal.valueOf(evaluated.size()),
                                2,
                                RoundingMode.HALF_UP
                        );
        return new AssessmentEvaluationSummary(
                assessment.getId(),
                assessment.getTitle(),
                evaluated.size(),
                evaluated.stream()
                        .filter(item -> item.getResultStatus()
                                == ResultPublicationStatus.PENDING_PUBLICATION)
                        .count(),
                evaluated.stream()
                        .filter(item -> item.getResultStatus()
                                == ResultPublicationStatus.PUBLISHED)
                        .count(),
                average,
                evaluated.stream().map(this::row).toList()
        );
    }

    @Transactional
    public PublicationResponse publish(UUID assessmentId) {
        Assessment assessment = findAssessment(assessmentId);
        List<AssessmentAttempt> pending = attempts
                .findAllByOrganisationIdAndAssessmentIdOrderBySubmittedAtAsc(
                        session.organisationId(), assessmentId
                ).stream()
                .filter(item -> item.getStatus() != AttemptStatus.IN_PROGRESS)
                .filter(item -> item.getResultStatus()
                        == ResultPublicationStatus.PENDING_PUBLICATION)
                .toList();
        if (pending.isEmpty()) {
            throw DomainException.badRequest(
                    "NO_RESULTS_TO_PUBLISH",
                    "There are no evaluated results waiting for publication."
            );
        }
        Instant publishedAt = Instant.now();
        pending.forEach(attempt -> {
            attempt.publishResult(session.userId());
            notifications.notifyUser(
                    attempt.getStudentUserId(),
                    NotificationType.RESULT_PUBLISHED,
                    "Your result is ready",
                    assessment.getTitle() + " has been evaluated and published.",
                    "/results/" + attempt.getId(),
                    false
            );
        });
        audit.record(
                "EVL",
                "PUBLISH_RESULTS",
                "Assessment",
                assessmentId,
                "PENDING_PUBLICATION",
                "PUBLISHED:" + pending.size()
        );
        return new PublicationResponse(assessmentId, pending.size(), publishedAt);
    }

    @Transactional
    public EvaluationRow reEvaluate(UUID attemptId, String reason) {
        AssessmentAttempt attempt = attempts
                .findByIdAndOrganisationId(attemptId, session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "ATTEMPT_NOT_FOUND", "Assessment attempt was not found."
                ));
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw DomainException.badRequest(
                    "ATTEMPT_NOT_EVALUATED",
                    "An in-progress attempt cannot be re-evaluated."
            );
        }
        Assessment assessment = findAssessment(attempt.getAssessmentId());
        List<Question> assessmentQuestions = questions
                .findAllByIdInAndOrganisationId(
                        assessment.getQuestionIds(), session.organisationId()
                );
        List<AttemptResponse> attemptResponses = responses.findAllByAttemptId(attemptId);
        String before = attempt.getScore() + "/" + attempt.getMaxScore()
                + " v" + attempt.getEvaluationVersion();
        EvaluationEngine.EvaluationOutcome outcome = engine.evaluate(
                assessment, assessmentQuestions, attemptResponses
        );
        attempt.reEvaluate(
                outcome.score(),
                outcome.maxScore(),
                outcome.percentage(),
                settings.resolveGrade(outcome.percentage()),
                outcome.correctAnswers(),
                outcome.wrongAnswers(),
                outcome.unansweredAnswers()
        );
        audit.record(
                "EVL",
                "RE_EVALUATE",
                "AssessmentAttempt",
                attemptId,
                before,
                outcome.score() + "/" + outcome.maxScore()
                        + " v" + attempt.getEvaluationVersion()
                        + " Reason: " + reason.trim()
        );
        notifications.notifyUser(
                attempt.getStudentUserId(),
                NotificationType.SYSTEM,
                "Result re-evaluated",
                assessment.getTitle()
                        + " was re-evaluated and is awaiting publication.",
                "/results/" + attemptId,
                true
        );
        return row(attempt);
    }

    private EvaluationRow row(AssessmentAttempt attempt) {
        UserAccount student = users.findById(attempt.getStudentUserId())
                .orElseThrow();
        return new EvaluationRow(
                attempt.getId(),
                attempt.getStudentUserId(),
                student.getFirstName() + " " + student.getLastName(),
                attempt.getStatus(),
                attempt.getResultStatus(),
                attempt.getScore(),
                attempt.getMaxScore(),
                attempt.getPercentage(),
                attempt.getGrade(),
                attempt.getCorrectAnswers(),
                attempt.getWrongAnswers(),
                attempt.getUnansweredAnswers(),
                attempt.getEvaluationVersion(),
                attempt.getEvaluatedAt(),
                attempt.getResultPublishedAt()
        );
    }

    private Assessment findAssessment(UUID id) {
        return assessments.findByIdAndOrganisationId(id, session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "ASSESSMENT_NOT_FOUND", "Assessment was not found."
                ));
    }
}
