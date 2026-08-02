package com.rabbit.aip.commercial;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialSubscriptionRepository
        extends JpaRepository<CommercialSubscription, UUID> {
    Optional<CommercialSubscription> findByOrganisationId(UUID organisationId);
}
