package com.rabbit.aip.commercial;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "commercial_trial_reminder_log")
public class CommercialTrialReminder extends TenantEntity {
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;
    @Column(name = "reminder_days", nullable = false)
    private int reminderDays;
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected CommercialTrialReminder() {
    }

    public CommercialTrialReminder(
            UUID organisationId, UUID subscriptionId, int reminderDays, Instant sentAt
    ) {
        super(organisationId);
        this.subscriptionId = subscriptionId;
        this.reminderDays = reminderDays;
        this.sentAt = sentAt;
    }
}
