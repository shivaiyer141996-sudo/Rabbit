package com.rabbit.aip.question;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "questions")
public class Question extends TenantEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, columnDefinition = "text")
    private String stem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionType type;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "sub_topic", length = 200)
    private String subTopic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "bloom_level", nullable = false, length = 30)
    private BloomLevel bloomLevel;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal marks;

    @Column(name = "negative_marks", nullable = false, precision = 10, scale = 2)
    private BigDecimal negativeMarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionStatus status;

    @Column(nullable = false)
    private int version;

    @Column(columnDefinition = "text")
    private String explanation;

    @Column(nullable = false, length = 20)
    private String language;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "parent_question_id")
    private UUID parentQuestionId;

    @OneToMany(
            mappedBy = "question",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("sortOrder ASC")
    private List<QuestionOption> options = new ArrayList<>();

    protected Question() {
    }

    public Question(
            UUID organisationId,
            String code,
            String stem,
            QuestionType type,
            UUID subjectId,
            UUID topicId,
            String subTopic,
            Difficulty difficulty,
            BloomLevel bloomLevel,
            BigDecimal marks,
            BigDecimal negativeMarks,
            String explanation,
            String language,
            UUID authorUserId,
            int version,
            UUID parentQuestionId
    ) {
        super(organisationId);
        this.code = code;
        this.stem = stem;
        this.type = type;
        this.subjectId = subjectId;
        this.topicId = topicId;
        this.subTopic = subTopic;
        this.difficulty = difficulty;
        this.bloomLevel = bloomLevel;
        this.marks = marks;
        this.negativeMarks = negativeMarks;
        this.status = QuestionStatus.DRAFT;
        this.explanation = explanation;
        this.language = language;
        this.authorUserId = authorUserId;
        this.version = version;
        this.parentQuestionId = parentQuestionId;
    }

    public void replaceOptions(List<QuestionOption> replacement) {
        options.clear();
        replacement.forEach(option -> {
            option.attachTo(this);
            options.add(option);
        });
    }

    public void updateDraft(
            String stem,
            QuestionType type,
            UUID subjectId,
            UUID topicId,
            String subTopic,
            Difficulty difficulty,
            BloomLevel bloomLevel,
            BigDecimal marks,
            BigDecimal negativeMarks,
            String explanation,
            String language
    ) {
        this.stem = stem;
        this.type = type;
        this.subjectId = subjectId;
        this.topicId = topicId;
        this.subTopic = subTopic;
        this.difficulty = difficulty;
        this.bloomLevel = bloomLevel;
        this.marks = marks;
        this.negativeMarks = negativeMarks;
        this.explanation = explanation;
        this.language = language;
    }

    public void submitForReview() {
        status = QuestionStatus.UNDER_REVIEW;
    }

    public void approve(UUID reviewerId) {
        status = QuestionStatus.APPROVED;
        reviewedBy = reviewerId;
        approvedBy = reviewerId;
    }

    public void returnToDraft(UUID reviewerId) {
        status = QuestionStatus.DRAFT;
        reviewedBy = reviewerId;
    }

    public String getCode() { return code; }
    public String getStem() { return stem; }
    public QuestionType getType() { return type; }
    public UUID getSubjectId() { return subjectId; }
    public UUID getTopicId() { return topicId; }
    public String getSubTopic() { return subTopic; }
    public Difficulty getDifficulty() { return difficulty; }
    public BloomLevel getBloomLevel() { return bloomLevel; }
    public BigDecimal getMarks() { return marks; }
    public BigDecimal getNegativeMarks() { return negativeMarks; }
    public QuestionStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public String getExplanation() { return explanation; }
    public String getLanguage() { return language; }
    public UUID getAuthorUserId() { return authorUserId; }
    public UUID getReviewedBy() { return reviewedBy; }
    public UUID getApprovedBy() { return approvedBy; }
    public UUID getParentQuestionId() { return parentQuestionId; }
    public List<QuestionOption> getOptions() { return List.copyOf(options); }
}
