package com.rabbit.aip.assessment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentReviewRepository
        extends JpaRepository<AssessmentReview, UUID> {
    List<AssessmentReview> findAllByOrganisationIdAndAssessmentIdOrderByCreatedAtDesc(
            UUID organisationId,
            UUID assessmentId
    );
}
