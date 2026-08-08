package com.rabbit.aip.platform;

import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import com.rabbit.aip.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rabbit_platform_settings")
public class RabbitPlatformSettings extends BaseEntity {
    @Column(name = "default_trial_days", nullable = false)
    private int defaultTrialDays;
    @Enumerated(EnumType.STRING)
    @Column(name = "default_trial_plan_code", nullable = false, length = 20)
    private PlanCode defaultTrialPlan;
    @Column(name = "trial_reminder_days", nullable = false, columnDefinition = "integer[]")
    private Integer[] trialReminderDays;
    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    protected RabbitPlatformSettings() {
    }

    public void update(int trialDays, PlanCode trialPlan, List<Integer> reminderDays, UUID actor) {
        defaultTrialDays = trialDays;
        defaultTrialPlan = trialPlan;
        trialReminderDays = reminderDays.toArray(Integer[]::new);
        updatedByUserId = actor;
    }

    public int getDefaultTrialDays() { return defaultTrialDays; }
    public PlanCode getDefaultTrialPlan() { return defaultTrialPlan; }
    public List<Integer> getTrialReminderDays() { return Arrays.asList(trialReminderDays); }
    public UUID getUpdatedByUserId() { return updatedByUserId; }
}
