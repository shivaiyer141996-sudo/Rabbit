package com.rabbit.aip.notification;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification extends TenantEntity {

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @Column(nullable = false)
    private boolean critical;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 30)
    private DeliveryStatus deliveryStatus;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(
            UUID organisationId,
            UUID recipientUserId,
            NotificationType type,
            String title,
            String message,
            String actionUrl,
            boolean critical
    ) {
        super(organisationId);
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.actionUrl = actionUrl;
        this.critical = critical;
        this.deliveryStatus = DeliveryStatus.DELIVERED;
    }

    public void markRead() {
        if (readAt == null) readAt = Instant.now();
    }

    public void markDelivered() {
        deliveryStatus = DeliveryStatus.DELIVERED;
        lastError = null;
    }

    public void recordFailure(String error) {
        retryCount += 1;
        lastError = error;
        deliveryStatus = retryCount >= 3 ? DeliveryStatus.FAILED : DeliveryStatus.PENDING;
    }

    public UUID getRecipientUserId() { return recipientUserId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getActionUrl() { return actionUrl; }
    public boolean isCritical() { return critical; }
    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public int getRetryCount() { return retryCount; }
    public String getLastError() { return lastError; }
    public Instant getReadAt() { return readAt; }
}
