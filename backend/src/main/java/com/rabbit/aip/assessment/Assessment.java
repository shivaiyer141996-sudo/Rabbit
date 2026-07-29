package com.rabbit.aip.assessment;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "assessments")
public class Assessment extends TenantEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AssessmentType type;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssessmentStatus status;

    @Column(name = "total_marks", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalMarks;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions;

    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions;

    @Column(name = "partial_marking", nullable = false)
    private boolean partialMarking;

    @Column(name = "attempts_allowed", nullable = false)
    private int attemptsAllowed;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "assessment_question_ids",
            joinColumns = @JoinColumn(name = "assessment_id")
    )
    @Column(name = "question_id", nullable = false)
    @OrderColumn(name = "display_order")
    private List<UUID> questionIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "assessment_eligible_sections",
            joinColumns = @JoinColumn(name = "assessment_id")
    )
    @Column(name = "section_id", nullable = false)
    private Set<UUID> eligibleSectionIds = new HashSet<>();

    protected Assessment() {
    }

    public Assessment(
            UUID organisationId,
            String title,
            String code,
            AssessmentType type,
            UUID subjectId,
            int durationMinutes,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            boolean partialMarking,
            int attemptsAllowed,
            UUID createdBy,
            List<UUID> questionIds,
            BigDecimal totalMarks
    ) {
        super(organisationId);
        this.title = title;
        this.code = code;
        this.type = type;
        this.subjectId = subjectId;
        this.durationMinutes = durationMinutes;
        this.status = AssessmentStatus.DRAFT;
        this.shuffleQuestions = shuffleQuestions;
        this.shuffleOptions = shuffleOptions;
        this.partialMarking = partialMarking;
        this.attemptsAllowed = attemptsAllowed;
        this.createdBy = createdBy;
        this.questionIds.addAll(questionIds);
        this.questionCount = questionIds.size();
        this.totalMarks = totalMarks;
    }

    public void publish() {
        status = AssessmentStatus.PUBLISHED;
        publishedAt = Instant.now();
    }

    public void submitForReview() {
        status = AssessmentStatus.READY_FOR_REVIEW;
    }

    public void approve() {
        status = AssessmentStatus.APPROVED;
    }

    public void returnToDraft() {
        status = AssessmentStatus.DRAFT;
    }

    public void schedule(Instant startAt, Instant endAt, Set<UUID> sectionIds) {
        this.startAt = startAt;
        this.endAt = endAt;
        this.eligibleSectionIds.clear();
        this.eligibleSectionIds.addAll(sectionIds);
        this.status = AssessmentStatus.SCHEDULED;
    }

    public boolean isOpenAt(Instant instant) {
        return status == AssessmentStatus.SCHEDULED
                && startAt != null
                && endAt != null
                && !instant.isBefore(startAt)
                && instant.isBefore(endAt);
    }

    public String getTitle() { return title; }
    public String getCode() { return code; }
    public AssessmentType getType() { return type; }
    public UUID getSubjectId() { return subjectId; }
    public int getDurationMinutes() { return durationMinutes; }
    public AssessmentStatus getStatus() { return status; }
    public BigDecimal getTotalMarks() { return totalMarks; }
    public int getQuestionCount() { return questionCount; }
    public boolean isShuffleQuestions() { return shuffleQuestions; }
    public boolean isShuffleOptions() { return shuffleOptions; }
    public boolean isPartialMarking() { return partialMarking; }
    public int getAttemptsAllowed() { return attemptsAllowed; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public List<UUID> getQuestionIds() { return List.copyOf(questionIds); }
    public Set<UUID> getEligibleSectionIds() { return Set.copyOf(eligibleSectionIds); }
}
