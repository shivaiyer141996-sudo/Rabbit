package com.rabbit.aip.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuestionDtos {

    private QuestionDtos() {
    }

    public record OptionRequest(
            @NotBlank String text,
            boolean correct
    ) {
    }

    public record QuestionRequest(
            String code,
            @NotBlank String stem,
            @NotNull QuestionType type,
            @NotNull UUID subjectId,
            @NotNull UUID topicId,
            String subTopic,
            @NotNull Difficulty difficulty,
            @NotNull BloomLevel bloomLevel,
            @NotNull @DecimalMin("0.01") BigDecimal marks,
            @NotNull @DecimalMin("0.0") BigDecimal negativeMarks,
            String explanation,
            String language,
            @NotEmpty @Size(min = 4, max = 6) List<@Valid OptionRequest> options
    ) {
    }

    public record OptionResponse(
            UUID id,
            String label,
            String text,
            boolean correct,
            int sortOrder
    ) {
    }

    public record QuestionResponse(
            UUID id,
            String code,
            String stem,
            QuestionType type,
            UUID subjectId,
            UUID topicId,
            String subTopic,
            Difficulty difficulty,
            BloomLevel bloomLevel,
            BigDecimal marks,
            BigDecimal negativeMarks,
            QuestionStatus status,
            int version,
            String explanation,
            String language,
            UUID authorUserId,
            UUID reviewedBy,
            UUID approvedBy,
            List<OptionResponse> options,
            Instant createdAt,
            Instant updatedAt
    ) {
        static QuestionResponse from(Question question) {
            return new QuestionResponse(
                    question.getId(),
                    question.getCode(),
                    question.getStem(),
                    question.getType(),
                    question.getSubjectId(),
                    question.getTopicId(),
                    question.getSubTopic(),
                    question.getDifficulty(),
                    question.getBloomLevel(),
                    question.getMarks(),
                    question.getNegativeMarks(),
                    question.getStatus(),
                    question.getVersion(),
                    question.getExplanation(),
                    question.getLanguage(),
                    question.getAuthorUserId(),
                    question.getReviewedBy(),
                    question.getApprovedBy(),
                    question.getOptions().stream().map(option -> new OptionResponse(
                            option.getId(),
                            option.getLabel(),
                            option.getText(),
                            option.isCorrect(),
                            option.getSortOrder()
                    )).toList(),
                    question.getCreatedAt(),
                    question.getUpdatedAt()
            );
        }
    }

    public enum ReviewDecision {
        APPROVE,
        RETURN,
        REJECT
    }

    public record ReviewRequest(
            @NotNull ReviewDecision decision,
            String reason
    ) {
    }
}
