package com.rabbit.aip.platform;

import com.rabbit.aip.commercial.CommercialDtos.SubscriptionResponse;
import com.rabbit.aip.platform.PlatformDtos.AssignOrganisationRequest;
import com.rabbit.aip.platform.PlatformDtos.CustomerAccountRequest;
import com.rabbit.aip.platform.PlatformDtos.CustomerAccountResponse;
import com.rabbit.aip.platform.PlatformDtos.CustomerAccountStateRequest;
import com.rabbit.aip.platform.PlatformDtos.OnboardOrganisationRequest;
import com.rabbit.aip.platform.PlatformDtos.OnboardOrganisationResponse;
import com.rabbit.aip.platform.PlatformDtos.OrganisationDetailsRequest;
import com.rabbit.aip.platform.PlatformDtos.PlatformOrganisationResponse;
import com.rabbit.aip.platform.PlatformDtos.PlatformOverviewResponse;
import com.rabbit.aip.platform.PlatformDtos.PlatformSettingsRequest;
import com.rabbit.aip.platform.PlatformDtos.PlatformSettingsResponse;
import com.rabbit.aip.platform.PlatformDtos.SubscriptionActionRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformController {
    private final PlatformService service;

    public PlatformController(PlatformService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    PlatformOverviewResponse overview() { return service.overview(); }

    @PostMapping("/customer-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    CustomerAccountResponse createCustomerAccount(
            @Valid @RequestBody CustomerAccountRequest request
    ) { return service.createCustomerAccount(request); }

    @PutMapping("/customer-accounts/{id}")
    CustomerAccountResponse updateCustomerAccount(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerAccountRequest request
    ) { return service.updateCustomerAccount(id, request); }

    @PatchMapping("/customer-accounts/{id}/status")
    CustomerAccountResponse changeCustomerAccountStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerAccountStateRequest request
    ) { return service.changeCustomerAccountStatus(id, request); }

    @PostMapping("/organisations")
    @ResponseStatus(HttpStatus.CREATED)
    OnboardOrganisationResponse onboardOrganisation(
            @Valid @RequestBody OnboardOrganisationRequest request
    ) { return service.onboardOrganisation(request); }

    @PatchMapping("/organisations/{id}/customer-account")
    PlatformOrganisationResponse assignOrganisation(
            @PathVariable UUID id,
            @Valid @RequestBody AssignOrganisationRequest request
    ) { return service.assignOrganisation(id, request); }

    @PutMapping("/organisations/{id}")
    PlatformOrganisationResponse updateOrganisation(
            @PathVariable UUID id,
            @Valid @RequestBody OrganisationDetailsRequest request
    ) { return service.updateOrganisation(id, request); }

    @PostMapping("/organisations/{id}/subscription-actions")
    SubscriptionResponse subscriptionAction(
            @PathVariable UUID id,
            @Valid @RequestBody SubscriptionActionRequest request
    ) { return service.subscriptionAction(id, request); }

    @PutMapping("/settings")
    PlatformSettingsResponse updateSettings(
            @Valid @RequestBody PlatformSettingsRequest request
    ) { return service.updateSettings(request); }
}
