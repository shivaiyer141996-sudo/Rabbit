package com.rabbit.aip.attempt;

import com.rabbit.aip.question.QuestionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ScoringService {

    public BigDecimal scoreQuestion(
            QuestionType type,
            BigDecimal marks,
            BigDecimal negativeMarks,
            Set<UUID> correctOptions,
            Set<UUID> selectedOptions,
            boolean partialMarking
    ) {
        if (selectedOptions.isEmpty()) return BigDecimal.ZERO;
        if (selectedOptions.equals(correctOptions)) return marks;
        if (type == QuestionType.SINGLE_CORRECT || !partialMarking) {
            return negativeMarks.negate();
        }

        long correctSelected = selectedOptions.stream()
                .filter(correctOptions::contains)
                .count();
        long wrongSelected = selectedOptions.size() - correctSelected;
        BigDecimal partial = marks
                .multiply(BigDecimal.valueOf(correctSelected))
                .divide(
                        BigDecimal.valueOf(correctOptions.size()),
                        4,
                        RoundingMode.HALF_UP
                );
        BigDecimal penalty = negativeMarks.multiply(BigDecimal.valueOf(wrongSelected));
        return partial.subtract(penalty).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal floorTotal(Collection<BigDecimal> scores) {
        BigDecimal total = scores.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
