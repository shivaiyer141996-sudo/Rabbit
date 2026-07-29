package com.rabbit.aip.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationDeliveryWorker {

    private final NotificationRepository notifications;

    public NotificationDeliveryWorker(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Scheduled(fixedDelayString = "${rabbit.notifications.retry-delay-ms:60000}")
    @Transactional
    public void retryPendingInAppDeliveries() {
        notifications
                .findTop50ByDeliveryStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                        DeliveryStatus.PENDING, 3
                )
                .forEach(notification -> {
                    try {
                        notification.markDelivered();
                    } catch (RuntimeException exception) {
                        notification.recordFailure(exception.getMessage());
                    }
                });
    }
}
