package com.rabbit.aip.attempt;

import com.rabbit.aip.assessment.Assessment;
import com.rabbit.aip.assessment.AssessmentRepository;
import com.rabbit.aip.assessment.AssessmentStatus;
import com.rabbit.aip.attempt.AttemptDtos.AttemptView;
import com.rabbit.aip.attempt.AttemptDtos.PlayerOption;
import com.rabbit.aip.attempt.AttemptDtos.PlayerQuestion;
import com.rabbit.aip.attempt.AttemptDtos.ResultView;
import com.rabbit.aip.attempt.AttemptDtos.ResultQuestion;
import com.rabbit.aip.attempt.AttemptDtos.ResultOption;
import com.rabbit.aip.attempt.AttemptDtos.SaveResponseRequest;
import com.rabbit.aip.attempt.AttemptDtos.SavedResponse;
import com.rabbit.aip.attempt.AttemptDtos.StudentAssessment;
import com.rabbit.aip.attempt.AttemptDtos.StudentAssessmentInstructions;
import com.rabbit.aip.attempt.AttemptDtos.StudentAssessmentStatus;
import com.rabbit.aip.attempt.AttemptDtos.AttemptHistoryItem;
import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.notification.NotificationService;
import com.rabbit.aip.notification.NotificationType;
import com.rabbit.aip.question.Question;
import com.rabbit.aip.question.QuestionOption;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.settings.SettingsService;
import com.rabbit.aip.settings.AcademicSubjectRepository;
import com.rabbit.aip.settings.AcademicTopicRepository;
import com.rabbit.aip.user.AccountStatus;
import com.rabbit.aip.user.OrganisationMembership;
import com.rabbit.aip.user.OrganisationMembershipRepository;
import com.rabbit.aip.user.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttemptService {

    private final AssessmentRepository assessments;
    private final AssessmentAttemptRepository attempts;
    private final AttemptResponseRepository responses;
    private final QuestionRepository questions;
    private final OrganisationMembershipRepository memberships;
    private final CurrentSession session;
    private final EvaluationEngine evaluationEngine;
    private final SettingsService settings;
    private final AuditService audit;
    private final NotificationService notifications;
    private final AcademicSubjectRepository subjects;
    private final AcademicTopicRepository topics;

    public AttemptService(
            AssessmentRepository assessments,
            AssessmentAttemptRepository attempts,
            AttemptResponseRepository responses,
            QuestionRepository questions,
            OrganisationMembershipRepository memberships,
            CurrentSession session,
            EvaluationEngine evaluationEngine,
            SettingsService settings,
            AuditService audit,
            NotificationService notifications,
            AcademicSubjectRepository subjects,
            AcademicTopicRepository topics
    ) {
        this.assessments = assessments;
        this.attempts = attempts;
        this.responses = responses;
        this.questions = questions;
        this.memberships = memberships;
        this.session = session;
        this.evaluationEngine = evaluationEngine;
        this.settings = settings;
        this.audit = audit;
        this.notifications = notifications;
        this.subjects = subjects;
        this.topics = topics;
    }

    @Transactional(readOnly = true)
    public List<StudentAssessment> available() {
        return catalog().stream()
                .filter(item -> item.status() == StudentAssessmentStatus.AVAILABLE_NOW)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StudentAssessment> catalog() {
        Instant now = Instant.now();
        OrganisationMembership membership = currentMembership();
        Set<UUID> completed = attempts
                .findAllByOrganisationIdAndStudentUserIdOrderByStartedAtDesc(
                        session.organisationId(), session.userId()
                ).stream()
                .filter(item -> item.getStatus() != AttemptStatus.IN_PROGRESS)
                .map(AssessmentAttempt::getAssessmentId)
                .collect(java.util.stream.Collectors.toSet());
        return assessments.findAllByOrganisationIdOrderByUpdatedAtDesc(
                        session.organisationId()
                ).stream()
                .filter(item -> item.getStatus() == AssessmentStatus.SCHEDULED)
                .filter(item -> item.getStartAt() != null && item.getEndAt() != null)
                .filter(item -> eligible(item, membership))
                .map(item -> {
                    StudentAssessmentStatus status = StudentAssessmentClassifier.classify(
                            now, item.getStartAt(), item.getEndAt(), completed.contains(item.getId())
                    );
                    long remainingHours = java.time.Duration.between(now, item.getStartAt()).toHours();
                    long remainingDays = status == StudentAssessmentStatus.UPCOMING
                            ? Math.max(1, (remainingHours + 23) / 24)
                            : 0;
                    return new StudentAssessment(
                            item.getId(), item.getTitle(), item.getCode(), item.getType(),
                            item.getDurationMinutes(), item.getQuestionCount(), item.getTotalMarks(),
                            item.getStartAt(), item.getEndAt(), status, remainingDays
                    );
                })
                .sorted(Comparator.comparing(StudentAssessment::startAt))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentAssessmentInstructions instructions(UUID assessmentId) {
        Assessment assessment = findAssessment(assessmentId);
        OrganisationMembership membership = currentMembership();
        if (!eligible(assessment, membership)) {
            throw DomainException.forbidden(
                    "ASSESSMENT_NOT_ELIGIBLE",
                    "You are not in an eligible section for this assessment."
            );
        }
        if (assessment.getStatus() != AssessmentStatus.SCHEDULED
                || assessment.getStartAt() == null
                || assessment.getEndAt() == null) {
            throw DomainException.badRequest(
                    "ASSESSMENT_NOT_SCHEDULED",
                    "This assessment does not have an active delivery schedule."
            );
        }
        AssessmentAttempt inProgress = attempts
                .findFirstByOrganisationIdAndAssessmentIdAndStudentUserIdAndStatus(
                        session.organisationId(),
                        assessmentId,
                        session.userId(),
                        AttemptStatus.IN_PROGRESS
                )
                .orElse(null);
        long attemptsUsed = attempts
                .countByOrganisationIdAndAssessmentIdAndStudentUserId(
                        session.organisationId(), assessmentId, session.userId()
                );
        return new StudentAssessmentInstructions(
                assessment.getId(),
                assessment.getTitle(),
                assessment.getCode(),
                assessment.getType(),
                assessment.getDurationMinutes(),
                assessment.getQuestionCount(),
                assessment.getTotalMarks(),
                assessment.getStartAt(),
                assessment.getEndAt(),
                Instant.now(),
                assessment.getAttemptsAllowed(),
                attemptsUsed,
                assessment.isShuffleQuestions(),
                assessment.isShuffleOptions(),
                assessment.isPartialMarking(),
                inProgress == null ? null : inProgress.getId(),
                inProgress == null ? null : inProgress.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public List<AttemptHistoryItem> history() {
        List<AssessmentAttempt> history = attempts
                .findAllByOrganisationIdAndStudentUserIdOrderByStartedAtDesc(
                        session.organisationId(), session.userId()
                );
        Map<UUID, Assessment> assessmentMap = assessments
                .findAllByOrganisationIdOrderByUpdatedAtDesc(session.organisationId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Assessment::getId, java.util.function.Function.identity()
                ));
        Map<UUID, Long> answeredByAttempt = history.isEmpty()
                ? Map.of()
                : responses.findAllByAttemptIdIn(
                                history.stream().map(AssessmentAttempt::getId).toList()
                        ).stream()
                        .filter(item -> !item.getSelectedOptionIds().isEmpty())
                        .collect(java.util.stream.Collectors.groupingBy(
                                AttemptResponse::getAttemptId,
                                java.util.stream.Collectors.counting()
                        ));
        return history.stream()
                .map(attempt -> {
                    Assessment assessment = assessmentMap.get(attempt.getAssessmentId());
                    if (assessment == null) return null;
                    boolean published = attempt.getResultStatus()
                            == ResultPublicationStatus.PUBLISHED;
                    return new AttemptHistoryItem(
                            attempt.getId(),
                            assessment.getId(),
                            assessment.getTitle(),
                            assessment.getType(),
                            attempt.getStatus(),
                            attempt.getResultStatus(),
                            attempt.getStartedAt(),
                            attempt.getExpiresAt(),
                            attempt.getSubmittedAt(),
                            answeredByAttempt.getOrDefault(attempt.getId(), 0L).intValue(),
                            assessment.getQuestionCount(),
                            published ? attempt.getScore() : null,
                            published ? attempt.getMaxScore() : null,
                            published ? attempt.getPercentage() : null,
                            published ? attempt.getGrade() : null,
                            attempt.getEvaluationVersion()
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public AttemptView start(UUID assessmentId) {
        Assessment assessment = findAssessment(assessmentId);
        OrganisationMembership membership = currentMembership();
        if (!assessment.isOpenAt(Instant.now())) {
            throw DomainException.badRequest(
                    "ASSESSMENT_WINDOW_CLOSED",
                    "This assessment is not currently open."
            );
        }
        if (!eligible(assessment, membership)) {
            throw DomainException.forbidden(
                    "ASSESSMENT_NOT_ELIGIBLE",
                    "You are not in an eligible section for this assessment."
            );
        }
        AssessmentAttempt attempt = attempts
                .findFirstByOrganisationIdAndAssessmentIdAndStudentUserIdAndStatus(
                        session.organisationId(),
                        assessmentId,
                        session.userId(),
                        AttemptStatus.IN_PROGRESS
                )
                .orElseGet(() -> {
                    long previous = attempts
                            .countByOrganisationIdAndAssessmentIdAndStudentUserId(
                                    session.organisationId(),
                                    assessmentId,
                                    session.userId()
                            );
                    if (previous >= assessment.getAttemptsAllowed()) {
                        throw DomainException.badRequest(
                                "ATTEMPT_LIMIT_REACHED",
                                "The permitted number of attempts has been used."
                        );
                    }
                    Instant durationEnd = Instant.now().plus(
                            assessment.getDurationMinutes(),
                            ChronoUnit.MINUTES
                    );
                    Instant expires = durationEnd.isBefore(assessment.getEndAt())
                            ? durationEnd
                            : assessment.getEndAt();
                    return attempts.save(new AssessmentAttempt(
                            session.organisationId(),
                            assessmentId,
                            session.userId(),
                            expires
                    ));
                });
        return view(attempt, assessment);
    }

    @Transactional
    public SavedResponse save(UUID attemptId, SaveResponseRequest request) {
        // Serialize the final response save with server-side expiry processing.
        // If the save wins the row lock it is included in evaluation; if expiry
        // wins, this request observes the submitted state and is rejected.
        AssessmentAttempt attempt = findAttemptForUpdate(attemptId);
        ensureInProgress(attempt);
        Assessment assessment = findAssessment(attempt.getAssessmentId());
        if (!assessment.getQuestionIds().contains(request.questionId())) {
            throw DomainException.badRequest(
                    "QUESTION_NOT_IN_ASSESSMENT",
                    "The response question does not belong to this assessment."
            );
        }
        Question question = questions.findByIdAndOrganisationId(
                        request.questionId(),
                        session.organisationId()
                )
                .orElseThrow();
        Set<UUID> allowedOptions = question.getOptions().stream()
                .map(QuestionOption::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!allowedOptions.containsAll(request.selectedOptionIds())) {
            throw DomainException.badRequest(
                    "INVALID_OPTION",
                    "One or more selected options are invalid."
            );
        }
        AttemptResponse response = responses
                .findByAttemptIdAndQuestionId(attemptId, request.questionId())
                .orElseGet(() -> new AttemptResponse(
                        attemptId,
                        request.questionId(),
                        Set.of(),
                        false,
                        0
                ));
        response.replace(
                request.selectedOptionIds(),
                request.flagged(),
                request.timeSpentSeconds()
        );
        responses.save(response);
        return saved(response);
    }

    @Transactional
    public ResultView submit(UUID attemptId, boolean automatic) {
        AssessmentAttempt attempt = findAttemptForUpdate(attemptId);
        Assessment assessment = findAssessment(attempt.getAssessmentId());
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return result(
                    attempt,
                    assessment,
                    attempt.getResultStatus() == ResultPublicationStatus.PUBLISHED
            );
        }
        List<Question> assessmentQuestions = questions
                .findAllByIdInAndOrganisationId(
                        assessment.getQuestionIds(),
                        session.organisationId()
                );
        List<AttemptResponse> attemptResponses = responses.findAllByAttemptId(attemptId);
        EvaluationEngine.EvaluationOutcome outcome = evaluationEngine.evaluate(
                assessment, assessmentQuestions, attemptResponses
        );
        boolean timedOut = !Instant.now().isBefore(attempt.getExpiresAt());
        boolean automaticSubmission = automatic || timedOut;
        attempt.submit(
                outcome.score(),
                outcome.maxScore(),
                outcome.percentage(),
                settings.resolveGrade(outcome.percentage()),
                outcome.correctAnswers(),
                outcome.wrongAnswers(),
                outcome.unansweredAnswers(),
                automaticSubmission
        );
        audit.record(
                "DEL",
                automaticSubmission ? "AUTO_SUBMIT" : "SUBMIT",
                "AssessmentAttempt",
                attempt.getId(),
                "IN_PROGRESS",
                attempt.getStatus().name()
        );
        audit.record(
                "EVL",
                "AUTO_EVALUATE",
                "AssessmentAttempt",
                attempt.getId(),
                null,
                outcome.score() + "/" + outcome.maxScore()
        );
        notifications.notifyRoles(
                Set.of(UserRole.FACULTY, UserRole.ACADEMIC_HEAD, UserRole.ORG_ADMIN),
                NotificationType.ASSESSMENT_SUBMITTED,
                "Assessment submitted",
                assessment.getTitle() + " has a newly evaluated submission.",
                "/reports/assessments/" + assessment.getId(),
                false
        );
        return result(attempt, assessment, false);
    }

    @Transactional(readOnly = true)
    public ResultView result(UUID attemptId) {
        AssessmentAttempt attempt = findAttempt(attemptId);
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw DomainException.badRequest(
                    "RESULT_NOT_READY",
                    "Submit the assessment before viewing its result."
            );
        }
        return result(
                attempt,
                findAssessment(attempt.getAssessmentId()),
                attempt.getResultStatus() == ResultPublicationStatus.PUBLISHED
        );
    }

    private AttemptView view(AssessmentAttempt attempt, Assessment assessment) {
        Map<UUID, Question> byId = new HashMap<>();
        questions.findAllByIdInAndOrganisationId(
                        assessment.getQuestionIds(),
                        session.organisationId()
                )
                .forEach(question -> byId.put(question.getId(), question));
        List<Question> orderedQuestions = AttemptPresentationOrder.order(
                attempt.getId(),
                "questions",
                assessment.getQuestionIds().stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .toList(),
                Question::getId,
                assessment.isShuffleQuestions()
        );
        List<PlayerQuestion> playerQuestions = orderedQuestions.stream()
                .map(question -> {
                    List<QuestionOption> sourceOptions = question.getOptions().stream()
                            .sorted(Comparator.comparingInt(QuestionOption::getSortOrder))
                            .toList();
                    List<QuestionOption> orderedOptions = AttemptPresentationOrder.order(
                            attempt.getId(),
                            "options:" + question.getId(),
                            sourceOptions,
                            QuestionOption::getId,
                            assessment.isShuffleOptions()
                    );
                    List<PlayerOption> playerOptions = IntStream
                            .range(0, orderedOptions.size())
                            .mapToObj(index -> {
                                QuestionOption option = orderedOptions.get(index);
                                String label = assessment.isShuffleOptions()
                                        ? displayLabel(index)
                                        : option.getLabel();
                                return new PlayerOption(
                                        option.getId(), label, option.getText()
                                );
                            })
                            .toList();
                    return new PlayerQuestion(
                            question.getId(),
                            question.getStem(),
                            question.getType(),
                            question.getMarks(),
                            playerOptions
                    );
                })
                .toList();
        return new AttemptView(
                attempt.getId(),
                assessment.getId(),
                assessment.getTitle(),
                attempt.getStartedAt(),
                attempt.getExpiresAt(),
                playerQuestions,
                responses.findAllByAttemptId(attempt.getId()).stream()
                        .map(this::saved)
                        .toList()
        );
    }

    private ResultView result(
            AssessmentAttempt attempt,
            Assessment assessment,
            boolean reveal
    ) {
        List<AttemptResponse> attemptResponses = responses.findAllByAttemptId(
                attempt.getId()
        );
        int answered = (int) attemptResponses.stream()
                .filter(response -> !response.getSelectedOptionIds().isEmpty())
                .count();
        Map<UUID, AttemptResponse> byQuestion = new HashMap<>();
        attemptResponses.forEach(item -> byQuestion.put(item.getQuestionId(), item));
        Map<UUID, Question> resultQuestionMap = reveal
                ? questions.findAllByIdInAndOrganisationId(
                        assessment.getQuestionIds(), session.organisationId()
                ).stream().collect(java.util.stream.Collectors.toMap(
                        Question::getId,
                        java.util.function.Function.identity()
                ))
                : Map.of();
        List<Question> resultQuestions = reveal
                ? AttemptPresentationOrder.order(
                        attempt.getId(),
                        "questions",
                        assessment.getQuestionIds().stream()
                                .map(resultQuestionMap::get)
                                .filter(java.util.Objects::nonNull)
                                .toList(),
                        Question::getId,
                        assessment.isShuffleQuestions()
                )
                : List.of();
        Map<UUID, String> subjectNames = reveal
                ? subjects.findAllByOrganisationIdOrderByName(session.organisationId())
                        .stream().collect(java.util.stream.Collectors.toMap(
                                item -> item.getId(), item -> item.getName()
                        ))
                : Map.of();
        Map<UUID, String> topicNames = reveal
                ? topics.findAllByOrganisationIdOrderByName(session.organisationId())
                        .stream().collect(java.util.stream.Collectors.toMap(
                                item -> item.getId(), item -> item.getName()
                        ))
                : Map.of();
        List<ResultQuestion> questionResults = resultQuestions.stream()
                .map(question -> {
                    AttemptResponse response = byQuestion.get(question.getId());
                    List<QuestionOption> sourceOptions = question.getOptions().stream()
                            .sorted(Comparator.comparingInt(QuestionOption::getSortOrder))
                            .toList();
                    List<QuestionOption> orderedOptions = AttemptPresentationOrder.order(
                            attempt.getId(),
                            "options:" + question.getId(),
                            sourceOptions,
                            QuestionOption::getId,
                            assessment.isShuffleOptions()
                    );
                    return new ResultQuestion(
                            question.getId(),
                            question.getCode(),
                            question.getStem(),
                            question.getSubjectId(),
                            subjectNames.getOrDefault(question.getSubjectId(), "Unknown subject"),
                            question.getTopicId(),
                            topicNames.getOrDefault(question.getTopicId(), "Unknown topic"),
                            question.getSubTopic(),
                            question.getDifficulty(),
                            question.getBloomLevel(),
                            response == null ? Set.of() : response.getSelectedOptionIds(),
                            question.getOptions().stream()
                                    .filter(QuestionOption::isCorrect)
                                    .map(QuestionOption::getId)
                                    .collect(java.util.stream.Collectors.toSet()),
                            IntStream.range(0, orderedOptions.size())
                                    .mapToObj(index -> {
                                        QuestionOption option = orderedOptions.get(index);
                                        return new ResultOption(
                                                option.getId(),
                                                assessment.isShuffleOptions()
                                                        ? displayLabel(index)
                                                        : option.getLabel(),
                                                option.getText(),
                                                response != null && response
                                                        .getSelectedOptionIds()
                                                        .contains(option.getId()),
                                                option.isCorrect()
                                        );
                                    })
                                    .toList(),
                            response == null || response.getAwardedMarks() == null
                                    ? BigDecimal.ZERO
                                    : response.getAwardedMarks(),
                            question.getMarks(),
                            response != null && Boolean.TRUE.equals(response.getCorrect()),
                            answerStatus(response, question),
                            response == null ? 0 : response.getTimeSpentSeconds(),
                            question.getExplanation()
                    );
                })
                .toList();
        return new ResultView(
                attempt.getId(),
                assessment.getId(),
                assessment.getTitle(),
                attempt.getStatus(),
                attempt.getResultStatus(),
                reveal ? attempt.getScore() : null,
                reveal ? attempt.getMaxScore() : null,
                reveal ? attempt.getPercentage() : null,
                reveal ? attempt.getGrade() : null,
                attempt.getSubmittedAt(),
                answered,
                assessment.getQuestionCount(),
                reveal ? attempt.getCorrectAnswers() : 0,
                reveal ? attempt.getWrongAnswers() : 0,
                reveal ? attempt.getUnansweredAnswers() : 0,
                reveal && settings.rankingEnabled() ? rank(attempt) : null,
                reveal && settings.rankingEnabled() ? topperScore(attempt) : null,
                attempt.getSubmittedAt() == null
                        ? 0
                        : java.time.Duration.between(
                                attempt.getStartedAt(), attempt.getSubmittedAt()
                        ).toSeconds(),
                attempt.getEvaluationVersion(),
                questionResults
        );
    }

    private int rank(AssessmentAttempt attempt) {
        List<AssessmentAttempt> ranked = attempts
                .findAllByOrganisationIdAndAssessmentIdOrderBySubmittedAtAsc(
                        session.organisationId(), attempt.getAssessmentId()
                ).stream()
                .filter(item -> item.getResultStatus() == ResultPublicationStatus.PUBLISHED)
                .filter(item -> item.getPercentage() != null)
                .sorted(Comparator.comparing(
                        AssessmentAttempt::getPercentage,
                        Comparator.reverseOrder()
                ))
                .toList();
        for (int index = 0; index < ranked.size(); index++) {
            if (ranked.get(index).getId().equals(attempt.getId())) return index + 1;
        }
        return ranked.size() + 1;
    }

    private BigDecimal topperScore(AssessmentAttempt attempt) {
        return attempts.findAllByOrganisationIdAndAssessmentIdOrderBySubmittedAtAsc(
                        session.organisationId(), attempt.getAssessmentId()
                ).stream()
                .filter(item -> item.getResultStatus() == ResultPublicationStatus.PUBLISHED)
                .map(AssessmentAttempt::getPercentage)
                .filter(java.util.Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private String answerStatus(AttemptResponse response, Question question) {
        if (response == null || response.getSelectedOptionIds().isEmpty()) return "UNANSWERED";
        if (Boolean.TRUE.equals(response.getCorrect())) return "CORRECT";
        if (response.getAwardedMarks() != null
                && response.getAwardedMarks().compareTo(BigDecimal.ZERO) > 0
                && response.getAwardedMarks().compareTo(question.getMarks()) < 0) {
            return "PARTIALLY_CORRECT";
        }
        return "INCORRECT";
    }

    private SavedResponse saved(AttemptResponse response) {
        return new SavedResponse(
                response.getQuestionId(),
                response.getSelectedOptionIds(),
                response.isFlagged(),
                response.getTimeSpentSeconds()
        );
    }

    private OrganisationMembership currentMembership() {
        return memberships.findByUserIdAndOrganisationIdAndStatus(
                        session.userId(),
                        session.organisationId(),
                        AccountStatus.ACTIVE
                )
                .orElseThrow(() -> DomainException.forbidden(
                        "MEMBERSHIP_INACTIVE",
                        "Your organisation membership is not active."
                ));
    }

    private boolean eligible(
            Assessment assessment,
            OrganisationMembership membership
    ) {
        return assessment.getEligibleSectionIds().isEmpty()
                || (membership.getSectionId() != null
                && assessment.getEligibleSectionIds().contains(membership.getSectionId()));
    }

    private Assessment findAssessment(UUID id) {
        return assessments.findByIdAndOrganisationId(id, session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "ASSESSMENT_NOT_FOUND",
                        "Assessment was not found."
                ));
    }

    private AssessmentAttempt findAttempt(UUID id) {
        return attempts.findByIdAndOrganisationIdAndStudentUserId(
                        id,
                        session.organisationId(),
                        session.userId()
                )
                .orElseThrow(() -> DomainException.notFound(
                        "ATTEMPT_NOT_FOUND",
                        "Assessment attempt was not found."
                ));
    }

    private AssessmentAttempt findAttemptForUpdate(UUID id) {
        return attempts.findStudentAttemptForUpdate(
                        id, session.organisationId(), session.userId()
                )
                .orElseThrow(() -> DomainException.notFound(
                        "ATTEMPT_NOT_FOUND",
                        "Assessment attempt was not found."
                ));
    }

    private String displayLabel(int index) {
        int value = index;
        StringBuilder label = new StringBuilder();
        do {
            label.insert(0, (char) ('A' + value % 26));
            value = value / 26 - 1;
        } while (value >= 0);
        return label.toString();
    }

    private void ensureInProgress(AssessmentAttempt attempt) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw DomainException.badRequest(
                    "ATTEMPT_ALREADY_SUBMITTED",
                    "This attempt has already been submitted."
            );
        }
        if (!Instant.now().isBefore(attempt.getExpiresAt())) {
            throw DomainException.badRequest(
                    "ATTEMPT_EXPIRED",
                    "The assessment time has expired."
            );
        }
    }
}
