package com.rabbit.aip.pilot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PilotCheckResultRepository
        extends JpaRepository<PilotCheckResult, UUID> {

    List<PilotCheckResult> findAllByOrganisationIdOrderByKeyAsc(UUID organisationId);

    Optional<PilotCheckResult> findByOrganisationIdAndKey(
            UUID organisationId,
            PilotCheckKey key
    );
}
