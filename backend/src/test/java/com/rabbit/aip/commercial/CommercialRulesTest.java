package com.rabbit.aip.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbit.aip.commercial.CommercialTypes.InvoiceStatus;
import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import com.rabbit.aip.commercial.CommercialTypes.SubscriptionEventType;
import com.rabbit.aip.commercial.CommercialTypes.SubscriptionStatus;
import com.rabbit.aip.commercial.CommercialTypes.SupportCategory;
import com.rabbit.aip.commercial.CommercialTypes.SupportSeverity;
import com.rabbit.aip.commercial.CommercialTypes.SupportStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommercialRulesTest {

    private static final UUID ORGANISATION_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID ACTOR_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333301"
    );

    @Test
    void approvedMonthlyPricesAreExactForEveryPlanAndCapacity() {
        assertThat(CommercialTypes.monthlyPricePaise(PlanCode.BASIC, 50)).isEqualTo(59_900);
        assertThat(CommercialTypes.monthlyPricePaise(PlanCode.BASIC, 150)).isEqualTo(99_900);
        assertThat(CommercialTypes.monthlyPricePaise(PlanCode.BASIC, 500)).isEqualTo(149_900);
        assertThat(CommercialTypes.monthlyPricePaise(PlanCode.PRO, 50)).isEqualTo(89_900);
        assertThat(CommercialTypes.monthlyPricePaise(PlanCode.PRO, 150)).isEqualTo(139_900);
        assertThat(CommercialTypes.monthlyPricePaise(PlanCode.PRO, 500)).isEqualTo(189_900);
        assertThat(CommercialTypes.monthlyPricePaise(PlanCode.LEGEND, 50)).isEqualTo(149_900);
        assertThat(CommercialTypes.monthlyPricePaise(PlanCode.LEGEND, 150)).isEqualTo(199_900);
        assertThat(CommercialTypes.monthlyPricePaise(PlanCode.LEGEND, 500)).isEqualTo(249_900);
        assertThatThrownBy(() -> CommercialTypes.monthlyPricePaise(PlanCode.BASIC, 51))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void studentCountSelectsTheSmallestApprovedCapacity() {
        assertThat(CommercialTypes.studentLimitFor(1)).isEqualTo(50);
        assertThat(CommercialTypes.studentLimitFor(50)).isEqualTo(50);
        assertThat(CommercialTypes.studentLimitFor(51)).isEqualTo(150);
        assertThat(CommercialTypes.studentLimitFor(151)).isEqualTo(500);
        assertThatThrownBy(() -> CommercialTypes.studentLimitFor(501))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legendTrialIsExactlyTwentyDaysAndExpiresOnce() {
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        CommercialSubscription subscription = CommercialSubscription.startTrial(
                ORGANISATION_ID, 50, start, ACTOR_ID, "First trial"
        );

        assertThat(subscription.getPlan()).isEqualTo(PlanCode.LEGEND);
        assertThat(Duration.between(start, subscription.getTrialEndsAt()))
                .isEqualTo(Duration.ofDays(20));
        assertThat(subscription.refresh(start.plus(Duration.ofDays(19)), ACTOR_ID)).isNull();
        assertThat(subscription.refresh(start.plus(Duration.ofDays(20)), ACTOR_ID))
                .isEqualTo(SubscriptionEventType.TRIAL_EXPIRED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(subscription.refresh(start.plus(Duration.ofDays(21)), ACTOR_ID)).isNull();
    }

    @Test
    void paidRenewalIsScheduledWithoutCuttingShortCurrentPeriod() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        UUID currentInvoice = UUID.randomUUID();
        CommercialSubscription subscription = CommercialSubscription.startPaid(
                ORGANISATION_ID,
                PlanCode.LEGEND,
                150,
                now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(29)),
                currentInvoice,
                ACTOR_ID,
                "Current period"
        );
        Instant renewalStart = subscription.getPeriodEndsAt();
        SubscriptionEventType transition = subscription.applyPaidPlan(
                PlanCode.PRO,
                50,
                renewalStart,
                renewalStart.plus(Duration.ofDays(30)),
                UUID.randomUUID(),
                ACTOR_ID,
                "Scheduled downgrade",
                now
        );

        assertThat(transition).isEqualTo(SubscriptionEventType.PLAN_CHANGE_SCHEDULED);
        assertThat(subscription.getPlan()).isEqualTo(PlanCode.LEGEND);
        assertThat(subscription.getPendingPlan()).isEqualTo(PlanCode.PRO);
        assertThat(subscription.refresh(renewalStart, ACTOR_ID))
                .isEqualTo(SubscriptionEventType.PLAN_CHANGE_APPLIED);
        assertThat(subscription.getPlan()).isEqualTo(PlanCode.PRO);
        assertThat(subscription.getStudentLimit()).isEqualTo(50);
    }

    @Test
    void commercialActivationRequiresFinalM56EvidenceAndExactCommit() {
        String evidence = "urn:rabbit-evidence:m5-6:final:20260901T000000Z:"
                + "a".repeat(64);
        assertThatCode(() -> CommercialLaunchGuard.validateActivation(
                "a1b2c3d4", evidence
        )).doesNotThrowAnyException();

        assertThatThrownBy(() -> CommercialLaunchGuard.validateActivation(
                "unversioned", evidence
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CommercialLaunchGuard.validateActivation(
                "a1b2c3d4", "https://public.example/m5-6"
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolvedSupportCaseRequiresResolutionText() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        CommercialSupportCase supportCase = new CommercialSupportCase(
                ORGANISATION_ID,
                "SUP-20260901-ABC12345",
                SupportSeverity.S2,
                SupportCategory.ASSESSMENT,
                "Assessment unavailable",
                "The assessment list is empty for the approved section.",
                ACTOR_ID,
                now.plus(Duration.ofHours(4)),
                ACTOR_ID
        );
        assertThatThrownBy(() -> supportCase.update(
                SupportStatus.RESOLVED, "Local support", "", ACTOR_ID, now
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> supportCase.update(
                SupportStatus.RESOLVED,
                "Local support",
                "Section eligibility was corrected and verified.",
                ACTOR_ID,
                now
        )).doesNotThrowAnyException();
    }

    @Test
    void invoiceTransitionsRecordTheActualUpdater() {
        Instant issuedAt = Instant.parse("2026-09-01T00:00:00Z");
        UUID paymentOperator = UUID.fromString(
                "33333333-3333-3333-3333-333333333302"
        );
        CommercialInvoice invoice = new CommercialInvoice(
                ORGANISATION_ID,
                "RAB-202609-001",
                PlanCode.BASIC,
                50,
                issuedAt,
                issuedAt.plus(Duration.ofDays(30)),
                0,
                issuedAt,
                issuedAt.plus(Duration.ofDays(7)),
                "Manual invoice",
                ACTOR_ID
        );

        invoice.markPaid(issuedAt.plus(Duration.ofDays(1)), paymentOperator);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getCreatedByUserId()).isEqualTo(ACTOR_ID);
        assertThat(invoice.getUpdatedByUserId()).isEqualTo(paymentOperator);
    }

    @Test
    void suspensionDoesNotExtendOrReviveAnEndedTrial() {
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        CommercialSubscription subscription = CommercialSubscription.startTrial(
                ORGANISATION_ID, 50, start, ACTOR_ID, "First trial"
        );

        assertThat(subscription.suspend(ACTOR_ID, "Institution requested a pause"))
                .isEqualTo(SubscriptionEventType.SUBSCRIPTION_SUSPENDED);
        assertThat(subscription.restore(
                start.plus(Duration.ofDays(19)), ACTOR_ID, "Issue resolved"
        )).isEqualTo(SubscriptionEventType.SUBSCRIPTION_RESTORED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);

        subscription.suspend(ACTOR_ID, "Second incident");
        assertThatThrownBy(() -> subscription.restore(
                start.plus(Duration.ofDays(20)), ACTOR_ID, "Too late"
        )).isInstanceOf(IllegalStateException.class);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
    }

    @Test
    void paidUpgradeDoesNotSilentlyRestoreASuspendedSubscription() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        CommercialSubscription subscription = CommercialSubscription.startPaid(
                ORGANISATION_ID,
                PlanCode.BASIC,
                50,
                now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(29)),
                UUID.randomUUID(),
                ACTOR_ID,
                "Current plan"
        );
        subscription.suspend(ACTOR_ID, "Access review");

        subscription.applyPaidPlan(
                PlanCode.PRO,
                50,
                now,
                now.plus(Duration.ofDays(30)),
                UUID.randomUUID(),
                ACTOR_ID,
                "Verified upgrade",
                now
        );

        assertThat(subscription.getPlan()).isEqualTo(PlanCode.PRO);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
    }
}
