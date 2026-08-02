package com.rabbit.aip.pilot;

import com.rabbit.aip.pilot.PilotDtos.PilotReadinessResponse;
import com.rabbit.aip.pilot.PilotDtos.PilotDecisionRequest;
import com.rabbit.aip.pilot.PilotDtos.UpdatePilotCheckRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pilot-readiness")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
public class PilotReadinessController {

    private final PilotReadinessService service;

    public PilotReadinessController(PilotReadinessService service) {
        this.service = service;
    }

    @GetMapping
    PilotReadinessResponse readiness() {
        return service.readiness();
    }

    @PutMapping("/checks/{key}")
    PilotReadinessResponse update(
            @PathVariable PilotCheckKey key,
            @Valid @RequestBody UpdatePilotCheckRequest request
    ) {
        return service.update(key, request);
    }

    @PostMapping({"/decisions", "/sign-off"})
    PilotReadinessResponse decision(
            @Valid @RequestBody PilotDecisionRequest request
    ) {
        return service.recordDecision(request);
    }
}
