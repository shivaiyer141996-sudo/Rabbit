package com.rabbit.aip.attempt;

import static com.rabbit.aip.attempt.AttemptDtos.StudentAssessmentStatus.AVAILABLE_NOW;
import static com.rabbit.aip.attempt.AttemptDtos.StudentAssessmentStatus.COMPLETED;
import static com.rabbit.aip.attempt.AttemptDtos.StudentAssessmentStatus.MISSED_CLOSED;
import static com.rabbit.aip.attempt.AttemptDtos.StudentAssessmentStatus.UPCOMING;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class StudentAssessmentClassifierTest {
    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");

    @Test
    void appliesOneStatusModelForDashboardAndAssessmentCatalogue() {
        assertThat(StudentAssessmentClassifier.classify(
                NOW, NOW.plusSeconds(60), NOW.plusSeconds(120), false
        )).isEqualTo(UPCOMING);
        assertThat(StudentAssessmentClassifier.classify(
                NOW, NOW.minusSeconds(60), NOW.plusSeconds(60), false
        )).isEqualTo(AVAILABLE_NOW);
        assertThat(StudentAssessmentClassifier.classify(
                NOW, NOW.minusSeconds(120), NOW.minusSeconds(60), false
        )).isEqualTo(MISSED_CLOSED);
        assertThat(StudentAssessmentClassifier.classify(
                NOW, NOW.minusSeconds(120), NOW.minusSeconds(60), true
        )).isEqualTo(COMPLETED);
    }
}
