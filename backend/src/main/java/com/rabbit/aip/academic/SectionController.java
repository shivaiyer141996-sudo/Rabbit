package com.rabbit.aip.academic;

import com.rabbit.aip.academic.SectionDtos.SectionRequest;
import com.rabbit.aip.academic.SectionDtos.SectionResponse;
import com.rabbit.aip.commercial.CommercialService;
import com.rabbit.aip.commercial.CommercialTypes.Entitlement;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/academic-masters/sections")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
public class SectionController {
    private final SectionService service;
    private final CommercialService commercial;

    public SectionController(SectionService service, CommercialService commercial) {
        this.service = service;
        this.commercial = commercial;
    }

    @GetMapping
    List<SectionResponse> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SectionResponse create(@Valid @RequestBody SectionRequest request) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.create(request);
    }

    @PutMapping("/{id}")
    SectionResponse update(@PathVariable UUID id, @Valid @RequestBody SectionRequest request) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    SectionResponse activate(@PathVariable UUID id) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.activate(id);
    }

    @PatchMapping("/{id}/deactivate")
    SectionResponse deactivate(@PathVariable UUID id) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.deactivate(id);
    }

    @PatchMapping("/{id}/archive")
    SectionResponse archive(@PathVariable UUID id) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.archive(id);
    }
}
