package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialDtos.CommercialOverviewResponse;
import com.rabbit.aip.commercial.CommercialDtos.CreateInvoiceRequest;
import com.rabbit.aip.commercial.CommercialDtos.CreateSupportCaseRequest;
import com.rabbit.aip.commercial.CommercialDtos.InvoiceResponse;
import com.rabbit.aip.commercial.CommercialDtos.OnboardOrganisationRequest;
import com.rabbit.aip.commercial.CommercialDtos.OnboardOrganisationResponse;
import com.rabbit.aip.commercial.CommercialDtos.PaymentReceiptResponse;
import com.rabbit.aip.commercial.CommercialDtos.PlanCatalogResponse;
import com.rabbit.aip.commercial.CommercialDtos.RecordPaymentRequest;
import com.rabbit.aip.commercial.CommercialDtos.StartTrialRequest;
import com.rabbit.aip.commercial.CommercialDtos.SubscriptionResponse;
import com.rabbit.aip.commercial.CommercialDtos.SubscriptionStateRequest;
import com.rabbit.aip.commercial.CommercialDtos.SupportCaseResponse;
import com.rabbit.aip.commercial.CommercialDtos.UpdateSupportCaseRequest;
import com.rabbit.aip.commercial.CommercialDtos.VoidInvoiceRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commercial")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
public class CommercialController {

    private final CommercialService service;

    public CommercialController(CommercialService service) {
        this.service = service;
    }

    @GetMapping("/catalog")
    List<PlanCatalogResponse> catalog() {
        return service.catalog();
    }

    @GetMapping("/overview")
    CommercialOverviewResponse overview() {
        return service.overview();
    }

    @PostMapping("/trial")
    @ResponseStatus(HttpStatus.CREATED)
    SubscriptionResponse startTrial(@Valid @RequestBody StartTrialRequest request) {
        return service.startTrial(request);
    }

    @PostMapping("/onboarding")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    OnboardOrganisationResponse onboard(
            @Valid @RequestBody OnboardOrganisationRequest request
    ) {
        return service.onboard(request);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    InvoiceResponse createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
        return service.createInvoice(request);
    }

    @PostMapping("/invoices/{invoiceId}/void")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    InvoiceResponse voidInvoice(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody VoidInvoiceRequest request
    ) {
        return service.voidInvoice(invoiceId, request.reason());
    }

    @PostMapping("/subscription/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    SubscriptionResponse suspendSubscription(
            @Valid @RequestBody SubscriptionStateRequest request
    ) {
        return service.suspendSubscription(request.reason());
    }

    @PostMapping("/subscription/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    SubscriptionResponse restoreSubscription(
            @Valid @RequestBody SubscriptionStateRequest request
    ) {
        return service.restoreSubscription(request.reason());
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    PaymentReceiptResponse recordPayment(
            @Valid @RequestBody RecordPaymentRequest request
    ) {
        return service.recordPayment(request);
    }

    @PostMapping("/support-cases")
    @ResponseStatus(HttpStatus.CREATED)
    SupportCaseResponse createSupportCase(
            @Valid @RequestBody CreateSupportCaseRequest request
    ) {
        return service.createSupportCase(request);
    }

    @PatchMapping("/support-cases/{caseId}")
    SupportCaseResponse updateSupportCase(
            @PathVariable UUID caseId,
            @Valid @RequestBody UpdateSupportCaseRequest request
    ) {
        return service.updateSupportCase(caseId, request);
    }
}
