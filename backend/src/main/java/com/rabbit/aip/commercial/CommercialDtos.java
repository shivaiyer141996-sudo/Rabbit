package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.Entitlement;
import com.rabbit.aip.commercial.CommercialTypes.InvoiceStatus;
import com.rabbit.aip.commercial.CommercialTypes.PaymentMethod;
import com.rabbit.aip.commercial.CommercialTypes.PaymentStatus;
import com.rabbit.aip.commercial.CommercialTypes.ManualPaymentStatus;
import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import com.rabbit.aip.commercial.CommercialTypes.SubscriptionEventType;
import com.rabbit.aip.commercial.CommercialTypes.SubscriptionStatus;
import com.rabbit.aip.commercial.CommercialTypes.SupportCategory;
import com.rabbit.aip.commercial.CommercialTypes.SupportSeverity;
import com.rabbit.aip.commercial.CommercialTypes.SupportStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CommercialDtos {

    private CommercialDtos() {
    }

    public record PricePoint(int studentLimit, long monthlyPricePaise) {
    }

    public record PlanCatalogResponse(
            PlanCode code,
            String label,
            String description,
            List<PricePoint> prices,
            Set<Entitlement> entitlements
    ) {
    }

    public record SubscriptionResponse(
            UUID id,
            PlanCode plan,
            int studentLimit,
            PlanCode selectedPlan,
            int selectedStudentLimit,
            long monthlyPricePaise,
            SubscriptionStatus status,
            Instant trialStartsAt,
            Instant trialEndsAt,
            boolean trialEnabled,
            Integer trialDurationDays,
            PlanCode trialPlan,
            Instant periodStartsAt,
            Instant periodEndsAt,
            Instant graceEndsAt,
            ManualPaymentStatus paymentStatus,
            String paymentReference,
            String paymentRemarks,
            Long amountPaise,
            Instant activationDate,
            PlanCode pendingPlan,
            Integer pendingStudentLimit,
            Long pendingMonthlyPricePaise,
            Instant pendingPeriodStartsAt,
            Instant pendingPeriodEndsAt,
            String note,
            long rowVersion,
            Set<Entitlement> entitlements
    ) {
        public static SubscriptionResponse from(CommercialSubscription value) {
            return from(value, CommercialTypes.entitlements(value.getPlan()));
        }

        public static SubscriptionResponse from(
                CommercialSubscription value, Set<Entitlement> entitlements
        ) {
            return new SubscriptionResponse(
                    value.getId(),
                    value.getPlan(),
                    value.getStudentLimit(),
                    value.getSelectedPlan(),
                    value.getSelectedStudentLimit(),
                    value.getMonthlyPricePaise(),
                    value.getStatus(),
                    value.getTrialStartsAt(),
                    value.getTrialEndsAt(),
                    value.isTrialEnabled(),
                    value.getTrialDurationDays(),
                    value.getTrialPlan(),
                    value.getPeriodStartsAt(),
                    value.getPeriodEndsAt(),
                    value.getGraceEndsAt(),
                    value.getPaymentStatus(),
                    value.getPaymentReference(),
                    value.getPaymentRemarks(),
                    value.getAmountPaise(),
                    value.getActivationDate(),
                    value.getPendingPlan(),
                    value.getPendingStudentLimit(),
                    value.getPendingMonthlyPricePaise(),
                    value.getPendingPeriodStartsAt(),
                    value.getPendingPeriodEndsAt(),
                    value.getNote(),
                    value.getRowVersion(),
                    entitlements
            );
        }
    }

    public record SubscriptionEventResponse(
            UUID id,
            SubscriptionEventType eventType,
            String beforeValue,
            String afterValue,
            UUID actorUserId,
            Instant occurredAt
    ) {
        static SubscriptionEventResponse from(CommercialSubscriptionEvent value) {
            return new SubscriptionEventResponse(
                    value.getId(), value.getEventType(), value.getBeforeValue(),
                    value.getAfterValue(), value.getActorUserId(), value.getOccurredAt()
            );
        }
    }

    public record InvoiceResponse(
            UUID id,
            String invoiceNumber,
            PlanCode plan,
            int studentLimit,
            Instant periodStartsAt,
            Instant periodEndsAt,
            long subtotalPaise,
            long taxPaise,
            long totalPaise,
            InvoiceStatus status,
            Instant issuedAt,
            Instant dueAt,
            Instant paidAt,
            String note
    ) {
        static InvoiceResponse from(CommercialInvoice value) {
            return new InvoiceResponse(
                    value.getId(), value.getInvoiceNumber(), value.getPlan(),
                    value.getStudentLimit(), value.getPeriodStartsAt(),
                    value.getPeriodEndsAt(), value.getSubtotalPaise(),
                    value.getTaxPaise(), value.getTotalPaise(), value.getStatus(),
                    value.getIssuedAt(), value.getDueAt(), value.getPaidAt(), value.getNote()
            );
        }
    }

    public record PaymentResponse(
            UUID id,
            UUID invoiceId,
            String paymentReference,
            PaymentMethod paymentMethod,
            long amountPaise,
            PaymentStatus status,
            Instant paidAt,
            String note
    ) {
        static PaymentResponse from(CommercialPayment value) {
            return new PaymentResponse(
                    value.getId(), value.getInvoiceId(), value.getPaymentReference(),
                    value.getPaymentMethod(), value.getAmountPaise(), value.getStatus(),
                    value.getPaidAt(), value.getNote()
            );
        }
    }

    public record ReceiptResponse(
            UUID id,
            UUID paymentId,
            UUID invoiceId,
            String receiptNumber,
            long amountPaise,
            Instant issuedAt
    ) {
        static ReceiptResponse from(CommercialReceipt value) {
            return new ReceiptResponse(
                    value.getId(), value.getPaymentId(), value.getInvoiceId(),
                    value.getReceiptNumber(), value.getAmountPaise(), value.getIssuedAt()
            );
        }
    }

    public record SupportCaseResponse(
            UUID id,
            String caseNumber,
            SupportSeverity severity,
            SupportCategory category,
            SupportStatus status,
            String summary,
            String description,
            UUID requesterUserId,
            String assignedTo,
            Instant responseDueAt,
            Instant resolvedAt,
            String resolution,
            Instant createdAt,
            Instant updatedAt
    ) {
        static SupportCaseResponse from(CommercialSupportCase value) {
            return new SupportCaseResponse(
                    value.getId(), value.getCaseNumber(), value.getSeverity(),
                    value.getCategory(), value.getStatus(), value.getSummary(),
                    value.getDescription(), value.getRequesterUserId(),
                    value.getAssignedTo(), value.getResponseDueAt(),
                    value.getResolvedAt(), value.getResolution(),
                    value.getCreatedAt(), value.getUpdatedAt()
            );
        }
    }

    public record CommercialOverviewResponse(
            boolean enforcementEnabled,
            boolean m5_6ActivationEvidenceAccepted,
            int trialDays,
            Instant serverNow,
            int activeAndInvitedStudents,
            int availableStudentSlots,
            Set<Entitlement> effectiveEntitlements,
            SubscriptionResponse subscription,
            List<PlanCatalogResponse> catalog,
            List<SubscriptionEventResponse> subscriptionEvents,
            List<InvoiceResponse> invoices,
            List<PaymentResponse> payments,
            List<ReceiptResponse> receipts,
            List<SupportCaseResponse> supportCases
    ) {
    }

    public record CommercialAccessResponse(
            boolean enforcementEnabled,
            PlanCode plan,
            SubscriptionStatus status,
            Integer studentLimit,
            Instant accessEndsAt,
            long daysRemaining,
            Set<Entitlement> entitlements
    ) {
    }

    public record StartTrialRequest(
            @Min(1) @Max(500) int declaredStudents,
            @Size(max = 1000) String note
    ) {
    }

    public record OnboardOrganisationRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{2,30}$") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 80) String timezone,
            @Email @NotBlank String adminEmail,
            @NotBlank @Size(max = 100) String adminFirstName,
            @NotBlank @Size(max = 100) String adminLastName,
            @Min(1) @Max(500) int declaredStudents,
            @Size(max = 1000) String note
    ) {
    }

    public record OnboardOrganisationResponse(
            UUID organisationId,
            String organisationCode,
            String organisationName,
            UUID administratorUserId,
            String administratorEmail,
            String activationUrl,
            Instant activationExpiresAt,
            SubscriptionResponse subscription
    ) {
    }

    public record CreateInvoiceRequest(
            @NotBlank @Size(max = 80) String invoiceNumber,
            @NotNull PlanCode plan,
            @NotNull @Positive Integer studentLimit,
            @NotNull Instant periodStartsAt,
            @NotNull Instant periodEndsAt,
            @PositiveOrZero long taxPaise,
            @NotNull Instant issuedAt,
            @NotNull Instant dueAt,
            @Size(max = 1000) String note
    ) {
    }

    public record RecordPaymentRequest(
            @NotNull UUID invoiceId,
            @NotBlank @Size(max = 120) String paymentReference,
            @NotNull PaymentMethod paymentMethod,
            @Positive long amountPaise,
            @NotNull Instant paidAt,
            @Size(max = 1000) String note
    ) {
    }

    public record PaymentReceiptResponse(
            PaymentResponse payment,
            ReceiptResponse receipt,
            SubscriptionResponse subscription
    ) {
    }

    public record VoidInvoiceRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record SubscriptionStateRequest(
            @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record CreateSupportCaseRequest(
            @NotNull SupportSeverity severity,
            @NotNull SupportCategory category,
            @NotBlank @Size(max = 200) String summary,
            @NotBlank @Size(max = 5000) String description
    ) {
    }

    public record UpdateSupportCaseRequest(
            @NotNull SupportStatus status,
            @Size(max = 200) String assignedTo,
            @Size(max = 5000) String resolution
    ) {
    }
}
