package com.rabbit.aip.attempt;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assessment_attempts")
public class AssessmentAttempt extends TenantEntity {

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "student_user_id", nullable = false)
    private UUID studentUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttemptStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", precision = 10, scale = 2)
    private BigDecimal maxScore;

    @Column(precision = 7, scale = 2)
    private BigDecimal percentage;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @Column(length = 20)
    private String grade;

    @Column(name = "correct_answers", nullable = false)
    private int correctAnswers;

    @Column(name = "wrong_answers", nullable = false)
    private int wrongAnswers;

    @Column(name = "unanswered_answers", nullable = false)
    private int unansweredAnswers;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 30)
    private ResultPublicationStatus resultStatus;

    @Column(name = "result_published_at")
    private Instant resultPublishedAt;

    @Column(name = "result_published_by")
    private UUID resultPublishedBy;

    @Column(name = "evaluation_version", nullable = false)
    private int evaluationVersion;

    protected AssessmentAttempt() {
    }

    public AssessmentAttempt(
            UUID organisationId,
            UUID assessmentId,
            UUID studentUserId,
            Instant expiresAt
    ) {
        super(organisationId);
        this.assessmentId = assessmentId;
        this.studentUserId = studentUserId;
        this.status = AttemptStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
        this.expiresAt = expiresAt;
        this.resultStatus = ResultPublicationStatus.PENDING_PUBLICATION;
        this.evaluationVersion = 1;
    }

    public void submit(
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            String grade,
            int correctAnswers,
            int wrongAnswers,
            int unansweredAnswers,
            boolean automatic
    ) {
        this.score = score;
        this.maxScore = maxScore;
        this.percentage = percentage;
        this.grade = grade;
        this.correctAnswers = correctAnswers;
        this.wrongAnswers = wrongAnswers;
        this.unansweredAnswers = unansweredAnswers;
        this.evaluatedAt = Instant.now();
        this.resultStatus = ResultPublicationStatus.PENDING_PUBLICATION;
        this.submittedAt = Instant.now();
        this.status = automatic
                ? AttemptStatus.AUTO_SUBMITTED
                : AttemptStatus.SUBMITTED;
    }

    public void reEvaluate(
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            String grade,
            int correctAnswers,
            int wrongAnswers,
            int unansweredAnswers
    ) {
        this.score = score;
        this.maxScore = maxScore;
        this.percentage = percentage;
        this.grade = grade;
        this.correctAnswers = correctAnswers;
        this.wrongAnswers = wrongAnswers;
        this.unansweredAnswers = unansweredAnswers;
        this.evaluatedAt = Instant.now();
        this.evaluationVersion += 1;
        this.resultStatus = ResultPublicationStatus.PENDING_PUBLICATION;
        this.resultPublishedAt = null;
        this.resultPublishedBy = null;
    }

    public void publishResult(UUID publisherId) {
        this.resultStatus = ResultPublicationStatus.PUBLISHED;
        this.resultPublishedAt = Instant.now();
        this.resultPublishedBy = publisherId;
    }

    public UUID getAssessmentId() { return assessmentId; }
    public UUID getStudentUserId() { return studentUserId; }
    public AttemptStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public BigDecimal getScore() { return score; }
    public BigDecimal getMaxScore() { return maxScore; }
    public BigDecimal getPercentage() { return percentage; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public String getGrade() { return grade; }
    public int getCorrectAnswers() { return correctAnswers; }
    public int getWrongAnswers() { return wrongAnswers; }
    public int getUnansweredAnswers() { return unansweredAnswers; }
    public ResultPublicationStatus getResultStatus() { return resultStatus; }
    public Instant getResultPublishedAt() { return resultPublishedAt; }
    public UUID getResultPublishedBy() { return resultPublishedBy; }
    public int getEvaluationVersion() { return evaluationVersion; }
}
