package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import com.rabbit.aip.commercial.CommercialTypes.ManualPaymentStatus;
import com.rabbit.aip.commercial.CommercialTypes.SubscriptionEventType;
import com.rabbit.aip.commercial.CommercialTypes.SubscriptionStatus;
import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organisation_subscriptions")
public class CommercialSubscription extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false, length = 20)
    private PlanCode plan;

    @Column(name = "student_limit", nullable = false)
    private int studentLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_plan_code", nullable = false, length = 20)
    private PlanCode selectedPlan;
    @Column(name = "selected_student_limit", nullable = false)
    private int selectedStudentLimit;

    @Column(name = "monthly_price_paise", nullable = false)
    private long monthlyPricePaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionStatus status;

    @Column(name = "trial_starts_at")
    private Instant trialStartsAt;
    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;
    @Column(name = "trial_enabled", nullable = false)
    private boolean trialEnabled;
    @Column(name = "trial_duration_days")
    private Integer trialDurationDays;
    @Enumerated(EnumType.STRING)
    @Column(name = "trial_plan_code", length = 20)
    private PlanCode trialPlan;
    @Column(name = "period_starts_at")
    private Instant periodStartsAt;
    @Column(name = "period_ends_at")
    private Instant periodEndsAt;
    @Column(name = "grace_ends_at")
    private Instant graceEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private ManualPaymentStatus paymentStatus;
    @Column(name = "payment_reference", length = 120)
    private String paymentReference;
    @Column(name = "payment_remarks", length = 1000)
    private String paymentRemarks;
    @Column(name = "amount_paise")
    private Long amountPaise;
    @Column(name = "activation_date")
    private Instant activationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_plan_code", length = 20)
    private PlanCode pendingPlan;
    @Column(name = "pending_student_limit")
    private Integer pendingStudentLimit;
    @Column(name = "pending_monthly_price_paise")
    private Long pendingMonthlyPricePaise;
    @Column(name = "pending_period_starts_at")
    private Instant pendingPeriodStartsAt;
    @Column(name = "pending_period_ends_at")
    private Instant pendingPeriodEndsAt;

    @Column(name = "source_invoice_id")
    private UUID sourceInvoiceId;
    @Column(name = "pending_source_invoice_id")
    private UUID pendingSourceInvoiceId;
    @Column(length = 1000)
    private String note;
    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;
    @Column(name = "updated_by_user_id", nullable = false)
    private UUID updatedByUserId;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected CommercialSubscription() {
    }

    CommercialSubscription(
            UUID organisationId,
            PlanCode plan,
            int studentLimit,
            long configuredPricePaise,
            Instant startsAt,
            Instant endsAt,
            UUID invoiceId,
            UUID actorUserId,
            String note,
            Integer configuredTrialDays,
            PlanCode configuredTrialPlan
    ) {
        super(organisationId);
        createdByUserId = actorUserId;
        updatedByUserId = actorUserId;
        this.note = normalized(note);
        boolean trial = configuredTrialDays != null;
        this.plan = trial ? configuredTrialPlan : plan;
        this.studentLimit = studentLimit;
        selectedPlan = plan;
        selectedStudentLimit = studentLimit;
        monthlyPricePaise = configuredPricePaise;
        activationDate = startsAt;
        if (trial) {
            status = SubscriptionStatus.TRIAL;
            trialEnabled = true;
            trialDurationDays = configuredTrialDays;
            trialPlan = configuredTrialPlan;
            trialStartsAt = startsAt;
            trialEndsAt = startsAt.plusSeconds(configuredTrialDays * 86_400L);
            paymentStatus = ManualPaymentStatus.PENDING;
            amountPaise = configuredPricePaise;
        } else {
            status = SubscriptionStatus.ACTIVE;
            periodStartsAt = startsAt;
            periodEndsAt = endsAt;
            sourceInvoiceId = invoiceId;
            paymentStatus = ManualPaymentStatus.PAID;
            amountPaise = configuredPricePaise;
        }
    }

    public static CommercialSubscription startTrial(
            UUID organisationId,
            int studentLimit,
            Instant startsAt,
            UUID actorUserId,
            String note
    ) {
        return new CommercialSubscription(
                organisationId, PlanCode.LEGEND, studentLimit,
                CommercialTypes.monthlyPricePaise(PlanCode.LEGEND, studentLimit),
                startsAt, null, null, actorUserId, note,
                CommercialTypes.TRIAL_DAYS, PlanCode.LEGEND
        );
    }

    public static CommercialSubscription startTrial(
            UUID organisationId,
            PlanCode selectedPlan,
            PlanCode trialPlan,
            int studentLimit,
            long configuredPricePaise,
            int trialDays,
            Instant startsAt,
            UUID actorUserId,
            String note
    ) {
        return new CommercialSubscription(
                organisationId, selectedPlan, studentLimit, configuredPricePaise,
                startsAt, null, null, actorUserId, note, trialDays, trialPlan
        );
    }

    public static CommercialSubscription startPaid(
            UUID organisationId,
            PlanCode plan,
            int studentLimit,
            Instant startsAt,
            Instant endsAt,
            UUID invoiceId,
            UUID actorUserId,
            String note
    ) {
        return new CommercialSubscription(
                organisationId, plan, studentLimit,
                CommercialTypes.monthlyPricePaise(plan, studentLimit),
                startsAt, endsAt, invoiceId, actorUserId, note, null, null
        );
    }

    public static CommercialSubscription startPaid(
            UUID organisationId,
            PlanCode plan,
            int studentLimit,
            long configuredPricePaise,
            Instant startsAt,
            Instant endsAt,
            UUID invoiceId,
            UUID actorUserId,
            String note,
            ManualPaymentStatus paymentStatus,
            String paymentReference,
            Long amountPaise
    ) {
        CommercialSubscription result = new CommercialSubscription(
                organisationId, plan, studentLimit, configuredPricePaise,
                startsAt, endsAt, invoiceId, actorUserId, note, null, null
        );
        result.paymentStatus = paymentStatus;
        result.paymentReference = normalized(paymentReference);
        result.amountPaise = amountPaise;
        return result;
    }

    public static CommercialSubscription createPending(
            UUID organisationId,
            PlanCode selectedPlan,
            int studentLimit,
            long configuredPricePaise,
            Instant activationDate,
            UUID actorUserId,
            String note
    ) {
        CommercialSubscription result = new CommercialSubscription(
                organisationId, selectedPlan, studentLimit, configuredPricePaise,
                activationDate, activationDate.plusSeconds(86_400L), null,
                actorUserId, note, null, null
        );
        result.status = SubscriptionStatus.SUSPENDED;
        result.periodStartsAt = null;
        result.periodEndsAt = null;
        result.paymentStatus = ManualPaymentStatus.PENDING;
        result.amountPaise = configuredPricePaise;
        return result;
    }

    public SubscriptionEventType refresh(Instant now, UUID actorUserId) {
        if (pendingPlan != null && !pendingPeriodStartsAt.isAfter(now)) {
            boolean remainSuspended = status == SubscriptionStatus.SUSPENDED;
            plan = pendingPlan;
            studentLimit = pendingStudentLimit;
            selectedPlan = pendingPlan;
            selectedStudentLimit = pendingStudentLimit;
            monthlyPricePaise = pendingMonthlyPricePaise;
            periodStartsAt = pendingPeriodStartsAt;
            periodEndsAt = pendingPeriodEndsAt;
            sourceInvoiceId = pendingSourceInvoiceId;
            status = remainSuspended
                    ? SubscriptionStatus.SUSPENDED
                    : SubscriptionStatus.ACTIVE;
            clearPending();
            updatedByUserId = actorUserId;
            return SubscriptionEventType.PLAN_CHANGE_APPLIED;
        }
        if (status == SubscriptionStatus.SUSPENDED) {
            Instant accessEndsAt = periodEndsAt != null ? periodEndsAt : trialEndsAt;
            if (accessEndsAt != null && !accessEndsAt.isAfter(now)) {
                status = trialEnabled && periodEndsAt == null
                        ? SubscriptionStatus.TRIAL_EXPIRED
                        : SubscriptionStatus.EXPIRED;
                updatedByUserId = actorUserId;
                return periodEndsAt == null
                        ? SubscriptionEventType.TRIAL_EXPIRED
                        : SubscriptionEventType.SUBSCRIPTION_EXPIRED;
            }
        }
        if (status == SubscriptionStatus.TRIAL && !trialEndsAt.isAfter(now)) {
            status = SubscriptionStatus.TRIAL_EXPIRED;
            updatedByUserId = actorUserId;
            return SubscriptionEventType.TRIAL_EXPIRED;
        }
        if (status == SubscriptionStatus.ACTIVE
                && periodEndsAt != null
                && !periodEndsAt.isAfter(now)) {
            status = SubscriptionStatus.EXPIRED;
            updatedByUserId = actorUserId;
            return SubscriptionEventType.SUBSCRIPTION_EXPIRED;
        }
        if (status == SubscriptionStatus.GRACE_PERIOD
                && graceEndsAt != null && !graceEndsAt.isAfter(now)) {
            status = SubscriptionStatus.EXPIRED;
            updatedByUserId = actorUserId;
            return SubscriptionEventType.SUBSCRIPTION_EXPIRED;
        }
        return null;
    }

    public SubscriptionEventType applyPaidPlan(
            PlanCode nextPlan,
            int nextLimit,
            Instant nextStartsAt,
            Instant nextEndsAt,
            UUID invoiceId,
            UUID actorUserId,
            String nextNote,
            Instant now
    ) {
        return applyPaidPlan(nextPlan, nextLimit,
                CommercialTypes.monthlyPricePaise(nextPlan, nextLimit),
                nextStartsAt, nextEndsAt, invoiceId, actorUserId, nextNote, now);
    }

    public SubscriptionEventType applyPaidPlan(
            PlanCode nextPlan,
            int nextLimit,
            long nextPricePaise,
            Instant nextStartsAt,
            Instant nextEndsAt,
            UUID invoiceId,
            UUID actorUserId,
            String nextNote,
            Instant now
    ) {
        boolean remainSuspended = status == SubscriptionStatus.SUSPENDED;
        if ((status == SubscriptionStatus.ACTIVE || remainSuspended)
                && periodEndsAt != null
                && !nextStartsAt.isBefore(periodEndsAt)) {
            pendingPlan = nextPlan;
            pendingStudentLimit = nextLimit;
            pendingMonthlyPricePaise = nextPricePaise;
            pendingPeriodStartsAt = nextStartsAt;
            pendingPeriodEndsAt = nextEndsAt;
            pendingSourceInvoiceId = invoiceId;
            updatedByUserId = actorUserId;
            note = normalized(nextNote);
            return SubscriptionEventType.PLAN_CHANGE_SCHEDULED;
        }
        if (nextStartsAt.isAfter(now)) {
            throw new IllegalArgumentException("Immediate paid plans cannot start in the future.");
        }
        activate(
                nextPlan, nextLimit, nextPricePaise, nextStartsAt, nextEndsAt,
                invoiceId, actorUserId, nextNote
        );
        if (remainSuspended) status = SubscriptionStatus.SUSPENDED;
        return SubscriptionEventType.PLAN_ACTIVATED;
    }

    public SubscriptionEventType suspend(
            UUID actorUserId,
            String suspensionNote
    ) {
        if (status != SubscriptionStatus.TRIAL
                && status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only an active trial or paid subscription can be suspended."
            );
        }
        status = SubscriptionStatus.SUSPENDED;
        updatedByUserId = actorUserId;
        note = normalized(suspensionNote);
        return SubscriptionEventType.SUBSCRIPTION_SUSPENDED;
    }

    public SubscriptionEventType restore(
            Instant now,
            UUID actorUserId,
            String restorationNote
    ) {
        if (status != SubscriptionStatus.SUSPENDED) {
            throw new IllegalStateException("Only a suspended subscription can be restored.");
        }
        if (periodEndsAt != null) {
            if (!periodEndsAt.isAfter(now)) {
                throw new IllegalStateException(
                        "The paid period has ended; record a new paid invoice instead."
                );
            }
            status = SubscriptionStatus.ACTIVE;
        } else {
            if (trialEndsAt == null || !trialEndsAt.isAfter(now)) {
                throw new IllegalStateException(
                        "The trial has ended and cannot be restored."
                );
            }
            status = SubscriptionStatus.TRIAL;
        }
        updatedByUserId = actorUserId;
        note = normalized(restorationNote);
        return SubscriptionEventType.SUBSCRIPTION_RESTORED;
    }

    private void activate(
            PlanCode nextPlan,
            int nextLimit,
            long nextPricePaise,
            Instant nextStartsAt,
            Instant nextEndsAt,
            UUID invoiceId,
            UUID actorUserId,
            String nextNote
    ) {
        plan = nextPlan;
        studentLimit = nextLimit;
        monthlyPricePaise = nextPricePaise;
        status = SubscriptionStatus.ACTIVE;
        periodStartsAt = nextStartsAt;
        periodEndsAt = nextEndsAt;
        sourceInvoiceId = invoiceId;
        clearPending();
        updatedByUserId = actorUserId;
        note = normalized(nextNote);
    }

    public SubscriptionEventType extendTrial(
            int additionalDays, UUID actorUserId, String reason
    ) {
        if (!trialEnabled || trialEndsAt == null || additionalDays < 1) {
            throw new IllegalStateException("Only an existing trial can be extended.");
        }
        trialDurationDays += additionalDays;
        trialEndsAt = trialEndsAt.plusSeconds(additionalDays * 86_400L);
        if (status == SubscriptionStatus.TRIAL_EXPIRED) status = SubscriptionStatus.TRIAL;
        updatedByUserId = actorUserId;
        note = normalized(reason);
        return SubscriptionEventType.TRIAL_EXTENDED;
    }

    public SubscriptionEventType manualActivate(
            PlanCode nextPlan,
            int nextLimit,
            long nextPricePaise,
            Instant startsAt,
            Instant endsAt,
            ManualPaymentStatus nextPaymentStatus,
            Long nextAmountPaise,
            String reference,
            String remarks,
            UUID actorUserId,
            String reason
    ) {
        plan = nextPlan;
        studentLimit = nextLimit;
        selectedPlan = nextPlan;
        selectedStudentLimit = nextLimit;
        monthlyPricePaise = nextPricePaise;
        status = SubscriptionStatus.ACTIVE;
        periodStartsAt = startsAt;
        periodEndsAt = endsAt;
        graceEndsAt = null;
        activationDate = startsAt;
        paymentStatus = nextPaymentStatus;
        amountPaise = nextAmountPaise;
        paymentReference = normalized(reference);
        paymentRemarks = normalized(remarks);
        updatedByUserId = actorUserId;
        note = normalized(reason);
        clearPending();
        return SubscriptionEventType.PLAN_ACTIVATED;
    }

    public SubscriptionEventType startGracePeriod(
            Instant endsAt, UUID actorUserId, String reason
    ) {
        if (status != SubscriptionStatus.EXPIRED
                && status != SubscriptionStatus.TRIAL_EXPIRED) {
            throw new IllegalStateException("Grace Period can start only after expiry.");
        }
        status = SubscriptionStatus.GRACE_PERIOD;
        graceEndsAt = endsAt;
        updatedByUserId = actorUserId;
        note = normalized(reason);
        return SubscriptionEventType.GRACE_PERIOD_STARTED;
    }

    public SubscriptionEventType cancel(UUID actorUserId, String reason) {
        status = SubscriptionStatus.CANCELLED;
        updatedByUserId = actorUserId;
        note = normalized(reason);
        return SubscriptionEventType.SUBSCRIPTION_CANCELLED;
    }

    public SubscriptionEventType changePaymentStatus(
            ManualPaymentStatus nextStatus,
            Long nextAmountPaise,
            String reference,
            String remarks,
            UUID actorUserId
    ) {
        paymentStatus = nextStatus;
        amountPaise = nextAmountPaise;
        paymentReference = normalized(reference);
        paymentRemarks = normalized(remarks);
        updatedByUserId = actorUserId;
        return SubscriptionEventType.PAYMENT_STATUS_CHANGED;
    }

    private void clearPending() {
        pendingPlan = null;
        pendingStudentLimit = null;
        pendingMonthlyPricePaise = null;
        pendingPeriodStartsAt = null;
        pendingPeriodEndsAt = null;
        pendingSourceInvoiceId = null;
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public PlanCode getPlan() { return plan; }
    public int getStudentLimit() { return studentLimit; }
    public PlanCode getSelectedPlan() { return selectedPlan; }
    public int getSelectedStudentLimit() { return selectedStudentLimit; }
    public long getMonthlyPricePaise() { return monthlyPricePaise; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getTrialStartsAt() { return trialStartsAt; }
    public Instant getTrialEndsAt() { return trialEndsAt; }
    public boolean isTrialEnabled() { return trialEnabled; }
    public Integer getTrialDurationDays() { return trialDurationDays; }
    public PlanCode getTrialPlan() { return trialPlan; }
    public Instant getPeriodStartsAt() { return periodStartsAt; }
    public Instant getPeriodEndsAt() { return periodEndsAt; }
    public Instant getGraceEndsAt() { return graceEndsAt; }
    public ManualPaymentStatus getPaymentStatus() { return paymentStatus; }
    public String getPaymentReference() { return paymentReference; }
    public String getPaymentRemarks() { return paymentRemarks; }
    public Long getAmountPaise() { return amountPaise; }
    public Instant getActivationDate() { return activationDate; }
    public PlanCode getPendingPlan() { return pendingPlan; }
    public Integer getPendingStudentLimit() { return pendingStudentLimit; }
    public Long getPendingMonthlyPricePaise() { return pendingMonthlyPricePaise; }
    public Instant getPendingPeriodStartsAt() { return pendingPeriodStartsAt; }
    public Instant getPendingPeriodEndsAt() { return pendingPeriodEndsAt; }
    public UUID getSourceInvoiceId() { return sourceInvoiceId; }
    public UUID getPendingSourceInvoiceId() { return pendingSourceInvoiceId; }
    public String getNote() { return note; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public UUID getUpdatedByUserId() { return updatedByUserId; }
    public long getRowVersion() { return rowVersion; }
}
