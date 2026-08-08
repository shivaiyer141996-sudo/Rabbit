package com.rabbit.aip.commercial;

import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.auth.InvitationService;
import com.rabbit.aip.commercial.CommercialDtos.CommercialOverviewResponse;
import com.rabbit.aip.commercial.CommercialDtos.CommercialAccessResponse;
import com.rabbit.aip.commercial.CommercialDtos.CreateInvoiceRequest;
import com.rabbit.aip.commercial.CommercialDtos.CreateSupportCaseRequest;
import com.rabbit.aip.commercial.CommercialDtos.InvoiceResponse;
import com.rabbit.aip.commercial.CommercialDtos.OnboardOrganisationRequest;
import com.rabbit.aip.commercial.CommercialDtos.OnboardOrganisationResponse;
import com.rabbit.aip.commercial.CommercialDtos.PaymentReceiptResponse;
import com.rabbit.aip.commercial.CommercialDtos.PaymentResponse;
import com.rabbit.aip.commercial.CommercialDtos.PlanCatalogResponse;
import com.rabbit.aip.commercial.CommercialDtos.PricePoint;
import com.rabbit.aip.commercial.CommercialDtos.ReceiptResponse;
import com.rabbit.aip.commercial.CommercialDtos.RecordPaymentRequest;
import com.rabbit.aip.commercial.CommercialDtos.StartTrialRequest;
import com.rabbit.aip.commercial.CommercialDtos.SubscriptionEventResponse;
import com.rabbit.aip.commercial.CommercialDtos.SubscriptionResponse;
import com.rabbit.aip.commercial.CommercialDtos.SupportCaseResponse;
import com.rabbit.aip.commercial.CommercialDtos.UpdateSupportCaseRequest;
import com.rabbit.aip.commercial.CommercialTypes.Entitlement;
import com.rabbit.aip.commercial.CommercialTypes.InvoiceStatus;
import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import com.rabbit.aip.commercial.CommercialTypes.SubscriptionEventType;
import com.rabbit.aip.commercial.CommercialTypes.SubscriptionStatus;
import com.rabbit.aip.commercial.CommercialTypes.SupportStatus;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.organisation.Organisation;
import com.rabbit.aip.organisation.OrganisationRepository;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.settings.GradeBand;
import com.rabbit.aip.settings.GradeBandRepository;
import com.rabbit.aip.settings.OrganisationSettings;
import com.rabbit.aip.settings.OrganisationSettingsRepository;
import com.rabbit.aip.user.AccountStatus;
import com.rabbit.aip.user.OrganisationMembership;
import com.rabbit.aip.user.OrganisationMembershipRepository;
import com.rabbit.aip.user.UserRole;
import com.rabbit.aip.platform.RabbitPlatformSettings;
import com.rabbit.aip.platform.RabbitPlatformSettingsRepository;
import com.rabbit.aip.platform.CustomerAccount;
import com.rabbit.aip.platform.CustomerAccountRepository;
import com.rabbit.aip.platform.CustomerAccountStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommercialService {

    private static final Duration MAXIMUM_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Duration MINIMUM_MONTH = Duration.ofDays(27);
    private static final Duration MAXIMUM_MONTH = Duration.ofDays(32);

    private final CommercialSubscriptionRepository subscriptions;
    private final CommercialSubscriptionEventRepository subscriptionEvents;
    private final CommercialInvoiceRepository invoices;
    private final CommercialPaymentRepository payments;
    private final CommercialReceiptRepository receipts;
    private final CommercialSupportCaseRepository supportCases;
    private final OrganisationRepository organisations;
    private final OrganisationMembershipRepository memberships;
    private final OrganisationSettingsRepository settings;
    private final GradeBandRepository gradeBands;
    private final InvitationService invitations;
    private final CurrentSession session;
    private final AuditService audit;
    private final CommercialLaunchGuard launchGuard;
    private final CommercialCatalogService catalogService;
    private final RabbitPlatformSettingsRepository platformSettings;
    private final CustomerAccountRepository customerAccounts;
    private final Clock clock;

    public CommercialService(
            CommercialSubscriptionRepository subscriptions,
            CommercialSubscriptionEventRepository subscriptionEvents,
            CommercialInvoiceRepository invoices,
            CommercialPaymentRepository payments,
            CommercialReceiptRepository receipts,
            CommercialSupportCaseRepository supportCases,
            OrganisationRepository organisations,
            OrganisationMembershipRepository memberships,
            OrganisationSettingsRepository settings,
            GradeBandRepository gradeBands,
            InvitationService invitations,
            CurrentSession session,
            AuditService audit,
            CommercialLaunchGuard launchGuard,
            CommercialCatalogService catalogService,
            RabbitPlatformSettingsRepository platformSettings,
            CustomerAccountRepository customerAccounts,
            Clock clock
    ) {
        this.subscriptions = subscriptions;
        this.subscriptionEvents = subscriptionEvents;
        this.invoices = invoices;
        this.payments = payments;
        this.receipts = receipts;
        this.supportCases = supportCases;
        this.organisations = organisations;
        this.memberships = memberships;
        this.settings = settings;
        this.gradeBands = gradeBands;
        this.invitations = invitations;
        this.session = session;
        this.audit = audit;
        this.launchGuard = launchGuard;
        this.catalogService = catalogService;
        this.platformSettings = platformSettings;
        this.customerAccounts = customerAccounts;
        this.clock = clock;
    }

    public List<PlanCatalogResponse> catalog() {
        return catalogService.catalog();
    }

    @Transactional
    public CommercialAccessResponse access() {
        CommercialSubscription subscription = findAndRefresh(
                session.organisationId(), session.userId(), clock.instant()
        );
        Instant accessEndsAt = subscription == null
                ? null
                : subscription.getGraceEndsAt() != null
                    ? subscription.getGraceEndsAt()
                    : subscription.getPeriodEndsAt() != null
                    ? subscription.getPeriodEndsAt()
                    : subscription.getTrialEndsAt();
        long daysRemaining = accessEndsAt == null ? 0
                : Math.max(0, (long) Math.ceil(
                        Duration.between(clock.instant(), accessEndsAt).toSeconds() / 86_400.0
                ));
        return new CommercialAccessResponse(
                launchGuard.enabled(),
                subscription == null ? null : subscription.getPlan(),
                subscription == null ? null : subscription.getStatus(),
                subscription == null ? null : subscription.getStudentLimit(),
                accessEndsAt,
                daysRemaining,
                effectiveEntitlements(subscription)
        );
    }

    @Transactional
    public CommercialOverviewResponse overview() {
        UUID organisationId = session.organisationId();
        Instant now = clock.instant();
        CommercialSubscription subscription = findAndRefresh(
                organisationId, session.userId(), now
        );
        int students = studentCount(organisationId);
        Set<Entitlement> effective = effectiveEntitlements(subscription);
        int admissionLimit = subscription == null ? 0 : admissionLimit(subscription);
        int availableSlots = subscription == null
                ? 0
                : Math.max(0, admissionLimit - students);
        return new CommercialOverviewResponse(
                launchGuard.enabled(),
                launchGuard.enabled(),
                defaultPlatformSettings().getDefaultTrialDays(),
                now,
                students,
                availableSlots,
                effective,
                subscription == null ? null : subscriptionResponse(subscription),
                catalog(),
                subscriptionEvents.findAllByOrganisationIdOrderByOccurredAtDesc(
                                organisationId
                        ).stream()
                        .map(SubscriptionEventResponse::from)
                        .toList(),
                invoices.findAllByOrganisationIdOrderByIssuedAtDesc(organisationId)
                        .stream().map(InvoiceResponse::from).toList(),
                payments.findAllByOrganisationIdOrderByPaidAtDesc(organisationId)
                        .stream().map(PaymentResponse::from).toList(),
                receipts.findAllByOrganisationIdOrderByIssuedAtDesc(organisationId)
                        .stream().map(ReceiptResponse::from).toList(),
                supportCases.findAllByOrganisationIdOrderByCreatedAtDesc(organisationId)
                        .stream().map(SupportCaseResponse::from).toList()
        );
    }

    @Transactional
    public SubscriptionResponse startTrial(StartTrialRequest request) {
        launchGuard.requireEnabled();
        return subscriptionResponse(startTrialFor(
                session.organisationId(),
                request.declaredStudents(),
                request.note(),
                session.userId(),
                clock.instant()
        ));
    }

    @Transactional
    public OnboardOrganisationResponse onboard(OnboardOrganisationRequest request) {
        launchGuard.requireEnabled();
        String timezone = request.timezone().trim();
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw DomainException.badRequest(
                    "TIMEZONE_INVALID", "Use a valid IANA time zone such as Asia/Kolkata."
            );
        }
        if (organisations.existsByCodeIgnoreCase(request.code())) {
            throw new DomainException(
                    "ORGANISATION_CODE_EXISTS",
                    "Organisation code must be unique.",
                    HttpStatus.CONFLICT
            );
        }
        CustomerAccount customerAccount = customerAccounts.save(new CustomerAccount(
                "CA-" + request.code(), request.name().trim() + " Account", session.userId()
        ));
        Organisation organisation = organisations.save(new Organisation(
                customerAccount.getId(), request.code(), request.name().trim(), timezone
        ));
        memberships.save(new OrganisationMembership(
                organisation.getId(),
                session.userId(),
                UserRole.SUPER_ADMIN,
                AccountStatus.ACTIVE,
                null
        ));
        settings.save(new OrganisationSettings(
                organisation.getId(), organisation.getName(), organisation.getTimezone()
        ));
        saveDefaultGradeBands(organisation.getId());
        InvitationService.IssuedInvitation invitation = invitations.create(
                organisation.getId(),
                session.userId(),
                request.adminEmail(),
                request.adminFirstName(),
                request.adminLastName(),
                UserRole.ORG_ADMIN,
                null
        );
        CommercialSubscription subscription = startTrialFor(
                organisation.getId(),
                request.declaredStudents(),
                request.note(),
                session.userId(),
                clock.instant()
        );
        audit.recordForOrganisation(
                organisation.getId(),
                "COM",
                "ORGANISATION_ONBOARDED",
                "Organisation",
                organisation.getId(),
                null,
                organisation.getCode() + ":20-day Legend trial"
        );
        return new OnboardOrganisationResponse(
                organisation.getId(),
                organisation.getCode(),
                organisation.getName(),
                invitation.user().getId(),
                invitation.user().getEmail(),
                invitation.activationUrl(),
                invitation.expiresAt(),
                subscriptionResponse(subscription)
        );
    }

    @Transactional
    public InvoiceResponse createInvoice(CreateInvoiceRequest request) {
        launchGuard.requireEnabled();
        UUID organisationId = session.organisationId();
        if (invoices.existsByOrganisationIdAndInvoiceNumberIgnoreCase(
                organisationId, request.invoiceNumber().trim()
        )) {
            throw new DomainException(
                    "INVOICE_NUMBER_EXISTS",
                    "Invoice number already exists for this organisation.",
                    HttpStatus.CONFLICT
            );
        }
        long monthlyPrice = validatedPrice(request.plan(), request.studentLimit());
        requireCapacityCoversCurrentStudents(
                organisationId, request.studentLimit()
        );
        validateInvoiceDates(request, monthlyPrice);
        CommercialInvoice invoice = invoices.save(new CommercialInvoice(
                organisationId,
                request.invoiceNumber(),
                request.plan(),
                request.studentLimit(),
                monthlyPrice,
                request.periodStartsAt(),
                request.periodEndsAt(),
                request.taxPaise(),
                request.issuedAt(),
                request.dueAt(),
                request.note(),
                session.userId()
        ));
        audit.record(
                "COM", "INVOICE_ISSUED", "CommercialInvoice", invoice.getId(),
                null, invoice.getInvoiceNumber() + ":" + invoice.getTotalPaise()
        );
        return InvoiceResponse.from(invoice);
    }

    @Transactional
    public InvoiceResponse voidInvoice(UUID invoiceId, String reason) {
        launchGuard.requireEnabled();
        CommercialInvoice invoice = findInvoice(invoiceId);
        try {
            invoice.voidInvoice(session.userId());
        } catch (IllegalStateException exception) {
            throw DomainException.badRequest("INVOICE_NOT_VOIDABLE", exception.getMessage());
        }
        audit.record(
                "COM", "INVOICE_VOIDED", "CommercialInvoice", invoice.getId(),
                "ISSUED", "VOID:" + reason.trim()
        );
        return InvoiceResponse.from(invoice);
    }

    @Transactional
    public SubscriptionResponse suspendSubscription(String reason) {
        launchGuard.requireEnabled();
        Instant now = clock.instant();
        CommercialSubscription subscription = findAndRefresh(
                session.organisationId(), session.userId(), now
        );
        if (subscription == null) {
            throw DomainException.notFound(
                    "SUBSCRIPTION_NOT_FOUND", "No subscription exists for this organisation."
            );
        }
        String before = snapshot(subscription);
        try {
            SubscriptionEventType transition = subscription.suspend(
                    session.userId(), reason
            );
            recordSubscriptionEvent(subscription, transition, before, now);
        } catch (IllegalStateException exception) {
            throw DomainException.badRequest(
                    "SUBSCRIPTION_NOT_SUSPENDABLE", exception.getMessage()
            );
        }
        return subscriptionResponse(subscription);
    }

    @Transactional
    public SubscriptionResponse restoreSubscription(String reason) {
        launchGuard.requireEnabled();
        Instant now = clock.instant();
        CommercialSubscription subscription = subscriptions
                .findByOrganisationId(session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "SUBSCRIPTION_NOT_FOUND",
                        "No subscription exists for this organisation."
                ));
        String before = snapshot(subscription);
        try {
            SubscriptionEventType transition = subscription.restore(
                    now, session.userId(), reason
            );
            recordSubscriptionEvent(subscription, transition, before, now);
        } catch (IllegalStateException exception) {
            throw DomainException.badRequest(
                    "SUBSCRIPTION_NOT_RESTORABLE", exception.getMessage()
            );
        }
        return subscriptionResponse(subscription);
    }

    @Transactional
    public PaymentReceiptResponse recordPayment(RecordPaymentRequest request) {
        launchGuard.requireEnabled();
        UUID organisationId = session.organisationId();
        CommercialInvoice invoice = findInvoice(request.invoiceId());
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw DomainException.badRequest(
                    "INVOICE_NOT_PAYABLE", "Only an unpaid issued invoice can receive a payment."
            );
        }
        if (request.amountPaise() != invoice.getTotalPaise()) {
            throw DomainException.badRequest(
                    "PAYMENT_AMOUNT_MISMATCH",
                    "Payment amount must exactly match the invoice total."
            );
        }
        requireCapacityCoversCurrentStudents(
                organisationId, invoice.getStudentLimit()
        );
        if (payments.existsByOrganisationIdAndPaymentReferenceIgnoreCase(
                organisationId, request.paymentReference().trim()
        )) {
            throw new DomainException(
                    "PAYMENT_REFERENCE_EXISTS",
                    "Payment reference already exists for this organisation.",
                    HttpStatus.CONFLICT
            );
        }
        Instant now = clock.instant();
        if (request.paidAt().isAfter(now.plus(MAXIMUM_CLOCK_SKEW))
                || request.paidAt().isBefore(invoice.getIssuedAt())) {
            throw DomainException.badRequest(
                    "PAYMENT_DATE_INVALID",
                    "Payment time must be after invoice issue and cannot be in the future."
            );
        }
        validatePaidPlanChange(invoice, now);

        CommercialPayment payment = new CommercialPayment(
                organisationId,
                invoice.getId(),
                request.paymentReference(),
                request.paymentMethod(),
                request.amountPaise(),
                request.paidAt(),
                session.userId(),
                request.note()
        );
        payments.saveAndFlush(payment);
        invoice.markPaid(request.paidAt(), session.userId());
        invoices.flush();

        CommercialReceipt receipt = receipts.save(new CommercialReceipt(
                organisationId,
                payment.getId(),
                invoice.getId(),
                receiptNumber(payment.getId()),
                payment.getAmountPaise(),
                now,
                session.userId()
        ));

        CommercialSubscription subscription = subscriptions
                .findByOrganisationId(organisationId)
                .orElse(null);
        SubscriptionEventType transition;
        String before = subscription == null ? null : snapshot(subscription);
        if (subscription == null) {
            if (invoice.getPeriodStartsAt().isAfter(now)
                    || !invoice.getPeriodEndsAt().isAfter(now)) {
                throw DomainException.badRequest(
                        "PAID_PLAN_START_INVALID",
                        "A first paid plan must cover the current time."
                );
            }
            subscription = subscriptions.save(CommercialSubscription.startPaid(
                    organisationId,
                    invoice.getPlan(),
                    invoice.getStudentLimit(),
                    invoice.getSubtotalPaise(),
                    invoice.getPeriodStartsAt(),
                    invoice.getPeriodEndsAt(),
                    invoice.getId(),
                    session.userId(),
                    request.note(),
                    com.rabbit.aip.commercial.CommercialTypes.ManualPaymentStatus.PAID,
                    request.paymentReference(),
                    request.amountPaise()
            ));
            transition = SubscriptionEventType.PLAN_ACTIVATED;
        } else {
            try {
                transition = subscription.applyPaidPlan(
                        invoice.getPlan(),
                        invoice.getStudentLimit(),
                        invoice.getSubtotalPaise(),
                        invoice.getPeriodStartsAt(),
                        invoice.getPeriodEndsAt(),
                        invoice.getId(),
                        session.userId(),
                        request.note(),
                        now
                );
            } catch (IllegalArgumentException exception) {
                throw DomainException.badRequest(
                        "PAID_PLAN_START_INVALID", exception.getMessage()
                );
            }
        }
        recordSubscriptionEvent(subscription, transition, before, now);
        audit.record(
                "COM", "PAYMENT_RECORDED", "CommercialPayment", payment.getId(),
                null, payment.getPaymentReference() + ":" + payment.getAmountPaise()
        );
        audit.record(
                "COM", "RECEIPT_ISSUED", "CommercialReceipt", receipt.getId(),
                null, receipt.getReceiptNumber()
        );
        return new PaymentReceiptResponse(
                PaymentResponse.from(payment),
                ReceiptResponse.from(receipt),
                subscriptionResponse(subscription)
        );
    }

    @Transactional
    public SupportCaseResponse createSupportCase(CreateSupportCaseRequest request) {
        launchGuard.requireEnabled();
        Instant now = clock.instant();
        CommercialSupportCase supportCase = supportCases.save(new CommercialSupportCase(
                session.organisationId(),
                supportCaseNumber(),
                request.severity(),
                request.category(),
                request.summary(),
                request.description(),
                session.userId(),
                now.plus(CommercialTypes.firstResponseTarget(request.severity())),
                session.userId()
        ));
        audit.record(
                "SUP", "CASE_CREATED", "CommercialSupportCase", supportCase.getId(),
                null, supportCase.getCaseNumber() + ":" + supportCase.getSeverity()
        );
        return SupportCaseResponse.from(supportCase);
    }

    @Transactional
    public SupportCaseResponse updateSupportCase(
            UUID caseId,
            UpdateSupportCaseRequest request
    ) {
        launchGuard.requireEnabled();
        CommercialSupportCase supportCase = supportCases
                .findByIdAndOrganisationId(caseId, session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "SUPPORT_CASE_NOT_FOUND", "Support case was not found."
                ));
        SupportStatus before = supportCase.getStatus();
        try {
            supportCase.update(
                    request.status(), request.assignedTo(), request.resolution(),
                    session.userId(), clock.instant()
            );
        } catch (IllegalArgumentException exception) {
            throw DomainException.badRequest(
                    "SUPPORT_RESOLUTION_REQUIRED", exception.getMessage()
            );
        }
        audit.record(
                "SUP", "CASE_UPDATED", "CommercialSupportCase", supportCase.getId(),
                before.name(), supportCase.getStatus().name()
        );
        return SupportCaseResponse.from(supportCase);
    }

    @Transactional
    public void requireEntitlement(Entitlement entitlement) {
        if (!launchGuard.enabled()) return;
        CommercialSubscription subscription = requireActiveSubscription();
        if (!catalogService.entitlements(subscription.getPlan()).contains(entitlement)) {
            String requiredPlan = catalogService.catalog().stream()
                    .filter(value -> value.entitlements().contains(entitlement))
                    .map(PlanCatalogResponse::label)
                    .findFirst().orElse("a higher Rabbit plan");
            throw new DomainException(
                    "PLAN_ENTITLEMENT_REQUIRED",
                    "Available on " + requiredPlan + ". "
                            + entitlement.name().replace('_', ' ')
                            + " is not included in the Organisation's current plan.",
                    HttpStatus.PAYMENT_REQUIRED
            );
        }
    }

    @Transactional
    public void requireStudentCapacity(UserRole invitedRole) {
        if (!launchGuard.enabled() || invitedRole != UserRole.STUDENT) return;
        CommercialSubscription subscription = requireActiveSubscription();
        int used = studentCount(session.organisationId());
        int admissionLimit = admissionLimit(subscription);
        if (used >= admissionLimit) {
            throw new DomainException(
                    "STUDENT_LIMIT_REACHED",
                    "The current or scheduled plan permits " + admissionLimit
                            + " active or invited students. Upgrade the capacity before inviting another student.",
                    HttpStatus.PAYMENT_REQUIRED
            );
        }
    }

    private CommercialSubscription requireActiveSubscription() {
        if (!customerAccountActive(session.organisationId())) {
            throw new DomainException(
                    "CUSTOMER_ACCOUNT_INACTIVE",
                    "The Organisation's Customer Account is suspended or archived.",
                    HttpStatus.PAYMENT_REQUIRED
            );
        }
        CommercialSubscription subscription = findAndRefresh(
                session.organisationId(), session.userId(), clock.instant()
        );
        if (subscription == null
                || (subscription.getStatus() != SubscriptionStatus.TRIAL
                    && subscription.getStatus() != SubscriptionStatus.ACTIVE
                    && subscription.getStatus() != SubscriptionStatus.GRACE_PERIOD)) {
            throw new DomainException(
                    "SUBSCRIPTION_INACTIVE",
                    "The organisation needs an active trial or paid subscription to continue.",
                    HttpStatus.PAYMENT_REQUIRED
            );
        }
        return subscription;
    }

    private CommercialSubscription startTrialFor(
            UUID organisationId,
            int declaredStudents,
            String note,
            UUID actorUserId,
            Instant now
    ) {
        if (subscriptions.findByOrganisationId(organisationId).isPresent()) {
            throw DomainException.badRequest(
                    "TRIAL_ALREADY_USED",
                    "Each organisation can receive the 20-day Legend trial only once."
            );
        }
        int currentStudents = studentCount(organisationId);
        if (declaredStudents < currentStudents) {
            throw DomainException.badRequest(
                    "DECLARED_STUDENT_COUNT_TOO_LOW",
                    "Declared students cannot be lower than the organisation's "
                            + currentStudents + " active or invited Students."
            );
        }
        RabbitPlatformSettings defaults = defaultPlatformSettings();
        PlanCode trialPlan = defaults.getDefaultTrialPlan();
        int studentLimit = catalogService.capacityFor(trialPlan, declaredStudents);
        CommercialPlanPrice price = catalogService.requirePrice(trialPlan, studentLimit);
        CommercialSubscription subscription = subscriptions.save(
                CommercialSubscription.startTrial(
                        organisationId, trialPlan, trialPlan, studentLimit,
                        price.getMonthlyPricePaise(), defaults.getDefaultTrialDays(),
                        now, actorUserId, note
                )
        );
        recordSubscriptionEvent(
                subscription, SubscriptionEventType.TRIAL_STARTED, null, now
        );
        return subscription;
    }

    private CommercialSubscription findAndRefresh(
            UUID organisationId,
            UUID actorUserId,
            Instant now
    ) {
        CommercialSubscription subscription = subscriptions
                .findByOrganisationId(organisationId)
                .orElse(null);
        if (subscription == null) return null;
        SubscriptionEventType transition;
        do {
            String before = snapshot(subscription);
            transition = subscription.refresh(now, actorUserId);
            if (transition != null) {
                recordSubscriptionEvent(subscription, transition, before, now);
            }
        } while (transition != null);
        return subscription;
    }

    private Set<Entitlement> effectiveEntitlements(CommercialSubscription subscription) {
        if (!launchGuard.enabled()) return EnumSet.allOf(Entitlement.class);
        if (!customerAccountActive(session.organisationId())) {
            return EnumSet.noneOf(Entitlement.class);
        }
        if (subscription == null
                || (subscription.getStatus() != SubscriptionStatus.TRIAL
                    && subscription.getStatus() != SubscriptionStatus.ACTIVE
                    && subscription.getStatus() != SubscriptionStatus.GRACE_PERIOD)) {
            return EnumSet.noneOf(Entitlement.class);
        }
        return catalogService.entitlements(subscription.getPlan());
    }

    private boolean customerAccountActive(UUID organisationId) {
        return organisations.findById(organisationId)
                .flatMap(value -> customerAccounts.findById(value.getCustomerAccountId()))
                .map(value -> value.getStatus() == CustomerAccountStatus.ACTIVE)
                .orElse(false);
    }

    private int studentCount(UUID organisationId) {
        return Math.toIntExact(memberships
                .findAllByOrganisationIdOrderByCreatedAtDesc(organisationId)
                .stream()
                .filter(membership -> membership.getRole() == UserRole.STUDENT)
                .filter(membership -> membership.getStatus() == AccountStatus.ACTIVE
                        || membership.getStatus() == AccountStatus.INVITED)
                .count());
    }

    private void requireCapacityCoversCurrentStudents(
            UUID organisationId,
            int studentLimit
    ) {
        int used = studentCount(organisationId);
        if (studentLimit < used) {
            throw DomainException.badRequest(
                    "PLAN_CAPACITY_BELOW_USAGE",
                    "The selected capacity is below the organisation's " + used
                            + " active or invited Students."
            );
        }
    }

    private int admissionLimit(CommercialSubscription subscription) {
        return subscription.getPendingStudentLimit() == null
                ? subscription.getStudentLimit()
                : Math.min(
                        subscription.getStudentLimit(),
                        subscription.getPendingStudentLimit()
                );
    }

    private void validateInvoiceDates(CreateInvoiceRequest request, long monthlyPrice) {
        Instant now = clock.instant();
        if (!request.periodStartsAt().isBefore(request.periodEndsAt())) {
            throw DomainException.badRequest(
                    "INVOICE_PERIOD_INVALID", "Invoice period start must be before its end."
            );
        }
        Duration period = Duration.between(
                request.periodStartsAt(), request.periodEndsAt()
        );
        if (period.compareTo(MINIMUM_MONTH) < 0 || period.compareTo(MAXIMUM_MONTH) > 0) {
            throw DomainException.badRequest(
                    "INVOICE_PERIOD_NOT_MONTHLY",
                    "Each invoice must cover one monthly period of 27 to 32 days."
            );
        }
        if (request.issuedAt().isAfter(now.plus(MAXIMUM_CLOCK_SKEW))
                || request.dueAt().isBefore(request.issuedAt())) {
            throw DomainException.badRequest(
                    "INVOICE_DATES_INVALID",
                    "Invoice issue time cannot be in the future and due time cannot precede it."
            );
        }
        if (request.taxPaise() > monthlyPrice) {
            throw DomainException.badRequest(
                    "INVOICE_TAX_INVALID",
                    "Manual tax cannot exceed the monthly plan subtotal."
            );
        }
    }

    private void validatePaidPlanChange(CommercialInvoice invoice, Instant now) {
        if (!invoice.getPeriodEndsAt().isAfter(now)) {
            throw DomainException.badRequest(
                    "INVOICE_PERIOD_EXPIRED",
                    "The paid subscription period must end in the future."
            );
        }
        CommercialSubscription current = findAndRefresh(
                session.organisationId(), session.userId(), now
        );
        if (current == null) return;
        if (current.getPendingPlan() != null) {
            throw DomainException.badRequest(
                    "PLAN_CHANGE_ALREADY_SCHEDULED",
                    "A paid renewal or plan change is already scheduled."
            );
        }
        if ((current.getStatus() == SubscriptionStatus.ACTIVE
                || current.getStatus() == SubscriptionStatus.SUSPENDED)
                && current.getPeriodEndsAt() != null
                && invoice.getPeriodStartsAt().isBefore(current.getPeriodEndsAt())) {
            boolean upgrade = invoice.getPlan().rank() > current.getPlan().rank()
                    && invoice.getStudentLimit() >= current.getStudentLimit();
            boolean capacityUpgrade = invoice.getPlan().rank() >= current.getPlan().rank()
                    && invoice.getStudentLimit() > current.getStudentLimit();
            if (!upgrade && !capacityUpgrade) {
                throw DomainException.badRequest(
                        "DOWNGRADE_EFFECTIVE_DATE_INVALID",
                        "Renewals and downgrades must start on or after the current paid period ends."
                );
            }
        }
    }

    private long validatedPrice(PlanCode plan, int studentLimit) {
        return catalogService.requirePrice(plan, studentLimit).getMonthlyPricePaise();
    }

    private RabbitPlatformSettings defaultPlatformSettings() {
        return platformSettings.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Rabbit platform settings are missing."));
    }

    private SubscriptionResponse subscriptionResponse(CommercialSubscription subscription) {
        return SubscriptionResponse.from(
                subscription, catalogService.entitlements(subscription.getPlan())
        );
    }

    private CommercialInvoice findInvoice(UUID invoiceId) {
        return invoices.findByIdAndOrganisationId(invoiceId, session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "INVOICE_NOT_FOUND", "Invoice was not found."
                ));
    }

    private void recordSubscriptionEvent(
            CommercialSubscription subscription,
            SubscriptionEventType eventType,
            String before,
            Instant now
    ) {
        subscriptions.save(subscription);
        CommercialSubscriptionEvent event = subscriptionEvents.save(
                new CommercialSubscriptionEvent(
                        subscription.getOrganisationId(),
                        subscription.getId(),
                        eventType,
                        before,
                        snapshot(subscription),
                        session.userId(),
                        now
                )
        );
        audit.recordForOrganisation(
                subscription.getOrganisationId(),
                "COM",
                eventType.name(),
                "CommercialSubscription",
                subscription.getId(),
                before,
                event.getAfterValue()
        );
    }

    private String snapshot(CommercialSubscription value) {
        return "status=" + value.getStatus()
                + ";plan=" + value.getPlan()
                + ";studentLimit=" + value.getStudentLimit()
                + ";periodEndsAt=" + value.getPeriodEndsAt()
                + ";trialEndsAt=" + value.getTrialEndsAt()
                + ";pendingPlan=" + value.getPendingPlan()
                + ";pendingStartsAt=" + value.getPendingPeriodStartsAt()
                + ";note=" + value.getNote();
    }

    private String receiptNumber(UUID paymentId) {
        String organisationCode = organisations.findById(session.organisationId())
                .map(Organisation::getCode)
                .orElse("ORG");
        return "RCT-" + organisationCode + "-"
                + paymentId.toString().substring(0, 8).toUpperCase();
    }

    private String supportCaseNumber() {
        return "SUP-" + clock.instant().toString().substring(0, 10).replace("-", "")
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
}
