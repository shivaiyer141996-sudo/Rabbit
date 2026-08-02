package com.rabbit.aip.evaluation;

import com.rabbit.aip.commercial.CommercialService;
import com.rabbit.aip.commercial.CommercialTypes.Entitlement;
import com.rabbit.aip.evaluation.EvaluationDtos.AssessmentEvaluationSummary;
import com.rabbit.aip.evaluation.EvaluationDtos.EvaluationRow;
import com.rabbit.aip.evaluation.EvaluationDtos.PublicationResponse;
import com.rabbit.aip.evaluation.EvaluationDtos.ReEvaluationRequest;
import com.rabbit.aip.evaluation.EvaluationDtos.AssessmentMonitor;
import com.rabbit.aip.evaluation.EvaluationDtos.ManualAttemptReview;
import com.rabbit.aip.evaluation.EvaluationDtos.ManualScoreUpdateRequest;
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
    private final CommercialService commercial;

    public EvaluationController(
            EvaluationService service,
            CommercialService commercial
    ) {
        this.service = service;
        this.commercial = commercial;
    }

    @GetMapping("/assessments/{assessmentId}/results")
    AssessmentEvaluationSummary results(@PathVariable UUID assessmentId) {
        return service.assessmentResults(assessmentId);
    }

    @GetMapping("/assessments/{assessmentId}/monitor")
    AssessmentMonitor monitor(@PathVariable UUID assessmentId) {
        return service.monitor(assessmentId);
    }

    @PostMapping("/assessments/{assessmentId}/publish")
    PublicationResponse publish(@PathVariable UUID assessmentId) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.publish(assessmentId);
    }

    @PostMapping("/attempts/{attemptId}/re-evaluate")
    EvaluationRow reEvaluate(
            @PathVariable UUID attemptId,
            @Valid @RequestBody ReEvaluationRequest request
    ) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.reEvaluate(attemptId, request.reason());
    }

    @GetMapping("/attempts/{attemptId}/review")
    ManualAttemptReview manualReview(@PathVariable UUID attemptId) {
        return service.manualReview(attemptId);
    }

    @PostMapping("/attempts/{attemptId}/score")
    ManualAttemptReview updateScore(
            @PathVariable UUID attemptId,
            @Valid @RequestBody ManualScoreUpdateRequest request
    ) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.updateScore(attemptId, request);
    }
}
