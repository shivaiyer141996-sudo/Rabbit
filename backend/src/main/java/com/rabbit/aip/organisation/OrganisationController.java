package com.rabbit.aip.organisation;

import com.rabbit.aip.commercial.CommercialLaunchGuard;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.user.AccountStatus;
import com.rabbit.aip.platform.CustomerAccount;
import com.rabbit.aip.platform.CustomerAccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organisations")
public class OrganisationController {

    private final OrganisationRepository organisations;
    private final CurrentSession session;
    private final CommercialLaunchGuard commercialLaunch;
    private final CustomerAccountRepository customerAccounts;

    public OrganisationController(
            OrganisationRepository organisations,
            CurrentSession session,
            CommercialLaunchGuard commercialLaunch,
            CustomerAccountRepository customerAccounts
    ) {
        this.organisations = organisations;
        this.session = session;
        this.commercialLaunch = commercialLaunch;
        this.customerAccounts = customerAccounts;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    List<OrganisationResponse> list() {
        return organisations.findAll().stream().map(OrganisationResponse::from).toList();
    }

    @GetMapping("/current")
    OrganisationResponse current() {
        return organisations.findById(session.organisationId())
                .map(OrganisationResponse::from)
                .orElseThrow(() -> DomainException.notFound(
                        "ORGANISATION_NOT_FOUND",
                        "The selected organisation no longer exists."
                ));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    OrganisationResponse create(@Valid @RequestBody CreateOrganisationRequest request) {
        if (commercialLaunch.enabled()) {
            throw DomainException.badRequest(
                    "COMMERCIAL_ONBOARDING_REQUIRED",
                    "Use commercial onboarding so membership, settings, trial, and audit records are created atomically."
            );
        }
        if (organisations.existsByCodeIgnoreCase(request.code())) {
            throw new DomainException(
                    "ORGANISATION_CODE_EXISTS",
                    "Organisation code must be unique.",
                    HttpStatus.CONFLICT
            );
        }
        CustomerAccount account = customerAccounts.save(new CustomerAccount(
                "CA-" + request.code(), request.name() + " Account", session.userId()
        ));
        return OrganisationResponse.from(organisations.save(new Organisation(
                account.getId(), request.code(),
                request.name(),
                request.timezone()
        )));
    }

    record CreateOrganisationRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9_-]{2,30}$")
            String code,
            @NotBlank String name,
            @NotBlank String timezone
    ) {
    }

    record OrganisationResponse(
            UUID id,
            String code,
            String name,
            String timezone,
            AccountStatus status,
            UUID customerAccountId,
            boolean logoAvailable,
            java.time.Instant logoUpdatedAt
    ) {
        static OrganisationResponse from(Organisation organisation) {
            return new OrganisationResponse(
                    organisation.getId(),
                    organisation.getCode(),
                    organisation.getName(),
                    organisation.getTimezone(),
                    organisation.getStatus(),
                    organisation.getCustomerAccountId(),
                    organisation.hasLogo(),
                    organisation.getLogoUpdatedAt()
            );
        }
    }
}
