package com.rabbit.aip.settings;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "organisation_settings")
public class OrganisationSettings extends TenantEntity {

    @Column(nullable = false, length = 80)
    private String timezone;

    @Column(nullable = false, length = 20)
    private String language;

    @Column(name = "pass_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal passPercentage;

    @Column(name = "at_risk_threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal atRiskThreshold;

    @Column(name = "default_duration_minutes", nullable = false)
    private int defaultDurationMinutes;

    @Column(name = "default_attempts_allowed", nullable = false)
    private int defaultAttemptsAllowed;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions;

    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions;

    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled;

    @Column(name = "sms_notifications_enabled", nullable = false)
    private boolean smsNotificationsEnabled;

    @Column(name = "audit_retention_days", nullable = false)
    private int auditRetentionDays;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "primary_colour", nullable = false, length = 7)
    private String primaryColour;

    protected OrganisationSettings() {
    }

    public OrganisationSettings(UUID organisationId) {
        super(organisationId);
        this.timezone = "Asia/Kolkata";
        this.language = "en";
        this.passPercentage = BigDecimal.valueOf(40);
        this.atRiskThreshold = BigDecimal.valueOf(40);
        this.defaultDurationMinutes = 45;
        this.defaultAttemptsAllowed = 1;
        this.shuffleQuestions = true;
        this.shuffleOptions = false;
        this.emailNotificationsEnabled = true;
        this.smsNotificationsEnabled = false;
        this.auditRetentionDays = 2555;
        this.displayName = "Rabbit AiP";
        this.primaryColour = "#5936C8";
    }

    public void update(
            String timezone,
            String language,
            BigDecimal passPercentage,
            BigDecimal atRiskThreshold,
            int defaultDurationMinutes,
            int defaultAttemptsAllowed,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            boolean emailNotificationsEnabled,
            boolean smsNotificationsEnabled,
            int auditRetentionDays,
            String displayName,
            String primaryColour
    ) {
        this.timezone = timezone;
        this.language = language;
        this.passPercentage = passPercentage;
        this.atRiskThreshold = atRiskThreshold;
        this.defaultDurationMinutes = defaultDurationMinutes;
        this.defaultAttemptsAllowed = defaultAttemptsAllowed;
        this.shuffleQuestions = shuffleQuestions;
        this.shuffleOptions = shuffleOptions;
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.smsNotificationsEnabled = smsNotificationsEnabled;
        this.auditRetentionDays = auditRetentionDays;
        this.displayName = displayName;
        this.primaryColour = primaryColour.toUpperCase();
    }

    public String getTimezone() { return timezone; }
    public String getLanguage() { return language; }
    public BigDecimal getPassPercentage() { return passPercentage; }
    public BigDecimal getAtRiskThreshold() { return atRiskThreshold; }
    public int getDefaultDurationMinutes() { return defaultDurationMinutes; }
    public int getDefaultAttemptsAllowed() { return defaultAttemptsAllowed; }
    public boolean isShuffleQuestions() { return shuffleQuestions; }
    public boolean isShuffleOptions() { return shuffleOptions; }
    public boolean isEmailNotificationsEnabled() { return emailNotificationsEnabled; }
    public boolean isSmsNotificationsEnabled() { return smsNotificationsEnabled; }
    public int getAuditRetentionDays() { return auditRetentionDays; }
    public String getDisplayName() { return displayName; }
    public String getPrimaryColour() { return primaryColour; }
}
