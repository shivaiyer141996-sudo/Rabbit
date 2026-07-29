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
    }

    public void submit(
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            boolean automatic
    ) {
        this.score = score;
        this.maxScore = maxScore;
        this.percentage = percentage;
        this.submittedAt = Instant.now();
        this.status = automatic
                ? AttemptStatus.AUTO_SUBMITTED
                : AttemptStatus.SUBMITTED;
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
}
