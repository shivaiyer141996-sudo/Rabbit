package com.rabbit.aip.attempt;

import com.rabbit.aip.attempt.AttemptDtos.StudentAssessmentStatus;
import java.time.Instant;

public final class StudentAssessmentClassifier {
    private StudentAssessmentClassifier() {
    }

    public static StudentAssessmentStatus classify(
            Instant now,
            Instant startAt,
            Instant endAt,
            boolean completed
    ) {
        if (completed) return StudentAssessmentStatus.COMPLETED;
        if (now.isBefore(startAt)) return StudentAssessmentStatus.UPCOMING;
        if (now.isBefore(endAt)) return StudentAssessmentStatus.AVAILABLE_NOW;
        return StudentAssessmentStatus.MISSED_CLOSED;
    }
}
