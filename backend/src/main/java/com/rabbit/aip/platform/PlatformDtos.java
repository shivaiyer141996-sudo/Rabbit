package com.rabbit.aip.platform;

import com.rabbit.aip.commercial.CommercialDtos.PlanCatalogResponse;
import com.rabbit.aip.commercial.CommercialDtos.SubscriptionResponse;
import com.rabbit.aip.commercial.CommercialTypes.ManualPaymentStatus;
import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import com.rabbit.aip.commercial.CommercialTypes.SubscriptionStatus;
import com.rabbit.aip.user.AccountStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlatformDtos {
    private PlatformDtos() {
    }

    public record CustomerAccountRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{2,50}$") String code,
            @NotBlank @Size(max = 200) String name
    ) {
    }

    public record CustomerAccountStateRequest(
            @NotNull CustomerAccountStatus status,
            @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record CustomerAccountResponse(
            UUID id,
            String code,
            String name,
            CustomerAccountStatus status,
            Instant archivedAt,
            long organisationCount,
            long studentUsage,
            long studentCapacity,
            long trials,
            long activeSubscriptions
    ) {
    }

    public record PlatformOrganisationResponse(
            UUID id,
            UUID customerAccountId,
            String code,
            String name,
            String timezone,
            AccountStatus status,
            boolean logoAvailable,
            Instant logoUpdatedAt,
            PlanCode effectivePlan,
            PlanCode selectedPlan,
            SubscriptionStatus subscriptionStatus,
            int studentCapacity,
            long studentUsage,
            Instant accessEndsAt
    ) {
    }

    public record PlatformSettingsResponse(
            int defaultTrialDays,
            PlanCode defaultTrialPlan,
            List<Integer> reminderDays
    ) {
    }

    public record PlatformSettingsRequest(
            @Min(1) @Max(365) int defaultTrialDays,
            @NotNull PlanCode defaultTrialPlan,
            @NotNull @Size(min = 1, max = 10) List<@Min(1) @Max(365) Integer> reminderDays
    ) {
    }

    public record PlatformDashboardResponse(
            long totalCustomerAccounts,
            long totalOrganisations,
            long organisationsOnTrial,
            long trialsExpiringSoon,
            long activeSubscriptions,
            long expiredSubscriptions,
            long basicOrganisations,
            long proOrganisations,
            long legendOrganisations,
            long studentCapacity,
            long actualStudentUsage,
            Instant serverNow
    ) {
    }

    public record PlatformOverviewResponse(
            PlatformDashboardResponse dashboard,
            PlatformSettingsResponse settings,
            List<PlanCatalogResponse> plans,
            List<CustomerAccountResponse> customerAccounts,
            List<PlatformOrganisationResponse> organisations
    ) {
    }

    public record OnboardOrganisationRequest(
            @NotNull UUID customerAccountId,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{2,30}$") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 80) String timezone,
            @Email @NotBlank String adminEmail,
            @NotBlank @Size(max = 100) String adminFirstName,
            @NotBlank @Size(max = 100) String adminLastName,
            @NotNull PlanCode selectedPlan,
            @Min(1) int studentCapacity,
            boolean trialEnabled,
            @Min(1) @Max(365) Integer trialDurationDays,
            PlanCode trialPlan,
            @NotNull Instant activationDate,
            @Size(max = 1000) String note
    ) {
    }

    public record OnboardOrganisationResponse(
            PlatformOrganisationResponse organisation,
            UUID administratorUserId,
            String administratorEmail,
            String activationUrl,
            Instant activationExpiresAt,
            SubscriptionResponse subscription
    ) {
    }

    public record AssignOrganisationRequest(
            @NotNull UUID customerAccountId,
            @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record OrganisationDetailsRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 80) String timezone
    ) {
    }

    public record SubscriptionActionRequest(
            @NotBlank @Pattern(regexp = "UPGRADE|DOWNGRADE|RENEW|ACTIVATE|EXTEND_TRIAL|SUSPEND|REACTIVATE|GRACE_PERIOD|CANCEL|PAYMENT_STATUS") String action,
            PlanCode plan,
            @Min(1) Integer studentCapacity,
            Instant startsAt,
            Instant endsAt,
            @Min(1) @Max(365) Integer extensionDays,
            ManualPaymentStatus paymentStatus,
            @PositiveOrZero Long amountPaise,
            @Size(max = 120) String paymentReference,
            @Size(max = 1000) String remarks,
            @NotBlank @Size(max = 1000) String reason
    ) {
    }
}
