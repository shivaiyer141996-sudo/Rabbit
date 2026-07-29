package com.rabbit.aip.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbit.aip.question.QuestionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScoringServiceTest {

    private final ScoringService scoring = new ScoringService();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void singleCorrectAwardsFullMarks() {
        assertThat(scoring.scoreQuestion(
                QuestionType.SINGLE_CORRECT,
                new BigDecimal("4"),
                new BigDecimal("1"),
                Set.of(a),
                Set.of(a),
                false
        )).isEqualByComparingTo("4");
    }

    @Test
    void wrongSingleCorrectAppliesNegativeMarks() {
        assertThat(scoring.scoreQuestion(
                QuestionType.SINGLE_CORRECT,
                new BigDecimal("4"),
                new BigDecimal("1"),
                Set.of(a),
                Set.of(b),
                false
        )).isEqualByComparingTo("-1");
    }

    @Test
    void multipleCorrectCanAwardPartialMarksWithoutWrongSelection() {
        assertThat(scoring.scoreQuestion(
                QuestionType.MULTIPLE_CORRECT,
                new BigDecimal("4"),
                new BigDecimal("1"),
                Set.of(a, b),
                Set.of(a),
                true
        )).isEqualByComparingTo("2.00");
    }

    @Test
    void multipleCorrectPenalisesWrongSelections() {
        assertThat(scoring.scoreQuestion(
                QuestionType.MULTIPLE_CORRECT,
                new BigDecimal("4"),
                new BigDecimal("1"),
                Set.of(a, b),
                Set.of(a, c),
                true
        )).isEqualByComparingTo("1.00");
    }

    @Test
    void totalScoreNeverDropsBelowZero() {
        assertThat(scoring.floorTotal(List.of(
                new BigDecimal("-1"),
                new BigDecimal("-0.5")
        ))).isEqualByComparingTo("0.00");
    }
}
