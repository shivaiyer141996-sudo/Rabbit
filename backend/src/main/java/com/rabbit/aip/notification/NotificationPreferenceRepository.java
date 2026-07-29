package com.rabbit.aip.notification;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreference, UUID> {
    Optional<NotificationPreference> findByOrganisationIdAndUserId(
            UUID organisationId,
            UUID userId
    );
}
