package com.rabbit.aip.commercial;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialTrialReminderRepository
        extends JpaRepository<CommercialTrialReminder, UUID> {
    boolean existsBySubscriptionIdAndReminderDays(UUID subscriptionId, int reminderDays);
}
