package com.rabbit.aip.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            UUID id,
            NotificationType type,
            String title,
            String message,
            String actionUrl,
            boolean critical,
            DeliveryStatus deliveryStatus,
            boolean read,
            Instant createdAt
    ) {
        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getActionUrl(),
                    notification.isCritical(),
                    notification.getDeliveryStatus(),
                    notification.getReadAt() != null,
                    notification.getCreatedAt()
            );
        }
    }

    public record NotificationInbox(
            long unreadCount,
            List<NotificationResponse> items
    ) {
    }

    public record PreferenceRequest(
            boolean inAppEnabled,
            boolean emailEnabled,
            boolean smsEnabled,
            boolean assessmentReminders,
            boolean workflowUpdates,
            boolean resultUpdates
    ) {
    }

    public record PreferenceResponse(
            boolean inAppEnabled,
            boolean emailEnabled,
            boolean smsEnabled,
            boolean assessmentReminders,
            boolean workflowUpdates,
            boolean resultUpdates
    ) {
        static PreferenceResponse from(NotificationPreference preference) {
            return new PreferenceResponse(
                    preference.isInAppEnabled(),
                    preference.isEmailEnabled(),
                    preference.isSmsEnabled(),
                    preference.isAssessmentReminders(),
                    preference.isWorkflowUpdates(),
                    preference.isResultUpdates()
            );
        }
    }
}
