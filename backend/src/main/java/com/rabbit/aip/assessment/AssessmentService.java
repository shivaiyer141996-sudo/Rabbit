package com.rabbit.aip.assessment;

import com.rabbit.aip.assessment.AssessmentDtos.AssessmentRequest;
import com.rabbit.aip.assessment.AssessmentDtos.AssessmentResponse;
import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.question.Question;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.question.QuestionStatus;
import com.rabbit.aip.security.CurrentSession;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentService {

    private final AssessmentRepository assessments;
    private final QuestionRepository questions;
    private final CurrentSession session;
    private final AuditService audit;

    public AssessmentService(
            AssessmentRepository assessments,
            QuestionRepository questions,
            CurrentSession session,
            AuditService audit
    ) {
        this.assessments = assessments;
        this.questions = questions;
        this.session = session;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> list() {
        return assessments.findAllByOrganisationIdOrderByUpdatedAtDesc(
                        session.organisationId()
                ).stream()
                .map(AssessmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssessmentResponse get(UUID id) {
        return AssessmentResponse.from(find(id));
    }

    @Transactional
    public AssessmentResponse create(AssessmentRequest request) {
        Set<UUID> unique = new HashSet<>(request.questionIds());
        if (unique.size() != request.questionIds().size()) {
            throw DomainException.badRequest(
                    "DUPLICATE_ASSESSMENT_QUESTION",
                    "An assessment cannot contain the same question more than once."
            );
        }
        List<Question> selected = questions.findAllByIdInAndOrganisationId(
                request.questionIds(),
                session.organisationId()
        );
        if (selected.size() != request.questionIds().size()) {
            throw DomainException.badRequest(
                    "ASSESSMENT_QUESTION_NOT_FOUND",
                    "One or more selected questions were not found."
            );
        }
        if (selected.stream().anyMatch(question ->
                question.getStatus() != QuestionStatus.APPROVED
                        && question.getStatus() != QuestionStatus.PUBLISHED
        )) {
            throw DomainException.badRequest(
                    "UNAPPROVED_QUESTION",
                    "Only approved questions can be added to an assessment."
            );
        }
        BigDecimal total = selected.stream()
                .map(Question::getMarks)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String code = request.code() == null || request.code().isBlank()
                ? "ASM-" + ThreadLocalRandom.current().nextInt(100000, 999999)
                : request.code().trim().toUpperCase(Locale.ROOT);
        Assessment saved = assessments.save(new Assessment(
                session.organisationId(),
                request.title().trim(),
                code,
                request.type(),
                request.subjectId(),
                request.durationMinutes(),
                request.shuffleQuestions(),
                request.shuffleOptions(),
                request.partialMarking(),
                request.attemptsAllowed(),
                session.userId(),
                request.questionIds(),
                total
        ));
        audit.record("ASM", "CREATE", "Assessment", saved.getId(), null, saved.getCode());
        return AssessmentResponse.from(saved);
    }

    @Transactional
    public AssessmentResponse publish(UUID id) {
        Assessment assessment = find(id);
        if (assessment.getStatus() != AssessmentStatus.DRAFT) {
            throw DomainException.badRequest(
                    "ASSESSMENT_NOT_DRAFT",
                    "Only a draft assessment can be published."
            );
        }
        if (assessment.getQuestionCount() < 1) {
            throw DomainException.badRequest(
                    "ASSESSMENT_EMPTY",
                    "Assessment must contain at least one approved question."
            );
        }
        assessment.publish();
        audit.record(
                "ASM", "PUBLISH", "Assessment", id, "DRAFT", "PUBLISHED"
        );
        return AssessmentResponse.from(assessment);
    }

    @Transactional
    public AssessmentResponse schedule(
            UUID id,
            Instant startAt,
            Instant endAt,
            Set<UUID> eligibleSectionIds
    ) {
        Assessment assessment = find(id);
        if (assessment.getStatus() != AssessmentStatus.PUBLISHED
                && assessment.getStatus() != AssessmentStatus.SCHEDULED) {
            throw DomainException.badRequest(
                    "ASSESSMENT_NOT_PUBLISHED",
                    "Publish the assessment before scheduling it."
            );
        }
        if (!startAt.isBefore(endAt)) {
            throw DomainException.badRequest(
                    "SCHEDULE_WINDOW_INVALID",
                    "Assessment end time must be after its start time."
            );
        }
        assessment.schedule(startAt, endAt, eligibleSectionIds);
        audit.record(
                "DEL", "SCHEDULE", "Assessment", id,
                assessment.getStartAt() == null ? null : assessment.getStartAt().toString(),
                startAt + " to " + endAt
        );
        return AssessmentResponse.from(assessment);
    }

    Assessment find(UUID id) {
        return assessments.findByIdAndOrganisationId(id, session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "ASSESSMENT_NOT_FOUND",
                        "Assessment was not found."
                ));
    }
}
