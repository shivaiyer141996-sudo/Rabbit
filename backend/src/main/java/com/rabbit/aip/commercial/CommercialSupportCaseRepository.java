package com.rabbit.aip.commercial;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialSupportCaseRepository
        extends JpaRepository<CommercialSupportCase, UUID> {
    List<CommercialSupportCase> findAllByOrganisationIdOrderByCreatedAtDesc(UUID organisationId);
    Optional<CommercialSupportCase> findByIdAndOrganisationId(UUID id, UUID organisationId);
}
