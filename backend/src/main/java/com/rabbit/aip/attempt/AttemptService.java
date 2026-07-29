package com.rabbit.aip.attempt;

import com.rabbit.aip.assessment.Assessment;
import com.rabbit.aip.assessment.AssessmentRepository;
import com.rabbit.aip.assessment.AssessmentStatus;
import com.rabbit.aip.attempt.AttemptDtos.AttemptView;
import com.rabbit.aip.attempt.AttemptDtos.PlayerOption;
import com.rabbit.aip.attempt.AttemptDtos.PlayerQuestion;
import com.rabbit.aip.attempt.AttemptDtos.ResultView;
import com.rabbit.aip.attempt.AttemptDtos.SaveResponseRequest;
import com.rabbit.aip.attempt.AttemptDtos.SavedResponse;
import com.rabbit.aip.attempt.AttemptDtos.StudentAssessment;
import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.question.Question;
import com.rabbit.aip.question.QuestionOption;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.user.AccountStatus;
import com.rabbit.aip.user.OrganisationMembership;
import com.rabbit.aip.user.OrganisationMembershipRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final ScoringService scoring;
    private final AuditService audit;

    public AttemptService(
            AssessmentRepository assessments,
            AssessmentAttemptRepository attempts,
            AttemptResponseRepository responses,
            QuestionRepository questions,
            OrganisationMembershipRepository memberships,
            CurrentSession session,
            ScoringService scoring,
            AuditService audit
    ) {
        this.assessments = assessments;
        this.attempts = attempts;
        this.responses = responses;
        this.questions = questions;
        this.memberships = memberships;
        this.session = session;
        this.scoring = scoring;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<StudentAssessment> available() {
        Instant now = Instant.now();
        OrganisationMembership membership = currentMembership();
        return assessments
                .findAllByOrganisationIdAndStatusAndStartAtLessThanEqualAndEndAtGreaterThan(
                        session.organisationId(),
                        AssessmentStatus.SCHEDULED,
                        now,
                        now
                ).stream()
                .filter(item -> eligible(item, membership))
                .map(item -> new StudentAssessment(
                        item.getId(),
                        item.getTitle(),
                        item.getCode(),
                        item.getType(),
                        item.getDurationMinutes(),
                        item.getQuestionCount(),
                        item.getTotalMarks(),
                        item.getStartAt(),
                        item.getEndAt()
                ))
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
        AssessmentAttempt attempt = findAttempt(attemptId);
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
        AssessmentAttempt attempt = findAttempt(attemptId);
        Assessment assessment = findAssessment(attempt.getAssessmentId());
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return result(attempt, assessment);
        }
        List<Question> assessmentQuestions = questions
                .findAllByIdInAndOrganisationId(
                        assessment.getQuestionIds(),
                        session.organisationId()
                );
        Map<UUID, AttemptResponse> responseMap = new HashMap<>();
        responses.findAllByAttemptId(attemptId)
                .forEach(response -> responseMap.put(response.getQuestionId(), response));

        List<BigDecimal> itemScores = new ArrayList<>();
        for (Question question : assessmentQuestions) {
            Set<UUID> correct = question.getOptions().stream()
                    .filter(QuestionOption::isCorrect)
                    .map(QuestionOption::getId)
                    .collect(java.util.stream.Collectors.toSet());
            Set<UUID> selected = responseMap.containsKey(question.getId())
                    ? responseMap.get(question.getId()).getSelectedOptionIds()
                    : Set.of();
            itemScores.add(scoring.scoreQuestion(
                    question.getType(),
                    question.getMarks(),
                    question.getNegativeMarks(),
                    correct,
                    selected,
                    assessment.isPartialMarking()
            ));
        }
        BigDecimal score = scoring.floorTotal(itemScores);
        BigDecimal percentage = assessment.getTotalMarks().signum() == 0
                ? BigDecimal.ZERO
                : score.multiply(BigDecimal.valueOf(100))
                        .divide(assessment.getTotalMarks(), 2, RoundingMode.HALF_UP);
        attempt.submit(
                score,
                assessment.getTotalMarks(),
                percentage,
                automatic
        );
        audit.record(
                "DEL",
                automatic ? "AUTO_SUBMIT" : "SUBMIT",
                "AssessmentAttempt",
                attempt.getId(),
                "IN_PROGRESS",
                attempt.getStatus().name()
        );
        return result(attempt, assessment);
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
        return result(attempt, findAssessment(attempt.getAssessmentId()));
    }

    private AttemptView view(AssessmentAttempt attempt, Assessment assessment) {
        Map<UUID, Question> byId = new HashMap<>();
        questions.findAllByIdInAndOrganisationId(
                        assessment.getQuestionIds(),
                        session.organisationId()
                )
                .forEach(question -> byId.put(question.getId(), question));
        List<PlayerQuestion> playerQuestions = assessment.getQuestionIds().stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(question -> new PlayerQuestion(
                        question.getId(),
                        question.getStem(),
                        question.getType(),
                        question.getMarks(),
                        question.getOptions().stream()
                                .sorted(Comparator.comparingInt(QuestionOption::getSortOrder))
                                .map(option -> new PlayerOption(
                                        option.getId(),
                                        option.getLabel(),
                                        option.getText()
                                )).toList()
                )).toList();
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

    private ResultView result(AssessmentAttempt attempt, Assessment assessment) {
        int answered = (int) responses.findAllByAttemptId(attempt.getId()).stream()
                .filter(response -> !response.getSelectedOptionIds().isEmpty())
                .count();
        return new ResultView(
                attempt.getId(),
                assessment.getId(),
                assessment.getTitle(),
                attempt.getStatus(),
                attempt.getScore(),
                attempt.getMaxScore(),
                attempt.getPercentage(),
                attempt.getSubmittedAt(),
                answered,
                assessment.getQuestionCount()
        );
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
