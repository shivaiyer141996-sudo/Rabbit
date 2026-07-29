package com.rabbit.aip.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findAllByOrganisationIdAndRecipientUserIdOrderByCreatedAtDesc(
            UUID organisationId,
            UUID recipientUserId
    );

    Optional<Notification> findByIdAndOrganisationIdAndRecipientUserId(
            UUID id,
            UUID organisationId,
            UUID recipientUserId
    );

    long countByOrganisationIdAndRecipientUserIdAndReadAtIsNull(
            UUID organisationId,
            UUID recipientUserId
    );

    List<Notification> findTop50ByDeliveryStatusAndRetryCountLessThanOrderByCreatedAtAsc(
            DeliveryStatus status,
            int retryCount
    );
}
