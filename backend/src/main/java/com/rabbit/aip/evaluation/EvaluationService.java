package com.rabbit.aip.evaluation;

import com.rabbit.aip.assessment.Assessment;
import com.rabbit.aip.assessment.AssessmentRepository;
import com.rabbit.aip.attempt.AssessmentAttempt;
import com.rabbit.aip.attempt.AssessmentAttemptRepository;
import com.rabbit.aip.attempt.AttemptResponse;
import com.rabbit.aip.attempt.AttemptResponseRepository;
import com.rabbit.aip.attempt.AttemptPresentationOrder;
import com.rabbit.aip.attempt.AttemptStatus;
import com.rabbit.aip.attempt.EvaluationEngine;
import com.rabbit.aip.attempt.ResultPublicationStatus;
import com.rabbit.aip.attempt.ScoringService;
import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.audit.AuditService.AuditEventResponse;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.evaluation.EvaluationDtos.AssessmentEvaluationSummary;
import com.rabbit.aip.evaluation.EvaluationDtos.EvaluationRow;
import com.rabbit.aip.evaluation.EvaluationDtos.PublicationResponse;
import com.rabbit.aip.evaluation.EvaluationDtos.AssessmentMonitor;
import com.rabbit.aip.evaluation.EvaluationDtos.EvaluationAuditEntry;
import com.rabbit.aip.evaluation.EvaluationDtos.ManualAttemptReview;
import com.rabbit.aip.evaluation.EvaluationDtos.ManualReviewOption;
import com.rabbit.aip.evaluation.EvaluationDtos.ManualReviewQuestion;
import com.rabbit.aip.evaluation.EvaluationDtos.ManualScoreAdjustment;
import com.rabbit.aip.evaluation.EvaluationDtos.ManualScoreUpdateRequest;
import com.rabbit.aip.evaluation.EvaluationDtos.MonitoringRow;
import com.rabbit.aip.notification.NotificationService;
import com.rabbit.aip.notification.NotificationType;
import com.rabbit.aip.question.Question;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.question.QuestionOption;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.settings.AcademicSubject;
import com.rabbit.aip.settings.AcademicSubjectRepository;
import com.rabbit.aip.settings.AcademicTopic;
import com.rabbit.aip.settings.AcademicTopicRepository;
import com.rabbit.aip.settings.SettingsService;
import com.rabbit.aip.user.UserAccount;
import com.rabbit.aip.user.UserAccountRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
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
    private final ScoringService scoring;
    private final SettingsService settings;
    private final AcademicSubjectRepository subjects;
    private final AcademicTopicRepository topics;
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
            ScoringService scoring,
            SettingsService settings,
            AcademicSubjectRepository subjects,
            AcademicTopicRepository topics,
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
        this.scoring = scoring;
        this.settings = settings;
        this.subjects = subjects;
        this.topics = topics;
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

    @Transactional(readOnly = true)
    public AssessmentMonitor monitor(UUID assessmentId) {
        Assessment assessment = findAssessment(assessmentId);
        List<AssessmentAttempt> assessmentAttempts = attempts
                .findAllByOrganisationIdAndAssessmentIdOrderBySubmittedAtAsc(
                        session.organisationId(), assessmentId
                );
        Map<UUID, UserAccount> studentMap = users.findAllById(
                        assessmentAttempts.stream()
                                .map(AssessmentAttempt::getStudentUserId)
                                .distinct()
                                .toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(
                        UserAccount::getId,
                        java.util.function.Function.identity()
                ));
        Map<UUID, Long> answeredByAttempt = assessmentAttempts.isEmpty()
                ? Map.of()
                : responses.findAllByAttemptIdIn(
                                assessmentAttempts.stream()
                                        .map(AssessmentAttempt::getId)
                                        .toList()
                        ).stream()
                        .filter(item -> !item.getSelectedOptionIds().isEmpty())
                        .collect(java.util.stream.Collectors.groupingBy(
                                AttemptResponse::getAttemptId,
                                java.util.stream.Collectors.counting()
                        ));
        Instant now = Instant.now();
        List<MonitoringRow> rows = assessmentAttempts.stream()
                .map(attempt -> {
                    UserAccount student = studentMap.get(attempt.getStudentUserId());
                    int answered = answeredByAttempt
                            .getOrDefault(attempt.getId(), 0L)
                            .intValue();
                    long progress = assessment.getQuestionCount() == 0
                            ? 0
                            : Math.round(answered * 100.0 / assessment.getQuestionCount());
                    long secondsRemaining = attempt.getStatus() == AttemptStatus.IN_PROGRESS
                            ? Math.max(0, Duration.between(now, attempt.getExpiresAt()).toSeconds())
                            : 0;
                    return new MonitoringRow(
                            attempt.getId(),
                            attempt.getStudentUserId(),
                            student == null
                                    ? "Unknown student"
                                    : student.getFirstName() + " " + student.getLastName(),
                            attempt.getStatus(),
                            attempt.getResultStatus(),
                            attempt.getStartedAt(),
                            attempt.getExpiresAt(),
                            attempt.getSubmittedAt(),
                            answered,
                            assessment.getQuestionCount(),
                            progress,
                            secondsRemaining
                    );
                })
                .sorted(Comparator
                        .comparing((MonitoringRow row) ->
                                row.attemptStatus() != AttemptStatus.IN_PROGRESS
                        )
                        .thenComparing(
                                MonitoringRow::startedAt,
                                Comparator.reverseOrder()
                        ))
                .toList();
        return new AssessmentMonitor(
                assessment.getId(),
                assessment.getTitle(),
                now,
                rows.size(),
                rows.stream().filter(item ->
                        item.attemptStatus() == AttemptStatus.IN_PROGRESS
                ).count(),
                rows.stream().filter(item ->
                        item.attemptStatus() == AttemptStatus.SUBMITTED
                ).count(),
                rows.stream().filter(item ->
                        item.attemptStatus() == AttemptStatus.AUTO_SUBMITTED
                ).count(),
                rows
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
            audit.record(
                    "EVL",
                    "PUBLISH_RESULT",
                    "AssessmentAttempt",
                    attempt.getId(),
                    "PENDING_PUBLICATION v" + attempt.getEvaluationVersion(),
                    "PUBLISHED v" + attempt.getEvaluationVersion()
            );
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

    @Transactional(readOnly = true)
    public ManualAttemptReview manualReview(UUID attemptId) {
        AssessmentAttempt attempt = attempts
                .findByIdAndOrganisationId(attemptId, session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "ATTEMPT_NOT_FOUND", "Assessment attempt was not found."
                ));
        ensureCompleted(attempt);
        Assessment assessment = findAssessment(attempt.getAssessmentId());
        UserAccount student = users.findById(attempt.getStudentUserId())
                .orElseThrow(() -> DomainException.notFound(
                        "STUDENT_NOT_FOUND", "The student was not found."
                ));
        Map<UUID, AttemptResponse> responseMap = responses
                .findAllByAttemptId(attemptId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        AttemptResponse::getQuestionId,
                        java.util.function.Function.identity()
                ));
        Map<UUID, Question> questionMap = questions
                .findAllByIdInAndOrganisationId(
                        assessment.getQuestionIds(), session.organisationId()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Question::getId,
                        java.util.function.Function.identity()
                ));
        Map<UUID, String> subjectNames = subjects
                .findAllByOrganisationIdOrderByName(session.organisationId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        AcademicSubject::getId, AcademicSubject::getName
                ));
        Map<UUID, String> topicNames = topics
                .findAllByOrganisationIdOrderByName(session.organisationId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        AcademicTopic::getId, AcademicTopic::getName
                ));
        List<Question> orderedQuestions = AttemptPresentationOrder.order(
                attempt.getId(),
                "questions",
                assessment.getQuestionIds().stream()
                        .map(questionMap::get)
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                Question::getId,
                assessment.isShuffleQuestions()
        );
        List<ManualReviewQuestion> reviewQuestions = orderedQuestions.stream()
                .map(question -> manualQuestion(
                        attempt.getId(),
                        assessment.isShuffleOptions(),
                        question,
                        responseMap.get(question.getId()),
                        subjectNames.getOrDefault(
                                question.getSubjectId(), "Unknown subject"
                        ),
                        topicNames.getOrDefault(
                                question.getTopicId(), "Unknown topic"
                        )
                ))
                .toList();
        return new ManualAttemptReview(
                attempt.getId(),
                assessment.getId(),
                assessment.getTitle(),
                attempt.getStudentUserId(),
                student.getFirstName() + " " + student.getLastName(),
                attempt.getStatus(),
                attempt.getResultStatus(),
                attempt.getScore(),
                attempt.getMaxScore(),
                attempt.getPercentage(),
                attempt.getGrade(),
                attempt.getEvaluationVersion(),
                attempt.getEvaluatedAt(),
                reviewQuestions,
                audit.entityHistory("AssessmentAttempt", attemptId).stream()
                        .filter(item -> "EVL".equalsIgnoreCase(item.module()))
                        .map(this::auditEntry)
                        .toList()
        );
    }

    @Transactional
    public ManualAttemptReview updateScore(
            UUID attemptId,
            ManualScoreUpdateRequest request
    ) {
        AssessmentAttempt attempt = attempts
                .findByIdAndOrganisationIdForUpdate(
                        attemptId, session.organisationId()
                )
                .orElseThrow(() -> DomainException.notFound(
                        "ATTEMPT_NOT_FOUND", "Assessment attempt was not found."
                ));
        ensureCompleted(attempt);
        Assessment assessment = findAssessment(attempt.getAssessmentId());
        List<Question> assessmentQuestions = questions
                .findAllByIdInAndOrganisationId(
                        assessment.getQuestionIds(), session.organisationId()
                );
        Map<UUID, Question> questionMap = assessmentQuestions.stream()
                .collect(java.util.stream.Collectors.toMap(
                        Question::getId,
                        java.util.function.Function.identity()
                ));
        Set<UUID> adjustmentIds = new HashSet<>();
        for (ManualScoreAdjustment adjustment : request.adjustments()) {
            if (!adjustmentIds.add(adjustment.questionId())) {
                throw DomainException.badRequest(
                        "DUPLICATE_SCORE_ADJUSTMENT",
                        "A question can only appear once in a score update."
                );
            }
            Question question = questionMap.get(adjustment.questionId());
            if (question == null) {
                throw DomainException.badRequest(
                        "QUESTION_NOT_IN_ASSESSMENT",
                        "Every adjusted question must belong to this assessment."
                );
            }
            BigDecimal value = adjustment.awardedMarks().setScale(
                    2, RoundingMode.HALF_UP
            );
            BigDecimal minimum = question.getNegativeMarks().negate();
            if (value.compareTo(minimum) < 0
                    || value.compareTo(question.getMarks()) > 0) {
                throw DomainException.badRequest(
                        "SCORE_ADJUSTMENT_OUT_OF_RANGE",
                        question.getCode() + " must be between " + minimum
                                + " and " + question.getMarks() + "."
                );
            }
        }

        Map<UUID, AttemptResponse> responseMap = new HashMap<>();
        responses.findAllByAttemptId(attemptId)
                .forEach(item -> responseMap.put(item.getQuestionId(), item));
        List<String> changes = new ArrayList<>();
        for (ManualScoreAdjustment adjustment : request.adjustments()) {
            Question question = questionMap.get(adjustment.questionId());
            BigDecimal nextMarks = adjustment.awardedMarks().setScale(
                    2, RoundingMode.HALF_UP
            );
            AttemptResponse response = responseMap.get(question.getId());
            if (response == null) {
                response = new AttemptResponse(
                        attemptId, question.getId(), Set.of(), false, 0
                );
                responseMap.put(question.getId(), response);
                responses.save(response);
            }
            BigDecimal previousMarks = response.getAwardedMarks() == null
                    ? BigDecimal.ZERO.setScale(2)
                    : response.getAwardedMarks().setScale(2, RoundingMode.HALF_UP);
            if (previousMarks.compareTo(nextMarks) == 0) continue;
            Set<UUID> correctOptions = question.getOptions().stream()
                    .filter(QuestionOption::isCorrect)
                    .map(QuestionOption::getId)
                    .collect(java.util.stream.Collectors.toSet());
            boolean exact = !response.getSelectedOptionIds().isEmpty()
                    && response.getSelectedOptionIds().equals(correctOptions);
            response.recordEvaluation(nextMarks, exact);
            changes.add(
                    question.getCode() + ": " + previousMarks + " -> " + nextMarks
            );
        }
        if (changes.isEmpty()) {
            throw DomainException.badRequest(
                    "SCORE_UPDATE_UNCHANGED",
                    "Change at least one awarded mark before saving."
            );
        }

        List<BigDecimal> itemScores = new ArrayList<>();
        int correctAnswers = 0;
        int wrongAnswers = 0;
        int unansweredAnswers = 0;
        for (Question question : assessmentQuestions) {
            AttemptResponse response = responseMap.get(question.getId());
            BigDecimal marks = response == null || response.getAwardedMarks() == null
                    ? BigDecimal.ZERO
                    : response.getAwardedMarks();
            itemScores.add(marks);
            if (response == null || response.getSelectedOptionIds().isEmpty()) {
                unansweredAnswers += 1;
            } else if (Boolean.TRUE.equals(response.getCorrect())) {
                correctAnswers += 1;
            } else {
                wrongAnswers += 1;
            }
        }
        BigDecimal updatedScore = scoring.floorTotal(itemScores);
        BigDecimal updatedPercentage = assessment.getTotalMarks().signum() == 0
                ? BigDecimal.ZERO.setScale(2)
                : updatedScore.multiply(BigDecimal.valueOf(100))
                        .divide(
                                assessment.getTotalMarks(),
                                2,
                                RoundingMode.HALF_UP
                        );
        String before = attempt.getScore() + "/" + attempt.getMaxScore()
                + " v" + attempt.getEvaluationVersion()
                + " " + attempt.getResultStatus();
        attempt.reEvaluate(
                updatedScore,
                assessment.getTotalMarks(),
                updatedPercentage,
                settings.resolveGrade(updatedPercentage),
                correctAnswers,
                wrongAnswers,
                unansweredAnswers
        );
        audit.record(
                "EVL",
                "MANUAL_SCORE_UPDATE",
                "AssessmentAttempt",
                attemptId,
                before,
                updatedScore + "/" + assessment.getTotalMarks()
                        + " v" + attempt.getEvaluationVersion()
                        + " PENDING_PUBLICATION; Changes: "
                        + String.join(", ", changes)
                        + "; Reason: " + request.reason().trim()
        );
        notifications.notifyUser(
                attempt.getStudentUserId(),
                NotificationType.SYSTEM,
                "Result score updated",
                assessment.getTitle()
                        + " was manually reviewed and is awaiting publication.",
                "/results/" + attemptId,
                true
        );
        return manualReview(attemptId);
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

    private ManualReviewQuestion manualQuestion(
            UUID attemptId,
            boolean shuffleOptions,
            Question question,
            AttemptResponse response,
            String subjectName,
            String topicName
    ) {
        Set<UUID> selected = response == null
                ? Set.of()
                : response.getSelectedOptionIds();
        return new ManualReviewQuestion(
                question.getId(),
                question.getCode(),
                question.getStem(),
                subjectName,
                topicName,
                question.getDifficulty(),
                response == null || response.getAwardedMarks() == null
                        ? BigDecimal.ZERO.setScale(2)
                        : response.getAwardedMarks(),
                question.getNegativeMarks().negate(),
                question.getMarks(),
                !selected.isEmpty(),
                response != null && Boolean.TRUE.equals(response.getCorrect()),
                response == null ? 0 : response.getTimeSpentSeconds(),
                manualOptions(attemptId, shuffleOptions, question, selected),
                question.getExplanation()
        );
    }

    private List<ManualReviewOption> manualOptions(
            UUID attemptId,
            boolean shuffleOptions,
            Question question,
            Set<UUID> selected
    ) {
        List<QuestionOption> source = question.getOptions().stream()
                .sorted(Comparator.comparingInt(QuestionOption::getSortOrder))
                .toList();
        List<QuestionOption> ordered = AttemptPresentationOrder.order(
                attemptId,
                "options:" + question.getId(),
                source,
                QuestionOption::getId,
                shuffleOptions
        );
        return IntStream.range(0, ordered.size())
                .mapToObj(index -> {
                    QuestionOption option = ordered.get(index);
                    return new ManualReviewOption(
                            option.getId(),
                            shuffleOptions
                                    ? String.valueOf((char) ('A' + index))
                                    : option.getLabel(),
                            option.getText(),
                            selected.contains(option.getId()),
                            option.isCorrect()
                    );
                })
                .toList();
    }

    private EvaluationAuditEntry auditEntry(AuditEventResponse event) {
        return new EvaluationAuditEntry(
                event.id(),
                event.timestamp(),
                event.actorEmail(),
                event.actorRole(),
                event.action(),
                event.beforeValue(),
                event.afterValue()
        );
    }

    private void ensureCompleted(AssessmentAttempt attempt) {
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw DomainException.badRequest(
                    "ATTEMPT_NOT_EVALUATED",
                    "An in-progress attempt cannot be manually reviewed."
            );
        }
    }

    private Assessment findAssessment(UUID id) {
        Assessment assessment = assessments
                .findByIdAndOrganisationId(id, session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "ASSESSMENT_NOT_FOUND", "Assessment was not found."
                ));
        if (session.role() == com.rabbit.aip.user.UserRole.FACULTY
                && !assessment.getCreatedBy().equals(session.userId())) {
            throw DomainException.forbidden(
                    "ASSESSMENT_ACCESS_DENIED",
                    "Teachers can only manage results for their own assessments."
            );
        }
        return assessment;
    }
}
