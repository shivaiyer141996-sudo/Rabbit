package com.rabbit.aip.attempt;

import com.rabbit.aip.assessment.AssessmentType;
import com.rabbit.aip.question.QuestionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AttemptDtos {

    private AttemptDtos() {
    }

    public record StudentAssessment(
            UUID id,
            String title,
            String code,
            AssessmentType type,
            int durationMinutes,
            int questionCount,
            BigDecimal totalMarks,
            Instant startAt,
            Instant endAt
    ) {
    }

    public record PlayerOption(UUID id, String label, String text) {
    }

    public record PlayerQuestion(
            UUID id,
            String stem,
            QuestionType type,
            BigDecimal marks,
            List<PlayerOption> options
    ) {
    }

    public record SavedResponse(
            UUID questionId,
            Set<UUID> selectedOptionIds,
            boolean flagged,
            int timeSpentSeconds
    ) {
    }

    public record AttemptView(
            UUID attemptId,
            UUID assessmentId,
            String title,
            Instant startedAt,
            Instant expiresAt,
            List<PlayerQuestion> questions,
            List<SavedResponse> responses
    ) {
    }

    public record SaveResponseRequest(
            @NotNull UUID questionId,
            @NotNull Set<UUID> selectedOptionIds,
            boolean flagged,
            @Min(0) int timeSpentSeconds
    ) {
    }

    public record ResultView(
            UUID attemptId,
            UUID assessmentId,
            String assessmentTitle,
            AttemptStatus status,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            Instant submittedAt,
            int answered,
            int questionCount
    ) {
    }
}
