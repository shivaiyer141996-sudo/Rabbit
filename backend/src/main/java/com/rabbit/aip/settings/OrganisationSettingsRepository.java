package com.rabbit.aip.settings;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationSettingsRepository
        extends JpaRepository<OrganisationSettings, UUID> {
    Optional<OrganisationSettings> findByOrganisationId(UUID organisationId);
}
