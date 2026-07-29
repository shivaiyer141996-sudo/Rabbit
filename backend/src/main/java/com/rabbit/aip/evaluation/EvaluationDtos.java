package com.rabbit.aip.evaluation;

import com.rabbit.aip.attempt.AttemptStatus;
import com.rabbit.aip.attempt.ResultPublicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EvaluationDtos {

    private EvaluationDtos() {
    }

    public record EvaluationRow(
            UUID attemptId,
            UUID studentUserId,
            String studentName,
            AttemptStatus attemptStatus,
            ResultPublicationStatus publicationStatus,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            String grade,
            int correctAnswers,
            int wrongAnswers,
            int unansweredAnswers,
            int evaluationVersion,
            Instant evaluatedAt,
            Instant publishedAt
    ) {
    }

    public record AssessmentEvaluationSummary(
            UUID assessmentId,
            String assessmentTitle,
            long evaluatedCount,
            long pendingPublicationCount,
            long publishedCount,
            BigDecimal averagePercentage,
            List<EvaluationRow> results
    ) {
    }

    public record ReEvaluationRequest(
            @NotBlank @Size(min = 10, max = 500) String reason
    ) {
    }

    public record PublicationResponse(
            UUID assessmentId,
            int publishedCount,
            Instant publishedAt
    ) {
    }
}
