package com.rabbit.aip.question;

import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.question.QuestionDtos.OptionRequest;
import com.rabbit.aip.question.QuestionDtos.QuestionRequest;
import com.rabbit.aip.question.QuestionDtos.QuestionResponse;
import com.rabbit.aip.question.QuestionDtos.ReviewDecision;
import com.rabbit.aip.security.CurrentSession;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {

    private final QuestionRepository questions;
    private final CurrentSession session;
    private final AuditService audit;

    public QuestionService(
            QuestionRepository questions,
            CurrentSession session,
            AuditService audit
    ) {
        this.questions = questions;
        this.session = session;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> list(
            String query,
            QuestionStatus status,
            Difficulty difficulty
    ) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return questions.findAllByOrganisationIdOrderByUpdatedAtDesc(
                        session.organisationId()
                ).stream()
                .filter(item -> status == null || item.getStatus() == status)
                .filter(item -> difficulty == null || item.getDifficulty() == difficulty)
                .filter(item -> normalized.isBlank()
                        || item.getStem().toLowerCase(Locale.ROOT).contains(normalized)
                        || item.getCode().toLowerCase(Locale.ROOT).contains(normalized))
                .map(QuestionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionResponse get(UUID id) {
        return QuestionResponse.from(find(id));
    }

    @Transactional
    public QuestionResponse create(QuestionRequest request) {
        validate(request);
        String code = request.code() == null || request.code().isBlank()
                ? "QB-" + ThreadLocalRandom.current().nextInt(100000, 999999)
                : request.code().trim().toUpperCase(Locale.ROOT);
        if (questions.existsByOrganisationIdAndCodeAndVersion(
                session.organisationId(), code, 1
        )) {
            throw DomainException.badRequest(
                    "QUESTION_CODE_EXISTS",
                    "Question code already exists in this organisation."
            );
        }
        Question question = new Question(
                session.organisationId(),
                code,
                request.stem().trim(),
                request.type(),
                request.subjectId(),
                request.topicId(),
                request.subTopic(),
                request.difficulty(),
                request.bloomLevel(),
                request.marks(),
                request.negativeMarks(),
                request.explanation(),
                language(request.language()),
                session.userId(),
                1,
                null
        );
        question.replaceOptions(toOptions(request.options()));
        Question saved = questions.save(question);
        audit.record("QB", "CREATE", "Question", saved.getId(), null, saved.getCode());
        return QuestionResponse.from(saved);
    }

    @Transactional
    public QuestionResponse update(UUID id, QuestionRequest request) {
        validate(request);
        Question existing = find(id);
        if (existing.getStatus() == QuestionStatus.RETIRED) {
            throw DomainException.badRequest(
                    "QUESTION_RETIRED",
                    "Retired questions cannot be edited."
            );
        }
        if (existing.getStatus() == QuestionStatus.APPROVED
                || existing.getStatus() == QuestionStatus.PUBLISHED) {
            Question version = new Question(
                    session.organisationId(),
                    existing.getCode(),
                    request.stem().trim(),
                    request.type(),
                    request.subjectId(),
                    request.topicId(),
                    request.subTopic(),
                    request.difficulty(),
                    request.bloomLevel(),
                    request.marks(),
                    request.negativeMarks(),
                    request.explanation(),
                    language(request.language()),
                    session.userId(),
                    existing.getVersion() + 1,
                    existing.getId()
            );
            version.replaceOptions(toOptions(request.options()));
            Question saved = questions.save(version);
            audit.record(
                    "QB", "CREATE_VERSION", "Question", saved.getId(),
                    existing.getId().toString(), String.valueOf(saved.getVersion())
            );
            return QuestionResponse.from(saved);
        }
        if (existing.getStatus() != QuestionStatus.DRAFT) {
            throw DomainException.badRequest(
                    "QUESTION_NOT_EDITABLE",
                    "Only draft questions can be edited."
            );
        }
        existing.updateDraft(
                request.stem().trim(),
                request.type(),
                request.subjectId(),
                request.topicId(),
                request.subTopic(),
                request.difficulty(),
                request.bloomLevel(),
                request.marks(),
                request.negativeMarks(),
                request.explanation(),
                language(request.language())
        );
        existing.replaceOptions(toOptions(request.options()));
        audit.record("QB", "UPDATE", "Question", existing.getId(), null, "DRAFT");
        return QuestionResponse.from(existing);
    }

    @Transactional
    public QuestionResponse submit(UUID id) {
        Question question = find(id);
        if (question.getStatus() != QuestionStatus.DRAFT) {
            throw DomainException.badRequest(
                    "QUESTION_NOT_DRAFT",
                    "Only draft questions can be submitted for review."
            );
        }
        question.submitForReview();
        audit.record(
                "QB", "SUBMIT_FOR_REVIEW", "Question", id, "DRAFT", "UNDER_REVIEW"
        );
        return QuestionResponse.from(question);
    }

    @Transactional
    public QuestionResponse review(UUID id, ReviewDecision decision, String reason) {
        Question question = find(id);
        if (question.getStatus() != QuestionStatus.UNDER_REVIEW) {
            throw DomainException.badRequest(
                    "QUESTION_NOT_UNDER_REVIEW",
                    "This question is not waiting for review."
            );
        }
        if (question.getAuthorUserId().equals(session.userId())) {
            throw DomainException.forbidden(
                    "SELF_REVIEW_NOT_ALLOWED",
                    "A question author cannot review their own question."
            );
        }
        if (decision != ReviewDecision.APPROVE
                && (reason == null || reason.trim().length() < 10)) {
            throw DomainException.badRequest(
                    "REVIEW_REASON_REQUIRED",
                    "Return or rejection requires a reason of at least 10 characters."
            );
        }
        if (decision == ReviewDecision.APPROVE) {
            question.approve(session.userId());
        } else {
            question.returnToDraft(session.userId());
        }
        audit.record(
                "QRV",
                decision.name(),
                "Question",
                id,
                "UNDER_REVIEW",
                question.getStatus().name() + (reason == null ? "" : ": " + reason.trim())
        );
        return QuestionResponse.from(question);
    }

    private Question find(UUID id) {
        return questions.findByIdAndOrganisationId(id, session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "QUESTION_NOT_FOUND",
                        "Question was not found."
                ));
    }

    private void validate(QuestionRequest request) {
        long correct = request.options().stream().filter(OptionRequest::correct).count();
        if (request.type() == QuestionType.SINGLE_CORRECT && correct != 1) {
            throw DomainException.badRequest(
                    "SINGLE_CORRECT_ANSWER_INVALID",
                    "Single Correct MCQ requires exactly one correct option."
            );
        }
        if (request.type() == QuestionType.MULTIPLE_CORRECT && correct < 2) {
            throw DomainException.badRequest(
                    "MULTIPLE_CORRECT_ANSWER_INVALID",
                    "Multiple Correct MCQ requires at least two correct options."
            );
        }
        if (request.negativeMarks().compareTo(BigDecimal.ZERO) < 0
                || request.negativeMarks().compareTo(request.marks()) > 0) {
            throw DomainException.badRequest(
                    "NEGATIVE_MARKS_INVALID",
                    "Negative marks must be between zero and the question marks."
            );
        }
    }

    private List<QuestionOption> toOptions(List<OptionRequest> requests) {
        return java.util.stream.IntStream.range(0, requests.size())
                .mapToObj(index -> {
                    OptionRequest request = requests.get(index);
                    return new QuestionOption(
                            String.valueOf((char) ('A' + index)),
                            request.text().trim(),
                            request.correct(),
                            index
                    );
                })
                .toList();
    }

    private String language(String value) {
        return value == null || value.isBlank() ? "en" : value.trim();
    }
}
