package com.rabbit.aip.question;

import com.rabbit.aip.common.domain.TenantEntity;
import com.rabbit.aip.question.QuestionDtos.ReviewDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "question_reviews")
public class QuestionReview extends TenantEntity {

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "reviewer_user_id", nullable = false)
    private UUID reviewerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewDecision decision;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "checklist_items", nullable = false, columnDefinition = "text")
    private String checklistItems;

    protected QuestionReview() {
    }

    public QuestionReview(
            UUID organisationId,
            UUID questionId,
            UUID reviewerUserId,
            ReviewDecision decision,
            String reason,
            Set<ReviewChecklistItem> checklistItems
    ) {
        super(organisationId);
        this.questionId = questionId;
        this.reviewerUserId = reviewerUserId;
        this.decision = decision;
        this.reason = reason;
        this.checklistItems = checklistItems.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    public Set<ReviewChecklistItem> checklist() {
        if (checklistItems == null || checklistItems.isBlank()) return Set.of();
        return Arrays.stream(checklistItems.split(","))
                .map(ReviewChecklistItem::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    public UUID getQuestionId() { return questionId; }
    public UUID getReviewerUserId() { return reviewerUserId; }
    public ReviewDecision getDecision() { return decision; }
    public String getReason() { return reason; }
}
