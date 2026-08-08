package com.rabbit.aip.platform;

import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.auth.InvitationService;
import com.rabbit.aip.commercial.CommercialCatalogService;
import com.rabbit.aip.commercial.CommercialDtos.SubscriptionResponse;
import com.rabbit.aip.commercial.CommercialPlanPrice;
import com.rabbit.aip.commercial.CommercialSubscription;
import com.rabbit.aip.commercial.CommercialSubscriptionEvent;
import com.rabbit.aip.commercial.CommercialSubscriptionEventRepository;
import com.rabbit.aip.commercial.CommercialSubscriptionRepository;
import com.rabbit.aip.commercial.CommercialTypes.ManualPaymentStatus;
import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import com.rabbit.aip.commercial.CommercialTypes.SubscriptionEventType;
import com.rabbit.aip.commercial.CommercialTypes.SubscriptionStatus;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.organisation.Organisation;
import com.rabbit.aip.organisation.OrganisationRepository;
import com.rabbit.aip.platform.PlatformDtos.AssignOrganisationRequest;
import com.rabbit.aip.platform.PlatformDtos.CustomerAccountRequest;
import com.rabbit.aip.platform.PlatformDtos.CustomerAccountResponse;
import com.rabbit.aip.platform.PlatformDtos.CustomerAccountStateRequest;
import com.rabbit.aip.platform.PlatformDtos.OnboardOrganisationRequest;
import com.rabbit.aip.platform.PlatformDtos.OnboardOrganisationResponse;
import com.rabbit.aip.platform.PlatformDtos.OrganisationDetailsRequest;
import com.rabbit.aip.platform.PlatformDtos.PlatformDashboardResponse;
import com.rabbit.aip.platform.PlatformDtos.PlatformOrganisationResponse;
import com.rabbit.aip.platform.PlatformDtos.PlatformOverviewResponse;
import com.rabbit.aip.platform.PlatformDtos.PlatformSettingsRequest;
import com.rabbit.aip.platform.PlatformDtos.PlatformSettingsResponse;
import com.rabbit.aip.platform.PlatformDtos.SubscriptionActionRequest;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.settings.GradeBand;
import com.rabbit.aip.settings.GradeBandRepository;
import com.rabbit.aip.settings.OrganisationSettings;
import com.rabbit.aip.settings.OrganisationSettingsRepository;
import com.rabbit.aip.user.AccountStatus;
import com.rabbit.aip.user.OrganisationMembership;
import com.rabbit.aip.user.OrganisationMembershipRepository;
import com.rabbit.aip.user.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformService {
    private final CustomerAccountRepository customerAccounts;
    private final OrganisationRepository organisations;
    private final CommercialSubscriptionRepository subscriptions;
    private final CommercialSubscriptionEventRepository subscriptionEvents;
    private final CommercialCatalogService catalog;
    private final RabbitPlatformSettingsRepository platformSettings;
    private final OrganisationMembershipRepository memberships;
    private final OrganisationSettingsRepository organisationSettings;
    private final GradeBandRepository gradeBands;
    private final InvitationService invitations;
    private final AuditService audit;
    private final CurrentSession session;
    private final Clock clock;

    public PlatformService(
            CustomerAccountRepository customerAccounts,
            OrganisationRepository organisations,
            CommercialSubscriptionRepository subscriptions,
            CommercialSubscriptionEventRepository subscriptionEvents,
            CommercialCatalogService catalog,
            RabbitPlatformSettingsRepository platformSettings,
            OrganisationMembershipRepository memberships,
            OrganisationSettingsRepository organisationSettings,
            GradeBandRepository gradeBands,
            InvitationService invitations,
            AuditService audit,
            CurrentSession session,
            Clock clock
    ) {
        this.customerAccounts = customerAccounts;
        this.organisations = organisations;
        this.subscriptions = subscriptions;
        this.subscriptionEvents = subscriptionEvents;
        this.catalog = catalog;
        this.platformSettings = platformSettings;
        this.memberships = memberships;
        this.organisationSettings = organisationSettings;
        this.gradeBands = gradeBands;
        this.invitations = invitations;
        this.audit = audit;
        this.session = session;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlatformOverviewResponse overview() {
        Instant now = clock.instant();
        List<Organisation> organisationList = organisations.findAll();
        List<CommercialSubscription> subscriptionList = subscriptions.findAll();
        RabbitPlatformSettings settings = settings();
        long capacity = subscriptionList.stream().mapToLong(CommercialSubscription::getStudentLimit).sum();
        long usage = organisationList.stream().mapToLong(value -> studentCount(value.getId())).sum();
        long trials = subscriptionList.stream().filter(value -> effectiveStatus(value, now) == SubscriptionStatus.TRIAL).count();
        long expiringSoon = subscriptionList.stream().filter(value -> {
            if (effectiveStatus(value, now) != SubscriptionStatus.TRIAL || value.getTrialEndsAt() == null) return false;
            long days = daysRemaining(value.getTrialEndsAt(), now);
            return settings.getTrialReminderDays().stream().anyMatch(reminder -> days <= reminder);
        }).count();
        long active = subscriptionList.stream().filter(value -> effectiveStatus(value, now) == SubscriptionStatus.ACTIVE).count();
        long expired = subscriptionList.stream().filter(value -> {
            SubscriptionStatus status = effectiveStatus(value, now);
            return status == SubscriptionStatus.EXPIRED || status == SubscriptionStatus.TRIAL_EXPIRED
                    || status == SubscriptionStatus.CANCELLED;
        }).count();
        PlatformDashboardResponse dashboard = new PlatformDashboardResponse(
                customerAccounts.count(), organisationList.size(), trials, expiringSoon,
                active, expired, planCount(subscriptionList, PlanCode.BASIC),
                planCount(subscriptionList, PlanCode.PRO), planCount(subscriptionList, PlanCode.LEGEND),
                capacity, usage, now
        );
        return new PlatformOverviewResponse(
                dashboard, settingsResponse(settings), catalog.catalog(),
                customerAccounts.findAllByOrderByNameAsc().stream().map(this::accountResponse).toList(),
                organisationList.stream().map(this::organisationResponse).toList()
        );
    }

    @Transactional
    public CustomerAccountResponse createCustomerAccount(CustomerAccountRequest request) {
        if (customerAccounts.existsByCodeIgnoreCase(request.code())) {
            throw conflict("CUSTOMER_ACCOUNT_CODE_EXISTS", "Customer Account code must be unique.");
        }
        CustomerAccount account = customerAccounts.save(new CustomerAccount(
                request.code(), request.name(), session.userId()
        ));
        audit.record("PLATFORM", "CUSTOMER_ACCOUNT_CREATED", "CustomerAccount", account.getId(),
                null, snapshot(account));
        return accountResponse(account);
    }

    @Transactional
    public CustomerAccountResponse updateCustomerAccount(UUID id, CustomerAccountRequest request) {
        CustomerAccount account = account(id);
        if (customerAccounts.existsByCodeIgnoreCaseAndIdNot(request.code(), id)) {
            throw conflict("CUSTOMER_ACCOUNT_CODE_EXISTS", "Customer Account code must be unique.");
        }
        String before = snapshot(account);
        try {
            account.update(request.code(), request.name(), session.userId());
        } catch (IllegalStateException exception) {
            throw DomainException.badRequest("CUSTOMER_ACCOUNT_ARCHIVED", exception.getMessage());
        }
        audit.record("PLATFORM", "CUSTOMER_ACCOUNT_UPDATED", "CustomerAccount", id,
                before, snapshot(account));
        return accountResponse(account);
    }

    @Transactional
    public CustomerAccountResponse changeCustomerAccountStatus(
            UUID id, CustomerAccountStateRequest request
    ) {
        CustomerAccount account = account(id);
        if (request.status() == CustomerAccountStatus.ARCHIVED
                && organisations.findAllByCustomerAccountIdOrderByNameAsc(id).stream()
                .anyMatch(value -> value.getStatus() != AccountStatus.ARCHIVED)) {
            throw DomainException.badRequest(
                    "CUSTOMER_ACCOUNT_HAS_ACTIVE_ORGANISATIONS",
                    "Archive every Organisation under this Customer Account first."
            );
        }
        String before = snapshot(account);
        try {
            switch (request.status()) {
                case ACTIVE -> account.activate(session.userId());
                case SUSPENDED -> account.suspend(session.userId());
                case ARCHIVED -> account.archive(session.userId(), clock.instant());
            }
        } catch (IllegalStateException exception) {
            throw DomainException.badRequest("CUSTOMER_ACCOUNT_ARCHIVED", exception.getMessage());
        }
        audit.record("PLATFORM", "CUSTOMER_ACCOUNT_STATUS_CHANGED", "CustomerAccount", id,
                before, snapshot(account) + ";reason=" + request.reason().trim());
        return accountResponse(account);
    }

    @Transactional
    public OnboardOrganisationResponse onboardOrganisation(OnboardOrganisationRequest request) {
        CustomerAccount account = activeAccount(request.customerAccountId());
        validateTimezone(request.timezone());
        if (organisations.existsByCodeIgnoreCase(request.code())) {
            throw conflict("ORGANISATION_CODE_EXISTS", "Organisation code must be unique.");
        }
        CommercialPlanPrice selectedPrice = catalog.requirePrice(
                request.selectedPlan(), request.studentCapacity()
        );
        Organisation organisation = organisations.save(new Organisation(
                account.getId(), request.code(), request.name().trim(), request.timezone().trim()
        ));
        memberships.save(new OrganisationMembership(
                organisation.getId(), session.userId(), UserRole.SUPER_ADMIN,
                AccountStatus.ACTIVE, null
        ));
        organisationSettings.save(new OrganisationSettings(
                organisation.getId(), organisation.getName(), organisation.getTimezone()
        ));
        saveDefaultGradeBands(organisation.getId());
        InvitationService.IssuedInvitation invitation = invitations.create(
                organisation.getId(), session.userId(), request.adminEmail(),
                request.adminFirstName(), request.adminLastName(), UserRole.ORG_ADMIN, null
        );
        RabbitPlatformSettings defaults = settings();
        CommercialSubscription subscription;
        SubscriptionEventType eventType;
        if (request.trialEnabled()) {
            PlanCode trialPlan = request.trialPlan() == null
                    ? defaults.getDefaultTrialPlan() : request.trialPlan();
            int trialDays = request.trialDurationDays() == null
                    ? defaults.getDefaultTrialDays() : request.trialDurationDays();
            CommercialPlanPrice trialPrice = catalog.requirePrice(trialPlan, request.studentCapacity());
            subscription = CommercialSubscription.startTrial(
                    organisation.getId(), request.selectedPlan(), trialPlan,
                    request.studentCapacity(), trialPrice.getMonthlyPricePaise(), trialDays,
                    request.activationDate(), session.userId(), request.note()
            );
            eventType = SubscriptionEventType.TRIAL_STARTED;
        } else {
            subscription = CommercialSubscription.createPending(
                    organisation.getId(), request.selectedPlan(), request.studentCapacity(),
                    selectedPrice.getMonthlyPricePaise(), request.activationDate(),
                    session.userId(), request.note()
            );
            eventType = SubscriptionEventType.SUBSCRIPTION_CREATED;
        }
        subscriptions.save(subscription);
        recordEvent(subscription, eventType, null, request.note());
        audit.recordForOrganisation(
                organisation.getId(), "PLATFORM", "ORGANISATION_CREATED", "Organisation",
                organisation.getId(), null,
                "customerAccount=" + account.getId() + ";plan=" + request.selectedPlan()
        );
        return new OnboardOrganisationResponse(
                organisationResponse(organisation), invitation.user().getId(),
                invitation.user().getEmail(), invitation.activationUrl(), invitation.expiresAt(),
                response(subscription)
        );
    }

    @Transactional
    public PlatformOrganisationResponse assignOrganisation(
            UUID organisationId, AssignOrganisationRequest request
    ) {
        Organisation organisation = organisation(organisationId);
        CustomerAccount target = activeAccount(request.customerAccountId());
        UUID before = organisation.getCustomerAccountId();
        if (before.equals(target.getId())) return organisationResponse(organisation);
        organisation.assignCustomerAccount(target.getId());
        audit.recordForOrganisation(
                organisationId, "PLATFORM", "ORGANISATION_CUSTOMER_ACCOUNT_ASSIGNED",
                "Organisation", organisationId, before.toString(),
                target.getId() + ";reason=" + request.reason().trim()
        );
        return organisationResponse(organisation);
    }

    @Transactional
    public PlatformOrganisationResponse updateOrganisation(
            UUID organisationId, OrganisationDetailsRequest request
    ) {
        validateTimezone(request.timezone());
        Organisation organisation = organisation(organisationId);
        String before = "name=" + organisation.getName() + ";timezone=" + organisation.getTimezone();
        organisation.update(request.name(), request.timezone());
        audit.recordForOrganisation(
                organisationId, "PLATFORM", "ORGANISATION_UPDATED", "Organisation",
                organisationId, before,
                "name=" + organisation.getName() + ";timezone=" + organisation.getTimezone()
        );
        return organisationResponse(organisation);
    }

    @Transactional
    public SubscriptionResponse subscriptionAction(
            UUID organisationId, SubscriptionActionRequest request
    ) {
        Organisation organisation = organisation(organisationId);
        activeAccount(organisation.getCustomerAccountId());
        CommercialSubscription subscription = subscriptions.findByOrganisationId(organisationId)
                .orElseThrow(() -> DomainException.notFound(
                        "SUBSCRIPTION_NOT_FOUND", "No subscription exists for this Organisation."
                ));
        String before = snapshot(subscription);
        SubscriptionEventType event;
        Instant now = clock.instant();
        try {
            event = switch (request.action()) {
                case "EXTEND_TRIAL" -> subscription.extendTrial(
                        required(request.extensionDays(), "extensionDays"),
                        session.userId(), request.reason()
                );
                case "SUSPEND" -> subscription.suspend(session.userId(), request.reason());
                case "REACTIVATE" -> subscription.restore(now, session.userId(), request.reason());
                case "GRACE_PERIOD" -> subscription.startGracePeriod(
                        required(request.endsAt(), "endsAt"), session.userId(), request.reason()
                );
                case "CANCEL" -> subscription.cancel(session.userId(), request.reason());
                case "PAYMENT_STATUS" -> subscription.changePaymentStatus(
                        required(request.paymentStatus(), "paymentStatus"), request.amountPaise(),
                        request.paymentReference(), request.remarks(), session.userId()
                );
                case "UPGRADE", "DOWNGRADE", "RENEW", "ACTIVATE" -> activate(
                        organisationId, subscription, request, now
                );
                default -> throw DomainException.badRequest(
                        "SUBSCRIPTION_ACTION_INVALID", "Unsupported subscription action."
                );
            };
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw DomainException.badRequest("SUBSCRIPTION_TRANSITION_INVALID", exception.getMessage());
        }
        recordEvent(subscription, event, before, request.reason());
        return response(subscription);
    }

    @Transactional
    public PlatformSettingsResponse updateSettings(PlatformSettingsRequest request) {
        if (request.reminderDays().stream().distinct().count() != request.reminderDays().size()) {
            throw DomainException.badRequest("REMINDER_DAYS_DUPLICATE", "Reminder days must be unique.");
        }
        catalog.requirePrice(request.defaultTrialPlan(), catalog.catalog().stream()
                .filter(value -> value.code() == request.defaultTrialPlan())
                .flatMap(value -> value.prices().stream()).findFirst()
                .orElseThrow().studentLimit());
        RabbitPlatformSettings settings = settings();
        String before = settingsResponse(settings).toString();
        settings.update(request.defaultTrialDays(), request.defaultTrialPlan(),
                request.reminderDays().stream().sorted(java.util.Comparator.reverseOrder()).toList(),
                session.userId());
        audit.record("PLATFORM", "PLATFORM_SETTINGS_UPDATED", "RabbitPlatformSettings",
                settings.getId(), before, settingsResponse(settings).toString());
        return settingsResponse(settings);
    }

    private SubscriptionEventType activate(
            UUID organisationId,
            CommercialSubscription subscription,
            SubscriptionActionRequest request,
            Instant now
    ) {
        PlanCode plan = required(request.plan(), "plan");
        if (request.action().equals("UPGRADE") && plan.rank() <= subscription.getPlan().rank()) {
            throw new IllegalArgumentException("An upgrade must select a higher Rabbit plan.");
        }
        if (request.action().equals("DOWNGRADE") && plan.rank() >= subscription.getPlan().rank()) {
            throw new IllegalArgumentException("A downgrade must select a lower Rabbit plan.");
        }
        int capacity = required(request.studentCapacity(), "studentCapacity");
        requireCapacity(organisationId, capacity);
        CommercialPlanPrice price = catalog.requirePrice(plan, capacity);
        Instant startsAt = required(request.startsAt(), "startsAt");
        Instant endsAt = required(request.endsAt(), "endsAt");
        if (!startsAt.isBefore(endsAt)) throw new IllegalArgumentException("Start must precede end.");
        if (startsAt.isAfter(now)) {
            return subscription.applyPaidPlan(
                    plan, capacity, price.getMonthlyPricePaise(), startsAt, endsAt,
                    null, session.userId(), request.reason(), now
            );
        }
        SubscriptionEventType event = subscription.manualActivate(
                plan, capacity, price.getMonthlyPricePaise(), startsAt, endsAt,
                request.paymentStatus() == null ? ManualPaymentStatus.PENDING : request.paymentStatus(),
                request.amountPaise(), request.paymentReference(), request.remarks(),
                session.userId(), request.reason()
        );
        return request.action().equals("RENEW")
                ? SubscriptionEventType.SUBSCRIPTION_RENEWED : event;
    }

    private void recordEvent(
            CommercialSubscription subscription,
            SubscriptionEventType eventType,
            String before,
            String reason
    ) {
        subscriptions.save(subscription);
        Instant now = clock.instant();
        String after = snapshot(subscription) + ";reason=" + normalized(reason);
        subscriptionEvents.save(new CommercialSubscriptionEvent(
                subscription.getOrganisationId(), subscription.getId(), eventType,
                before, after, session.userId(), now
        ));
        audit.recordForOrganisation(
                subscription.getOrganisationId(), "COM", eventType.name(),
                "CommercialSubscription", subscription.getId(), before, after
        );
    }

    private CustomerAccountResponse accountResponse(CustomerAccount account) {
        List<Organisation> accountOrganisations = organisations
                .findAllByCustomerAccountIdOrderByNameAsc(account.getId());
        List<CommercialSubscription> accountSubscriptions = accountOrganisations.stream()
                .map(value -> subscriptions.findByOrganisationId(value.getId()).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
        return new CustomerAccountResponse(
                account.getId(), account.getCode(), account.getName(), account.getStatus(),
                account.getArchivedAt(), accountOrganisations.size(),
                accountOrganisations.stream().mapToLong(value -> studentCount(value.getId())).sum(),
                accountSubscriptions.stream().mapToLong(CommercialSubscription::getStudentLimit).sum(),
                accountSubscriptions.stream().filter(value -> effectiveStatus(value, clock.instant()) == SubscriptionStatus.TRIAL).count(),
                accountSubscriptions.stream().filter(value -> effectiveStatus(value, clock.instant()) == SubscriptionStatus.ACTIVE).count()
        );
    }

    private PlatformOrganisationResponse organisationResponse(Organisation organisation) {
        CommercialSubscription subscription = subscriptions
                .findByOrganisationId(organisation.getId()).orElse(null);
        Instant endsAt = subscription == null ? null
                : subscription.getGraceEndsAt() != null ? subscription.getGraceEndsAt()
                : subscription.getPeriodEndsAt() != null ? subscription.getPeriodEndsAt()
                : subscription.getTrialEndsAt();
        return new PlatformOrganisationResponse(
                organisation.getId(), organisation.getCustomerAccountId(), organisation.getCode(),
                organisation.getName(), organisation.getTimezone(), organisation.getStatus(),
                organisation.hasLogo(), organisation.getLogoUpdatedAt(),
                subscription == null ? null : subscription.getPlan(),
                subscription == null ? null : subscription.getSelectedPlan(),
                subscription == null ? null : effectiveStatus(subscription, clock.instant()),
                subscription == null ? 0 : subscription.getStudentLimit(),
                studentCount(organisation.getId()), endsAt
        );
    }

    private SubscriptionResponse response(CommercialSubscription subscription) {
        return SubscriptionResponse.from(subscription, catalog.entitlements(subscription.getPlan()));
    }

    private RabbitPlatformSettings settings() {
        return platformSettings.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Rabbit platform settings are missing."));
    }

    private PlatformSettingsResponse settingsResponse(RabbitPlatformSettings value) {
        return new PlatformSettingsResponse(
                value.getDefaultTrialDays(), value.getDefaultTrialPlan(), value.getTrialReminderDays()
        );
    }

    private CustomerAccount account(UUID id) {
        return customerAccounts.findById(id).orElseThrow(() -> DomainException.notFound(
                "CUSTOMER_ACCOUNT_NOT_FOUND", "Customer Account was not found."
        ));
    }

    private CustomerAccount activeAccount(UUID id) {
        CustomerAccount account = account(id);
        if (account.getStatus() != CustomerAccountStatus.ACTIVE) {
            throw DomainException.badRequest(
                    "CUSTOMER_ACCOUNT_INACTIVE", "Customer Account must be active."
            );
        }
        return account;
    }

    private Organisation organisation(UUID id) {
        return organisations.findById(id).orElseThrow(() -> DomainException.notFound(
                "ORGANISATION_NOT_FOUND", "Organisation was not found."
        ));
    }

    private long studentCount(UUID organisationId) {
        return memberships.findAllByOrganisationIdOrderByCreatedAtDesc(organisationId).stream()
                .filter(value -> value.getRole() == UserRole.STUDENT)
                .filter(value -> value.getStatus() == AccountStatus.ACTIVE
                        || value.getStatus() == AccountStatus.INVITED).count();
    }

    private void requireCapacity(UUID organisationId, int capacity) {
        long used = studentCount(organisationId);
        if (capacity < used) throw DomainException.badRequest(
                "PLAN_CAPACITY_BELOW_USAGE",
                "The capacity is below the Organisation's " + used + " active or invited Students."
        );
    }

    private SubscriptionStatus effectiveStatus(CommercialSubscription value, Instant now) {
        if (value.getStatus() == SubscriptionStatus.TRIAL
                && value.getTrialEndsAt() != null && !value.getTrialEndsAt().isAfter(now)) {
            return SubscriptionStatus.TRIAL_EXPIRED;
        }
        if (value.getStatus() == SubscriptionStatus.ACTIVE
                && value.getPeriodEndsAt() != null && !value.getPeriodEndsAt().isAfter(now)) {
            return SubscriptionStatus.EXPIRED;
        }
        if (value.getStatus() == SubscriptionStatus.GRACE_PERIOD
                && value.getGraceEndsAt() != null && !value.getGraceEndsAt().isAfter(now)) {
            return SubscriptionStatus.EXPIRED;
        }
        return value.getStatus();
    }

    private long planCount(List<CommercialSubscription> values, PlanCode plan) {
        return values.stream().filter(value -> value.getPlan() == plan).count();
    }

    private long daysRemaining(Instant end, Instant now) {
        return Math.max(0, (long) Math.ceil(Duration.between(now, end).toSeconds() / 86_400.0));
    }

    private String snapshot(CustomerAccount value) {
        return "code=" + value.getCode() + ";name=" + value.getName()
                + ";status=" + value.getStatus();
    }

    private String snapshot(CommercialSubscription value) {
        return "status=" + value.getStatus() + ";plan=" + value.getPlan()
                + ";selectedPlan=" + value.getSelectedPlan()
                + ";studentLimit=" + value.getStudentLimit()
                + ";trialEndsAt=" + value.getTrialEndsAt()
                + ";periodStartsAt=" + value.getPeriodStartsAt()
                + ";periodEndsAt=" + value.getPeriodEndsAt()
                + ";graceEndsAt=" + value.getGraceEndsAt()
                + ";paymentStatus=" + value.getPaymentStatus();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone.trim());
        } catch (DateTimeException exception) {
            throw DomainException.badRequest(
                    "TIMEZONE_INVALID", "Use a valid IANA time zone such as Asia/Kolkata."
            );
        }
    }

    private void saveDefaultGradeBands(UUID organisationId) {
        gradeBands.saveAll(List.of(
                new GradeBand(organisationId, "A", "Excellent", BigDecimal.valueOf(80), BigDecimal.valueOf(100), 1),
                new GradeBand(organisationId, "B", "Very Good", BigDecimal.valueOf(65), BigDecimal.valueOf(79.99), 2),
                new GradeBand(organisationId, "C", "Good", BigDecimal.valueOf(50), BigDecimal.valueOf(64.99), 3),
                new GradeBand(organisationId, "D", "Developing", BigDecimal.valueOf(40), BigDecimal.valueOf(49.99), 4),
                new GradeBand(organisationId, "F", "Needs Support", BigDecimal.ZERO, BigDecimal.valueOf(39.99), 5)
        ));
    }

    private <T> T required(T value, String field) {
        if (value == null) throw DomainException.badRequest(
                "SUBSCRIPTION_FIELD_REQUIRED", field + " is required for this action."
        );
        return value;
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }
}
