package com.rabbit.aip.settings;

import com.rabbit.aip.commercial.CommercialService;
import com.rabbit.aip.commercial.CommercialTypes.Entitlement;
import com.rabbit.aip.settings.SettingsDtos.GeneralSettingsRequest;
import com.rabbit.aip.settings.SettingsDtos.GeneralSettingsResponse;
import com.rabbit.aip.settings.SettingsDtos.GradeBandResponse;
import com.rabbit.aip.settings.SettingsDtos.GradeBandsRequest;
import com.rabbit.aip.settings.SettingsDtos.SettingsBundle;
import com.rabbit.aip.settings.SettingsDtos.SubjectRequest;
import com.rabbit.aip.settings.SettingsDtos.SubjectResponse;
import com.rabbit.aip.settings.SettingsDtos.TopicRequest;
import com.rabbit.aip.settings.SettingsDtos.TopicResponse;
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
@RequestMapping("/api/v1/settings")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
public class SettingsController {

    private final SettingsService service;
    private final CommercialService commercial;

    public SettingsController(SettingsService service, CommercialService commercial) {
        this.service = service;
        this.commercial = commercial;
    }

    @GetMapping
    SettingsBundle get() {
        return service.get();
    }

    @PutMapping("/general")
    GeneralSettingsResponse updateGeneral(
            @Valid @RequestBody GeneralSettingsRequest request
    ) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.updateGeneral(request);
    }

    @PutMapping("/grade-bands")
    List<GradeBandResponse> updateGradeBands(
            @Valid @RequestBody GradeBandsRequest request
    ) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.updateGradeBands(request.bands());
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    SubjectResponse createSubject(@Valid @RequestBody SubjectRequest request) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.createSubject(request.code(), request.name());
    }

    @PatchMapping("/subjects/{id}/deactivate")
    SubjectResponse deactivateSubject(@PathVariable UUID id) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.deactivateSubject(id);
    }

    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    TopicResponse createTopic(@Valid @RequestBody TopicRequest request) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.createTopic(request.subjectId(), request.name());
    }
}
