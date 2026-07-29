package com.rabbit.aip.notification;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference extends TenantEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled;

    @Column(name = "assessment_reminders", nullable = false)
    private boolean assessmentReminders;

    @Column(name = "workflow_updates", nullable = false)
    private boolean workflowUpdates;

    @Column(name = "result_updates", nullable = false)
    private boolean resultUpdates;

    protected NotificationPreference() {
    }

    public NotificationPreference(UUID organisationId, UUID userId) {
        super(organisationId);
        this.userId = userId;
        this.inAppEnabled = true;
        this.emailEnabled = true;
        this.smsEnabled = false;
        this.assessmentReminders = true;
        this.workflowUpdates = true;
        this.resultUpdates = true;
    }

    public void update(
            boolean inAppEnabled,
            boolean emailEnabled,
            boolean smsEnabled,
            boolean assessmentReminders,
            boolean workflowUpdates,
            boolean resultUpdates
    ) {
        this.inAppEnabled = inAppEnabled;
        this.emailEnabled = emailEnabled;
        this.smsEnabled = smsEnabled;
        this.assessmentReminders = assessmentReminders;
        this.workflowUpdates = workflowUpdates;
        this.resultUpdates = resultUpdates;
    }

    public boolean accepts(NotificationType type, boolean critical) {
        if (critical) return true;
        if (!inAppEnabled) return false;
        return switch (type) {
            case ASSESSMENT_REMINDER -> assessmentReminders;
            case WORKFLOW -> workflowUpdates;
            case RESULT_PUBLISHED -> resultUpdates;
            default -> true;
        };
    }

    public UUID getUserId() { return userId; }
    public boolean isInAppEnabled() { return inAppEnabled; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public boolean isSmsEnabled() { return smsEnabled; }
    public boolean isAssessmentReminders() { return assessmentReminders; }
    public boolean isWorkflowUpdates() { return workflowUpdates; }
    public boolean isResultUpdates() { return resultUpdates; }
}
