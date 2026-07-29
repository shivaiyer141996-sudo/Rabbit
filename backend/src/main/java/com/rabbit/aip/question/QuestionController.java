package com.rabbit.aip.question;

import com.rabbit.aip.question.QuestionDtos.QuestionRequest;
import com.rabbit.aip.question.QuestionDtos.QuestionResponse;
import com.rabbit.aip.question.QuestionDtos.ReviewRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService service;

    public QuestionController(QuestionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY','REVIEWER')")
    List<QuestionResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) Difficulty difficulty
    ) {
        return service.list(query, status, difficulty);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY','REVIEWER')")
    QuestionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    QuestionResponse create(@Valid @RequestBody QuestionRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    QuestionResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody QuestionRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    QuestionResponse submit(@PathVariable UUID id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','REVIEWER')")
    QuestionResponse review(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewRequest request
    ) {
        return service.review(id, request.decision(), request.reason());
    }
}
