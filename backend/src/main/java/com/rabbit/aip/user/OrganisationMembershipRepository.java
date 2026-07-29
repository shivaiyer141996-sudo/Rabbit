package com.rabbit.aip.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationMembershipRepository
        extends JpaRepository<OrganisationMembership, UUID> {

    List<OrganisationMembership> findAllByUserIdAndStatus(
            UUID userId,
            AccountStatus status
    );

    Optional<OrganisationMembership> findByUserIdAndOrganisationIdAndStatus(
            UUID userId,
            UUID organisationId,
            AccountStatus status
    );

    List<OrganisationMembership> findAllByOrganisationIdOrderByCreatedAtDesc(
            UUID organisationId
    );
}
