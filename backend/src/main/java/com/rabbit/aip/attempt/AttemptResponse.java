package com.rabbit.aip.attempt;

import com.rabbit.aip.common.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "attempt_responses")
public class AttemptResponse extends BaseEntity {

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "time_spent_seconds", nullable = false)
    private int timeSpentSeconds;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "response_selected_options",
            joinColumns = @JoinColumn(name = "response_id")
    )
    @Column(name = "option_id", nullable = false)
    private Set<UUID> selectedOptionIds = new HashSet<>();

    protected AttemptResponse() {
    }

    public AttemptResponse(
            UUID attemptId,
            UUID questionId,
            Set<UUID> selectedOptionIds,
            boolean flagged,
            int timeSpentSeconds
    ) {
        this.attemptId = attemptId;
        this.questionId = questionId;
        replace(selectedOptionIds, flagged, timeSpentSeconds);
    }

    public void replace(
            Set<UUID> selectedOptionIds,
            boolean flagged,
            int timeSpentSeconds
    ) {
        this.selectedOptionIds.clear();
        this.selectedOptionIds.addAll(selectedOptionIds);
        this.flagged = flagged;
        this.timeSpentSeconds = Math.max(0, timeSpentSeconds);
    }

    public UUID getAttemptId() { return attemptId; }
    public UUID getQuestionId() { return questionId; }
    public boolean isFlagged() { return flagged; }
    public int getTimeSpentSeconds() { return timeSpentSeconds; }
    public Set<UUID> getSelectedOptionIds() { return Set.copyOf(selectedOptionIds); }
}
