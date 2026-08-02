package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.SubscriptionEventType;
import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "commercial_subscription_events")
public class CommercialSubscriptionEvent extends TenantEntity {

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private SubscriptionEventType eventType;
    @Column(name = "before_value", columnDefinition = "text")
    private String beforeValue;
    @Column(name = "after_value", nullable = false, columnDefinition = "text")
    private String afterValue;
    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected CommercialSubscriptionEvent() {
    }

    public CommercialSubscriptionEvent(
            UUID organisationId,
            UUID subscriptionId,
            SubscriptionEventType eventType,
            String beforeValue,
            String afterValue,
            UUID actorUserId,
            Instant occurredAt
    ) {
        super(organisationId);
        this.subscriptionId = subscriptionId;
        this.eventType = eventType;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
    }

    public UUID getSubscriptionId() { return subscriptionId; }
    public SubscriptionEventType getEventType() { return eventType; }
    public String getBeforeValue() { return beforeValue; }
    public String getAfterValue() { return afterValue; }
    public UUID getActorUserId() { return actorUserId; }
    public Instant getOccurredAt() { return occurredAt; }
}
