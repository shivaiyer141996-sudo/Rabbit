package com.rabbit.aip.attempt;

import com.rabbit.aip.commercial.CommercialService;
import com.rabbit.aip.commercial.CommercialTypes.Entitlement;
import com.rabbit.aip.attempt.AttemptDtos.AttemptView;
import com.rabbit.aip.attempt.AttemptDtos.ResultView;
import com.rabbit.aip.attempt.AttemptDtos.SaveResponseRequest;
import com.rabbit.aip.attempt.AttemptDtos.SavedResponse;
import com.rabbit.aip.attempt.AttemptDtos.StudentAssessment;
import com.rabbit.aip.attempt.AttemptDtos.StudentAssessmentInstructions;
import com.rabbit.aip.attempt.AttemptDtos.AttemptHistoryItem;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentAssessmentController {

    private final AttemptService service;
    private final CommercialService commercial;

    public StudentAssessmentController(AttemptService service, CommercialService commercial) {
        this.service = service;
        this.commercial = commercial;
    }

    @GetMapping("/assessments")
    List<StudentAssessment> assessments() {
        return service.catalog();
    }

    @GetMapping("/assessments/{assessmentId}")
    StudentAssessmentInstructions instructions(@PathVariable UUID assessmentId) {
        return service.instructions(assessmentId);
    }

    @GetMapping("/attempts/history")
    List<AttemptHistoryItem> history() {
        return service.history();
    }

    @PostMapping("/assessments/{assessmentId}/attempts")
    AttemptView start(@PathVariable UUID assessmentId) {
        commercial.requireEntitlement(Entitlement.ASSESSMENT_DELIVERY);
        return service.start(assessmentId);
    }

    @PutMapping("/attempts/{attemptId}/responses")
    SavedResponse save(
            @PathVariable UUID attemptId,
            @Valid @RequestBody SaveResponseRequest request
    ) {
        return service.save(attemptId, request);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    ResultView submit(
            @PathVariable UUID attemptId,
            @RequestParam(defaultValue = "false") boolean automatic
    ) {
        return service.submit(attemptId, automatic);
    }

    @GetMapping("/results/{attemptId}")
    ResultView result(@PathVariable UUID attemptId) {
        return service.result(attemptId);
    }
}
