package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
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

    @Column(name = "monthly_price_paise", nullable = false)
    private long monthlyPricePaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionStatus status;

    @Column(name = "trial_starts_at")
    private Instant trialStartsAt;
    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;
    @Column(name = "period_starts_at")
    private Instant periodStartsAt;
    @Column(name = "period_ends_at")
    private Instant periodEndsAt;

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
            Instant startsAt,
            Instant endsAt,
            UUID invoiceId,
            UUID actorUserId,
            String note,
            boolean trial
    ) {
        super(organisationId);
        createdByUserId = actorUserId;
        updatedByUserId = actorUserId;
        this.note = normalized(note);
        this.plan = trial ? PlanCode.LEGEND : plan;
        this.studentLimit = studentLimit;
        monthlyPricePaise = CommercialTypes.monthlyPricePaise(this.plan, studentLimit);
        if (trial) {
            status = SubscriptionStatus.TRIALING;
            trialStartsAt = startsAt;
            trialEndsAt = startsAt.plusSeconds(CommercialTypes.TRIAL_DAYS * 86_400L);
        } else {
            status = SubscriptionStatus.ACTIVE;
            periodStartsAt = startsAt;
            periodEndsAt = endsAt;
            sourceInvoiceId = invoiceId;
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
                organisationId, PlanCode.LEGEND, studentLimit, startsAt, null,
                null, actorUserId, note, true
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
                organisationId, plan, studentLimit, startsAt, endsAt,
                invoiceId, actorUserId, note, false
        );
    }

    public SubscriptionEventType refresh(Instant now, UUID actorUserId) {
        if (pendingPlan != null && !pendingPeriodStartsAt.isAfter(now)) {
            boolean remainSuspended = status == SubscriptionStatus.SUSPENDED;
            plan = pendingPlan;
            studentLimit = pendingStudentLimit;
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
                status = SubscriptionStatus.EXPIRED;
                updatedByUserId = actorUserId;
                return periodEndsAt == null
                        ? SubscriptionEventType.TRIAL_EXPIRED
                        : SubscriptionEventType.SUBSCRIPTION_EXPIRED;
            }
        }
        if (status == SubscriptionStatus.TRIALING && !trialEndsAt.isAfter(now)) {
            status = SubscriptionStatus.EXPIRED;
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
        boolean remainSuspended = status == SubscriptionStatus.SUSPENDED;
        if ((status == SubscriptionStatus.ACTIVE || remainSuspended)
                && periodEndsAt != null
                && !nextStartsAt.isBefore(periodEndsAt)) {
            pendingPlan = nextPlan;
            pendingStudentLimit = nextLimit;
            pendingMonthlyPricePaise = CommercialTypes.monthlyPricePaise(nextPlan, nextLimit);
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
                nextPlan, nextLimit, nextStartsAt, nextEndsAt,
                invoiceId, actorUserId, nextNote
        );
        if (remainSuspended) status = SubscriptionStatus.SUSPENDED;
        return SubscriptionEventType.PLAN_ACTIVATED;
    }

    public SubscriptionEventType suspend(
            UUID actorUserId,
            String suspensionNote
    ) {
        if (status != SubscriptionStatus.TRIALING
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
            status = SubscriptionStatus.TRIALING;
        }
        updatedByUserId = actorUserId;
        note = normalized(restorationNote);
        return SubscriptionEventType.SUBSCRIPTION_RESTORED;
    }

    private void activate(
            PlanCode nextPlan,
            int nextLimit,
            Instant nextStartsAt,
            Instant nextEndsAt,
            UUID invoiceId,
            UUID actorUserId,
            String nextNote
    ) {
        plan = nextPlan;
        studentLimit = nextLimit;
        monthlyPricePaise = CommercialTypes.monthlyPricePaise(nextPlan, nextLimit);
        status = SubscriptionStatus.ACTIVE;
        periodStartsAt = nextStartsAt;
        periodEndsAt = nextEndsAt;
        sourceInvoiceId = invoiceId;
        clearPending();
        updatedByUserId = actorUserId;
        note = normalized(nextNote);
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
    public long getMonthlyPricePaise() { return monthlyPricePaise; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getTrialStartsAt() { return trialStartsAt; }
    public Instant getTrialEndsAt() { return trialEndsAt; }
    public Instant getPeriodStartsAt() { return periodStartsAt; }
    public Instant getPeriodEndsAt() { return periodEndsAt; }
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
