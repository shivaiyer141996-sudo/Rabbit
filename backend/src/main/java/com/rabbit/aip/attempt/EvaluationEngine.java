package com.rabbit.aip.attempt;

import com.rabbit.aip.assessment.Assessment;
import com.rabbit.aip.question.Question;
import com.rabbit.aip.question.QuestionOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EvaluationEngine {

    private final ScoringService scoring;

    public EvaluationEngine(ScoringService scoring) {
        this.scoring = scoring;
    }

    public EvaluationOutcome evaluate(
            Assessment assessment,
            List<Question> assessmentQuestions,
            List<AttemptResponse> responses
    ) {
        Map<UUID, AttemptResponse> responseMap = new HashMap<>();
        responses.forEach(response -> responseMap.put(response.getQuestionId(), response));
        List<BigDecimal> itemScores = new ArrayList<>();
        int correctCount = 0;
        int wrongCount = 0;
        int unansweredCount = 0;

        for (Question question : assessmentQuestions) {
            Set<UUID> correctOptions = question.getOptions().stream()
                    .filter(QuestionOption::isCorrect)
                    .map(QuestionOption::getId)
                    .collect(java.util.stream.Collectors.toSet());
            AttemptResponse response = responseMap.get(question.getId());
            Set<UUID> selected = response == null
                    ? Set.of()
                    : response.getSelectedOptionIds();
            BigDecimal itemScore = scoring.scoreQuestion(
                    question.getType(),
                    question.getMarks(),
                    question.getNegativeMarks(),
                    correctOptions,
                    selected,
                    assessment.isPartialMarking()
            );
            boolean exact = !selected.isEmpty() && selected.equals(correctOptions);
            if (selected.isEmpty()) {
                unansweredCount += 1;
            } else if (exact) {
                correctCount += 1;
            } else {
                wrongCount += 1;
            }
            if (response != null) response.recordEvaluation(itemScore, exact);
            itemScores.add(itemScore);
        }

        BigDecimal score = scoring.floorTotal(itemScores);
        BigDecimal percentage = assessment.getTotalMarks().signum() == 0
                ? BigDecimal.ZERO
                : score.multiply(BigDecimal.valueOf(100))
                        .divide(assessment.getTotalMarks(), 2, RoundingMode.HALF_UP);
        return new EvaluationOutcome(
                score,
                assessment.getTotalMarks(),
                percentage,
                correctCount,
                wrongCount,
                unansweredCount
        );
    }

    public record EvaluationOutcome(
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            int correctAnswers,
            int wrongAnswers,
            int unansweredAnswers
    ) {
    }
}
