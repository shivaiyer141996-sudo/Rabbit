package com.rabbit.aip.assessment;

import com.rabbit.aip.assessment.AssessmentDtos.AssessmentRequest;
import com.rabbit.aip.assessment.AssessmentDtos.AssessmentResponse;
import com.rabbit.aip.assessment.AssessmentDtos.ScheduleRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assessments")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
public class AssessmentController {

    private final AssessmentService service;

    public AssessmentController(AssessmentService service) {
        this.service = service;
    }

    @GetMapping
    List<AssessmentResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    AssessmentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AssessmentResponse create(@Valid @RequestBody AssessmentRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/publish")
    AssessmentResponse publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/schedule")
    AssessmentResponse schedule(
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleRequest request
    ) {
        return service.schedule(
                id,
                request.startAt(),
                request.endAt(),
                request.eligibleSectionIds()
        );
    }
}
