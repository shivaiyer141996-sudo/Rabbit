package com.rabbit.aip.report;

import com.rabbit.aip.report.ReportDtos.AssessmentReport;
import com.rabbit.aip.report.ReportDtos.FacultyPerformance;
import com.rabbit.aip.report.ReportDtos.IntelligenceOverview;
import com.rabbit.aip.report.ReportDtos.QuestionPerformance;
import com.rabbit.aip.report.ReportDtos.StudentPerformanceReport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    IntelligenceOverview overview() {
        return service.overview();
    }

    @GetMapping("/assessments/{assessmentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    AssessmentReport assessment(@PathVariable UUID assessmentId) {
        return service.assessment(assessmentId);
    }

    @GetMapping("/students/me")
    @PreAuthorize("hasRole('STUDENT')")
    StudentPerformanceReport me() {
        return service.myPerformance();
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    StudentPerformanceReport student(@PathVariable UUID studentId) {
        return service.student(studentId);
    }

    @GetMapping("/questions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY','REVIEWER')")
    List<QuestionPerformance> questions() {
        return service.questionAnalytics();
    }

    @GetMapping("/faculty")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD')")
    List<FacultyPerformance> faculty() {
        return service.facultyPerformance();
    }

    @GetMapping(value = "/assessments/{assessmentId}/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    ResponseEntity<byte[]> exportAssessment(@PathVariable UUID assessmentId) {
        byte[] data = service.assessmentCsv(assessmentId)
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("rabbit-assessment-report.csv")
                                .build()
                                .toString()
                )
                .body(data);
    }
}
