package com.rabbit.aip.assessment;

import com.rabbit.aip.commercial.CommercialService;
import com.rabbit.aip.commercial.CommercialTypes.Entitlement;
import com.rabbit.aip.assessment.AssessmentDtos.AssessmentRequest;
import com.rabbit.aip.assessment.AssessmentDtos.AssessmentResponse;
import com.rabbit.aip.assessment.AssessmentDtos.AssessmentReviewRequest;
import com.rabbit.aip.assessment.AssessmentDtos.AssessmentReviewResponse;
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
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY','REVIEWER')")
public class AssessmentController {

    private final AssessmentService service;
    private final CommercialService commercial;

    public AssessmentController(AssessmentService service, CommercialService commercial) {
        this.service = service;
        this.commercial = commercial;
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    AssessmentResponse create(@Valid @RequestBody AssessmentRequest request) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.create(request);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    AssessmentResponse publish(@PathVariable UUID id) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.publish(id);
    }

    @PostMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    AssessmentResponse schedule(
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleRequest request
    ) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.schedule(
                id,
                request.startAt(),
                request.endAt(),
                request.eligibleSectionIds()
        );
    }

    @GetMapping("/review-queue")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','REVIEWER')")
    List<AssessmentResponse> reviewQueue() {
        return service.reviewQueue();
    }

    @GetMapping("/{id}/reviews")
    List<AssessmentReviewResponse> reviewHistory(@PathVariable UUID id) {
        return service.reviewHistory(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    AssessmentResponse submit(@PathVariable UUID id) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.submit(id);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','REVIEWER')")
    AssessmentResponse review(
            @PathVariable UUID id,
            @Valid @RequestBody AssessmentReviewRequest request
    ) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.review(id, request.decision(), request.reason());
    }
}
