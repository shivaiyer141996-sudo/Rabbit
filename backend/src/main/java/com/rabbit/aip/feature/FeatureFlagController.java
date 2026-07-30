package com.rabbit.aip.feature;

import com.rabbit.aip.feature.FeatureFlagDtos.FeatureFlagResponse;
import com.rabbit.aip.feature.FeatureFlagDtos.UpdateFeatureFlagRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feature-flags")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
public class FeatureFlagController {

    private final FeatureFlagService service;

    public FeatureFlagController(FeatureFlagService service) {
        this.service = service;
    }

    @GetMapping
    List<FeatureFlagResponse> list() {
        return service.list();
    }

    @PatchMapping("/{key}")
    FeatureFlagResponse update(
            @PathVariable FeatureFlagKey key,
            @Valid @RequestBody UpdateFeatureFlagRequest request
    ) {
        return service.update(key, request.enabled(), request.rolloutPercentage());
    }
}
