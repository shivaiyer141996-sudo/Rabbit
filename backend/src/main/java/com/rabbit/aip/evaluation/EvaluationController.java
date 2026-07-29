package com.rabbit.aip.evaluation;

import com.rabbit.aip.evaluation.EvaluationDtos.AssessmentEvaluationSummary;
import com.rabbit.aip.evaluation.EvaluationDtos.EvaluationRow;
import com.rabbit.aip.evaluation.EvaluationDtos.PublicationResponse;
import com.rabbit.aip.evaluation.EvaluationDtos.ReEvaluationRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/evaluation")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @GetMapping("/assessments/{assessmentId}/results")
    AssessmentEvaluationSummary results(@PathVariable UUID assessmentId) {
        return service.assessmentResults(assessmentId);
    }

    @PostMapping("/assessments/{assessmentId}/publish")
    PublicationResponse publish(@PathVariable UUID assessmentId) {
        return service.publish(assessmentId);
    }

    @PostMapping("/attempts/{attemptId}/re-evaluate")
    EvaluationRow reEvaluate(
            @PathVariable UUID attemptId,
            @Valid @RequestBody ReEvaluationRequest request
    ) {
        return service.reEvaluate(attemptId, request.reason());
    }
}
