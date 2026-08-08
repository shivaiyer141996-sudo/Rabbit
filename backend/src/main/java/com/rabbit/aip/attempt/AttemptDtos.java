package com.rabbit.aip.attempt;

import com.rabbit.aip.assessment.AssessmentType;
import com.rabbit.aip.question.QuestionType;
import com.rabbit.aip.question.Difficulty;
import com.rabbit.aip.question.BloomLevel;
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

    public enum StudentAssessmentStatus {
        AVAILABLE_NOW,
        UPCOMING,
        COMPLETED,
        MISSED_CLOSED
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
            Instant endAt,
            StudentAssessmentStatus status,
            long remainingDays
    ) {
    }

    public record StudentAssessmentInstructions(
            UUID id,
            String title,
            String code,
            AssessmentType type,
            int durationMinutes,
            int questionCount,
            BigDecimal totalMarks,
            Instant startAt,
            Instant endAt,
            Instant serverNow,
            int attemptsAllowed,
            long attemptsUsed,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            boolean partialMarking,
            UUID inProgressAttemptId,
            Instant inProgressExpiresAt
    ) {
    }

    public record AttemptHistoryItem(
            UUID attemptId,
            UUID assessmentId,
            String assessmentTitle,
            AssessmentType assessmentType,
            AttemptStatus status,
            ResultPublicationStatus publicationStatus,
            Instant startedAt,
            Instant expiresAt,
            Instant submittedAt,
            int answered,
            int questionCount,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            String grade,
            int evaluationVersion
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
            ResultPublicationStatus publicationStatus,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            String grade,
            Instant submittedAt,
            int answered,
            int questionCount,
            int correctAnswers,
            int wrongAnswers,
            int unansweredAnswers,
            Integer rank,
            BigDecimal topperScore,
            long timeTakenSeconds,
            int evaluationVersion,
            List<ResultQuestion> questions
    ) {
    }

    public record ResultQuestion(
            UUID questionId,
            String questionCode,
            String stem,
            UUID subjectId,
            String subjectName,
            UUID topicId,
            String topicName,
            String chapter,
            Difficulty difficulty,
            BloomLevel bloomLevel,
            Set<UUID> selectedOptionIds,
            Set<UUID> correctOptionIds,
            List<ResultOption> options,
            BigDecimal awardedMarks,
            BigDecimal maxMarks,
            boolean correct,
            String answerStatus,
            int timeSpentSeconds,
            String explanation
    ) {
    }

    public record ResultOption(
            UUID optionId,
            String label,
            String text,
            boolean selected,
            boolean correct
    ) {
    }
}
