package com.rabbit.aip.commercial;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialSubscriptionEventRepository
        extends JpaRepository<CommercialSubscriptionEvent, UUID> {
    List<CommercialSubscriptionEvent> findAllByOrganisationIdOrderByOccurredAtDesc(
            UUID organisationId
    );
}
