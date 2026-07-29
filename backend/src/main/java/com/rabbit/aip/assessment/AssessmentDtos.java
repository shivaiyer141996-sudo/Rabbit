package com.rabbit.aip.assessment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AssessmentDtos {

    private AssessmentDtos() {
    }

    public record AssessmentRequest(
            String code,
            @NotBlank String title,
            @NotNull AssessmentType type,
            @NotNull UUID subjectId,
            @Min(1) int durationMinutes,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            boolean partialMarking,
            @Min(1) int attemptsAllowed,
            @NotEmpty List<UUID> questionIds
    ) {
    }

    public record ScheduleRequest(
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            @NotEmpty Set<UUID> eligibleSectionIds
    ) {
    }

    public enum AssessmentReviewDecision {
        APPROVE,
        RETURN,
        REJECT
    }

    public record AssessmentReviewRequest(
            @NotNull AssessmentReviewDecision decision,
            String reason
    ) {
    }

    public record AssessmentReviewResponse(
            UUID id,
            UUID reviewerUserId,
            AssessmentReviewDecision decision,
            String reason,
            Instant createdAt
    ) {
        static AssessmentReviewResponse from(AssessmentReview review) {
            return new AssessmentReviewResponse(
                    review.getId(),
                    review.getReviewerUserId(),
                    review.getDecision(),
                    review.getReason(),
                    review.getCreatedAt()
            );
        }
    }

    public record AssessmentResponse(
            UUID id,
            String title,
            String code,
            AssessmentType type,
            UUID subjectId,
            int durationMinutes,
            AssessmentStatus status,
            BigDecimal totalMarks,
            int questionCount,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            boolean partialMarking,
            int attemptsAllowed,
            Instant startAt,
            Instant endAt,
            List<UUID> questionIds,
            Set<UUID> eligibleSectionIds,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static AssessmentResponse from(Assessment assessment) {
            return new AssessmentResponse(
                    assessment.getId(),
                    assessment.getTitle(),
                    assessment.getCode(),
                    assessment.getType(),
                    assessment.getSubjectId(),
                    assessment.getDurationMinutes(),
                    assessment.getStatus(),
                    assessment.getTotalMarks(),
                    assessment.getQuestionCount(),
                    assessment.isShuffleQuestions(),
                    assessment.isShuffleOptions(),
                    assessment.isPartialMarking(),
                    assessment.getAttemptsAllowed(),
                    assessment.getStartAt(),
                    assessment.getEndAt(),
                    assessment.getQuestionIds(),
                    assessment.getEligibleSectionIds(),
                    assessment.getCreatedAt(),
                    assessment.getUpdatedAt()
            );
        }
    }
}
