package com.rabbit.aip.pilot;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PilotReleaseDecisionRepository
        extends JpaRepository<PilotReleaseDecision, UUID> {
    List<PilotReleaseDecision> findAllByOrganisationIdOrderByDecidedAtDesc(
            UUID organisationId
    );
}
