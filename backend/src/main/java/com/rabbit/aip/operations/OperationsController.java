package com.rabbit.aip.operations;

import com.rabbit.aip.operations.OperationsDtos.OperationalSnapshot;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
public class OperationsController {

    private final OperationsService service;

    public OperationsController(OperationsService service) {
        this.service = service;
    }

    @GetMapping("/readiness")
    OperationalSnapshot readiness() {
        return service.snapshot();
    }
}
