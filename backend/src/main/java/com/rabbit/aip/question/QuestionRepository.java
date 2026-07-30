package com.rabbit.aip.question;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    @EntityGraph(attributePaths = "options")
    List<Question> findAllByOrganisationIdOrderByUpdatedAtDesc(UUID organisationId);

    @EntityGraph(attributePaths = "options")
    Optional<Question> findByIdAndOrganisationId(UUID id, UUID organisationId);

    @EntityGraph(attributePaths = "options")
    List<Question> findAllByIdInAndOrganisationId(
            Collection<UUID> ids,
            UUID organisationId
    );

    boolean existsByOrganisationIdAndCodeAndVersion(
            UUID organisationId,
            String code,
            int version
    );

    boolean existsByOrganisationIdAndSubjectId(
            UUID organisationId,
            UUID subjectId
    );

    long countByOrganisationIdAndStatus(
            UUID organisationId,
            QuestionStatus status
    );

    long countByOrganisationIdAndStatusAndUpdatedAtBefore(
            UUID organisationId,
            QuestionStatus status,
            Instant updatedBefore
    );
}
