package com.rabbit.aip.attempt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attempt
            from AssessmentAttempt attempt
            where attempt.id = :id
              and attempt.organisationId = :organisationId
              and attempt.studentUserId = :studentUserId
            """)
    Optional<AssessmentAttempt> findStudentAttemptForUpdate(
            @Param("id") UUID id,
            @Param("organisationId") UUID organisationId,
            @Param("studentUserId") UUID studentUserId
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

    List<AssessmentAttempt> findAllByOrganisationIdAndStudentUserIdOrderByStartedAtDesc(
            UUID organisationId,
            UUID studentUserId
    );

    List<AssessmentAttempt> findAllByOrganisationIdOrderBySubmittedAtDesc(
            UUID organisationId
    );

    long countByOrganisationIdAndStatus(
            UUID organisationId,
            AttemptStatus status
    );

    long countByOrganisationIdAndResultStatusAndSubmittedAtIsNotNull(
            UUID organisationId,
            ResultPublicationStatus resultStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attempt
            from AssessmentAttempt attempt
            where attempt.status = :status
              and attempt.expiresAt <= :expiredBefore
            order by attempt.expiresAt
            """)
    List<AssessmentAttempt> findExpiredForUpdate(
            @Param("status") AttemptStatus status,
            @Param("expiredBefore") Instant expiredBefore
    );
}
