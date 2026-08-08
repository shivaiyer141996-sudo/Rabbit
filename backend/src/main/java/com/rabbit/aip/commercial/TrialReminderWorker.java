package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.SubscriptionStatus;
import com.rabbit.aip.notification.NotificationService;
import com.rabbit.aip.notification.NotificationType;
import com.rabbit.aip.platform.RabbitPlatformSettingsRepository;
import com.rabbit.aip.user.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TrialReminderWorker {
    private final CommercialSubscriptionRepository subscriptions;
    private final CommercialTrialReminderRepository reminders;
    private final RabbitPlatformSettingsRepository settings;
    private final NotificationService notifications;
    private final Clock clock;

    public TrialReminderWorker(
            CommercialSubscriptionRepository subscriptions,
            CommercialTrialReminderRepository reminders,
            RabbitPlatformSettingsRepository settings,
            NotificationService notifications,
            Clock clock
    ) {
        this.subscriptions = subscriptions;
        this.reminders = reminders;
        this.settings = settings;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "${rabbit.commercial.trial-reminder-cron:0 15 8 * * *}")
    @Transactional
    public void sendTrialExpiryReminders() {
        var configuration = settings.findAll().stream().findFirst().orElse(null);
        if (configuration == null) return;
        Instant now = clock.instant();
        subscriptions.findAll().stream()
                .filter(value -> value.getStatus() == SubscriptionStatus.TRIAL)
                .filter(value -> value.getTrialEndsAt() != null && value.getTrialEndsAt().isAfter(now))
                .forEach(subscription -> {
                    long remaining = Math.max(1, (long) Math.ceil(
                            Duration.between(now, subscription.getTrialEndsAt()).toSeconds() / 86_400.0
                    ));
                    configuration.getTrialReminderDays().stream()
                            .filter(day -> remaining <= day)
                            .filter(day -> !reminders.existsBySubscriptionIdAndReminderDays(
                                    subscription.getId(), day
                            ))
                            .min(Integer::compareTo)
                            .ifPresent(day -> {
                                notifications.notifyRolesForOrganisation(
                                        subscription.getOrganisationId(),
                                        Set.of(UserRole.SUPER_ADMIN, UserRole.ORG_ADMIN),
                                        NotificationType.ALERT,
                                        "Rabbit trial expires in " + remaining + " day(s)",
                                        "The Organisation trial ends on " + subscription.getTrialEndsAt()
                                                + ". A Super Admin can activate or extend the plan manually.",
                                        "/commercial", true
                                );
                                reminders.save(new CommercialTrialReminder(
                                        subscription.getOrganisationId(), subscription.getId(), day, now
                                ));
                            });
                });
    }
}
