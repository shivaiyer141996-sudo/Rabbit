package com.rabbit.aip.question;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionReviewRepository extends JpaRepository<QuestionReview, UUID> {
    List<QuestionReview> findAllByOrganisationIdAndQuestionIdOrderByCreatedAtDesc(
            UUID organisationId,
            UUID questionId
    );
}
