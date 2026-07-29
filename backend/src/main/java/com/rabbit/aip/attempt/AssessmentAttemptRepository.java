package com.rabbit.aip.attempt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentAttemptRepository
        extends JpaRepository<AssessmentAttempt, UUID> {

    Optional<AssessmentAttempt>
    findFirstByOrganisationIdAndAssessmentIdAndStudentUserIdAndStatus(
            UUID organisationId,
            UUID assessmentId,
            UUID studentUserId,
            AttemptStatus status
    );

    Optional<AssessmentAttempt> findByIdAndOrganisationIdAndStudentUserId(
            UUID id,
            UUID organisationId,
            UUID studentUserId
    );

    long countByOrganisationIdAndAssessmentIdAndStudentUserId(
            UUID organisationId,
            UUID assessmentId,
            UUID studentUserId
    );

    Optional<AssessmentAttempt> findByIdAndOrganisationId(
            UUID id,
            UUID organisationId
    );

    List<AssessmentAttempt> findAllByOrganisationIdAndAssessmentIdOrderBySubmittedAtAsc(
            UUID organisationId,
            UUID assessmentId
    );

    List<AssessmentAttempt> findAllByOrganisationIdAndStudentUserIdOrderBySubmittedAtAsc(
            UUID organisationId,
            UUID studentUserId
    );

    List<AssessmentAttempt> findAllByOrganisationIdOrderBySubmittedAtDesc(
            UUID organisationId
    );
}
