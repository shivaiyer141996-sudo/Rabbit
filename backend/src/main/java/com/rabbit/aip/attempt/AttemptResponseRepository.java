package com.rabbit.aip.attempt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptResponseRepository
        extends JpaRepository<AttemptResponse, UUID> {
    Optional<AttemptResponse> findByAttemptIdAndQuestionId(
            UUID attemptId,
            UUID questionId
    );
    List<AttemptResponse> findAllByAttemptId(UUID attemptId);
}
