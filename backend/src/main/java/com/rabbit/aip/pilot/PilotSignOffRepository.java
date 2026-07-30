package com.rabbit.aip.pilot;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PilotSignOffRepository extends JpaRepository<PilotSignOff, UUID> {
    Optional<PilotSignOff> findByOrganisationId(UUID organisationId);
}
