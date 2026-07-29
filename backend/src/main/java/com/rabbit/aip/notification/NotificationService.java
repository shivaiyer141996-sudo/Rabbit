package com.rabbit.aip.notification;

import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.notification.NotificationDtos.NotificationInbox;
import com.rabbit.aip.notification.NotificationDtos.NotificationResponse;
import com.rabbit.aip.notification.NotificationDtos.PreferenceRequest;
import com.rabbit.aip.notification.NotificationDtos.PreferenceResponse;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.user.OrganisationMembershipRepository;
import com.rabbit.aip.user.UserRole;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final OrganisationMembershipRepository memberships;
    private final CurrentSession session;

    public NotificationService(
            NotificationRepository notifications,
            NotificationPreferenceRepository preferences,
            OrganisationMembershipRepository memberships,
            CurrentSession session
    ) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.memberships = memberships;
        this.session = session;
    }

    @Transactional(readOnly = true)
    public NotificationInbox inbox() {
        UUID organisationId = session.organisationId();
        UUID userId = session.userId();
        return new NotificationInbox(
                notifications.countByOrganisationIdAndRecipientUserIdAndReadAtIsNull(
                        organisationId, userId
                ),
                notifications
                        .findAllByOrganisationIdAndRecipientUserIdOrderByCreatedAtDesc(
                                organisationId, userId
                        ).stream()
                        .limit(100)
                        .map(NotificationResponse::from)
                        .toList()
        );
    }

    @Transactional
    public NotificationResponse markRead(UUID id) {
        Notification notification = notifications
                .findByIdAndOrganisationIdAndRecipientUserId(
                        id, session.organisationId(), session.userId()
                )
                .orElseThrow(() -> DomainException.notFound(
                        "NOTIFICATION_NOT_FOUND",
                        "Notification was not found."
                ));
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead() {
        notifications.findAllByOrganisationIdAndRecipientUserIdOrderByCreatedAtDesc(
                        session.organisationId(), session.userId()
                )
                .forEach(Notification::markRead);
    }

    @Transactional
    public void notifyUser(
            UUID recipientUserId,
            NotificationType type,
            String title,
            String message,
            String actionUrl,
            boolean critical
    ) {
        UUID organisationId = session.organisationId();
        NotificationPreference preference = preferences
                .findByOrganisationIdAndUserId(organisationId, recipientUserId)
                .orElseGet(() -> preferences.save(new NotificationPreference(
                        organisationId, recipientUserId
                )));
        if (!preference.accepts(type, critical)) return;
        notifications.save(new Notification(
                organisationId,
                recipientUserId,
                type,
                title,
                message,
                actionUrl,
                critical
        ));
    }

    @Transactional
    public void notifyRoles(
            Set<UserRole> roles,
            NotificationType type,
            String title,
            String message,
            String actionUrl,
            boolean critical
    ) {
        memberships.findAllByOrganisationIdOrderByCreatedAtDesc(
                        session.organisationId()
                ).stream()
                .filter(membership -> roles.contains(membership.getRole()))
                .forEach(membership -> notifyUser(
                        membership.getUserId(),
                        type,
                        title,
                        message,
                        actionUrl,
                        critical
                ));
    }

    @Transactional(readOnly = true)
    public PreferenceResponse preference() {
        return PreferenceResponse.from(preferences
                .findByOrganisationIdAndUserId(
                        session.organisationId(), session.userId()
                )
                .orElseGet(() -> new NotificationPreference(
                        session.organisationId(), session.userId()
                )));
    }

    @Transactional
    public PreferenceResponse updatePreference(PreferenceRequest request) {
        NotificationPreference preference = preferences
                .findByOrganisationIdAndUserId(
                        session.organisationId(), session.userId()
                )
                .orElseGet(() -> preferences.save(new NotificationPreference(
                        session.organisationId(), session.userId()
                )));
        preference.update(
                request.inAppEnabled(),
                request.emailEnabled(),
                request.smsEnabled(),
                request.assessmentReminders(),
                request.workflowUpdates(),
                request.resultUpdates()
        );
        return PreferenceResponse.from(preference);
    }
}
