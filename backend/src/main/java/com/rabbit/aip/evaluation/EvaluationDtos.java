package com.rabbit.aip.evaluation;

import com.rabbit.aip.attempt.AttemptStatus;
import com.rabbit.aip.attempt.ResultPublicationStatus;
import com.rabbit.aip.question.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    public record ManualReviewOption(
            UUID optionId,
            String label,
            String text,
            boolean selected,
            boolean correct
    ) {
    }

    public record ManualReviewQuestion(
            UUID questionId,
            String code,
            String stem,
            String subjectName,
            String topicName,
            Difficulty difficulty,
            BigDecimal awardedMarks,
            BigDecimal minimumMarks,
            BigDecimal maximumMarks,
            boolean answered,
            boolean correct,
            int timeSpentSeconds,
            List<ManualReviewOption> options,
            String explanation
    ) {
    }

    public record EvaluationAuditEntry(
            UUID eventId,
            Instant timestamp,
            String actorEmail,
            String actorRole,
            String action,
            String beforeValue,
            String afterValue
    ) {
    }

    public record ManualAttemptReview(
            UUID attemptId,
            UUID assessmentId,
            String assessmentTitle,
            UUID studentUserId,
            String studentName,
            AttemptStatus attemptStatus,
            ResultPublicationStatus publicationStatus,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            String grade,
            int evaluationVersion,
            Instant evaluatedAt,
            List<ManualReviewQuestion> questions,
            List<EvaluationAuditEntry> auditTrail
    ) {
    }

    public record ManualScoreAdjustment(
            @NotNull UUID questionId,
            @NotNull BigDecimal awardedMarks
    ) {
    }

    public record ManualScoreUpdateRequest(
            @NotBlank @Size(min = 10, max = 500) String reason,
            @NotEmpty List<@Valid ManualScoreAdjustment> adjustments
    ) {
    }

    public record PublicationResponse(
            UUID assessmentId,
            int publishedCount,
            Instant publishedAt
    ) {
    }

    public record MonitoringRow(
            UUID attemptId,
            UUID studentUserId,
            String studentName,
            AttemptStatus attemptStatus,
            ResultPublicationStatus publicationStatus,
            Instant startedAt,
            Instant expiresAt,
            Instant submittedAt,
            int answered,
            int questionCount,
            long progressPercentage,
            long secondsRemaining
    ) {
    }

    public record AssessmentMonitor(
            UUID assessmentId,
            String assessmentTitle,
            Instant generatedAt,
            long totalAttempts,
            long inProgress,
            long submitted,
            long autoSubmitted,
            List<MonitoringRow> attempts
    ) {
    }
}
