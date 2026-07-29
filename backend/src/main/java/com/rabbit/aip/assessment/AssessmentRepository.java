package com.rabbit.aip.assessment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
    List<Assessment> findAllByOrganisationIdOrderByUpdatedAtDesc(UUID organisationId);
    Optional<Assessment> findByIdAndOrganisationId(UUID id, UUID organisationId);
    List<Assessment> findAllByOrganisationIdAndStatusAndStartAtLessThanEqualAndEndAtGreaterThan(
            UUID organisationId,
            AssessmentStatus status,
            Instant startsBefore,
            Instant endsAfter
    );
    long countByOrganisationIdAndStatus(
            UUID organisationId,
            AssessmentStatus status
    );
}
