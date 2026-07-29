package com.rabbit.aip.question;

import com.rabbit.aip.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_options")
public class QuestionOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, length = 3)
    private String label;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected QuestionOption() {
    }

    public QuestionOption(String label, String text, boolean correct, int sortOrder) {
        this.label = label;
        this.text = text;
        this.correct = correct;
        this.sortOrder = sortOrder;
    }

    void attachTo(Question question) {
        this.question = question;
    }

    public String getLabel() { return label; }
    public String getText() { return text; }
    public boolean isCorrect() { return correct; }
    public int getSortOrder() { return sortOrder; }
}
