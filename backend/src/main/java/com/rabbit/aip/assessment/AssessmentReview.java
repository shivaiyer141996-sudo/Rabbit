package com.rabbit.aip.assessment;

import com.rabbit.aip.assessment.AssessmentDtos.AssessmentReviewDecision;
import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "assessment_reviews")
public class AssessmentReview extends TenantEntity {

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "reviewer_user_id", nullable = false)
    private UUID reviewerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssessmentReviewDecision decision;

    @Column(columnDefinition = "text")
    private String reason;

    protected AssessmentReview() {
    }

    public AssessmentReview(
            UUID organisationId,
            UUID assessmentId,
            UUID reviewerUserId,
            AssessmentReviewDecision decision,
            String reason
    ) {
        super(organisationId);
        this.assessmentId = assessmentId;
        this.reviewerUserId = reviewerUserId;
        this.decision = decision;
        this.reason = reason;
    }

    public UUID getAssessmentId() { return assessmentId; }
    public UUID getReviewerUserId() { return reviewerUserId; }
    public AssessmentReviewDecision getDecision() { return decision; }
    public String getReason() { return reason; }
}
