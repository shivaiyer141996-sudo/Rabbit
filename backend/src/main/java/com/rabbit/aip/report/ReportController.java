package com.rabbit.aip.report;

import com.rabbit.aip.commercial.CommercialService;
import com.rabbit.aip.commercial.CommercialTypes.Entitlement;
import com.rabbit.aip.report.ReportDtos.AssessmentReport;
import com.rabbit.aip.report.ReportDtos.FacultyPerformance;
import com.rabbit.aip.report.ReportDtos.IntelligenceOverview;
import com.rabbit.aip.report.ReportDtos.QuestionPerformance;
import com.rabbit.aip.report.ReportDtos.StudentPerformanceReport;
import com.rabbit.aip.report.ReportDtos.StudentAnalyticsReport;
import com.rabbit.aip.report.ReportDtos.StudentReport;
import com.rabbit.aip.report.ReportDtos.TeacherAnalyticsReport;
import com.rabbit.aip.assessment.AssessmentType;
import com.rabbit.aip.report.ReportExportService.ExportedReport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;
    private final ReportExportService exports;
    private final CommercialService commercial;

    public ReportController(
            ReportService service,
            ReportExportService exports,
            CommercialService commercial
    ) {
        this.service = service;
        this.exports = exports;
        this.commercial = commercial;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    IntelligenceOverview overview() {
        commercial.requireEntitlement(Entitlement.INSTITUTION_ANALYTICS);
        return service.overview();
    }

    @GetMapping("/assessments/{assessmentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    AssessmentReport assessment(@PathVariable UUID assessmentId) {
        commercial.requireEntitlement(Entitlement.INSTITUTION_ANALYTICS);
        return service.assessment(assessmentId);
    }

    @GetMapping("/students/me")
    @PreAuthorize("hasRole('STUDENT')")
    StudentPerformanceReport me() {
        commercial.requireEntitlement(Entitlement.STUDENT_EVALUATION);
        return service.myPerformance();
    }

    @GetMapping("/students/me/analytics")
    @PreAuthorize("hasRole('STUDENT')")
    StudentAnalyticsReport myAnalytics() {
        commercial.requireEntitlement(Entitlement.STUDENT_EVALUATION);
        return service.myAnalytics();
    }

    @GetMapping("/students")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    StudentReport students(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) AssessmentType assessmentType,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) Instant submittedFrom,
            @RequestParam(required = false) Instant submittedBefore
    ) {
        commercial.requireEntitlement(Entitlement.STUDENT_EVALUATION);
        return service.students(
                query,
                subjectId,
                assessmentType,
                departmentId,
                sectionId,
                submittedFrom,
                submittedBefore
        );
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    StudentPerformanceReport student(@PathVariable UUID studentId) {
        commercial.requireEntitlement(Entitlement.STUDENT_EVALUATION);
        return service.student(studentId);
    }

    @GetMapping("/students/{studentId}/analytics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    StudentAnalyticsReport studentAnalytics(@PathVariable UUID studentId) {
        commercial.requireEntitlement(Entitlement.STUDENT_EVALUATION);
        return service.studentAnalytics(studentId);
    }

    @GetMapping("/teacher")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    TeacherAnalyticsReport teacher(
            @RequestParam(required = false) UUID teacherUserId
    ) {
        commercial.requireEntitlement(Entitlement.TEACHER_ANALYTICS);
        return service.teacherAnalytics(teacherUserId);
    }

    @GetMapping("/questions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY','REVIEWER')")
    List<QuestionPerformance> questions() {
        commercial.requireEntitlement(Entitlement.INSTITUTION_ANALYTICS);
        return service.questionAnalytics();
    }

    @GetMapping("/faculty")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD')")
    List<FacultyPerformance> faculty() {
        commercial.requireEntitlement(Entitlement.TEACHER_ANALYTICS);
        return service.facultyPerformance();
    }

    @GetMapping(value = "/assessments/{assessmentId}/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    ResponseEntity<byte[]> exportAssessment(@PathVariable UUID assessmentId) {
        commercial.requireEntitlement(Entitlement.REPORT_EXPORTS);
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

    @GetMapping(
            value = "/assessments/{assessmentId}/export.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    ResponseEntity<byte[]> exportAssessmentPdf(@PathVariable UUID assessmentId) {
        commercial.requireEntitlement(Entitlement.REPORT_EXPORTS);
        return downloadable(exports.pdf(assessmentId));
    }

    @GetMapping(
            value = "/assessments/{assessmentId}/export.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    ResponseEntity<byte[]> exportAssessmentExcel(@PathVariable UUID assessmentId) {
        commercial.requireEntitlement(Entitlement.REPORT_EXPORTS);
        return downloadable(exports.excel(assessmentId));
    }

    @GetMapping(
            value = "/teacher/export.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    ResponseEntity<byte[]> exportTeacherPdf(
            @RequestParam(required = false) UUID teacherUserId
    ) {
        commercial.requireEntitlement(Entitlement.REPORT_EXPORTS);
        return downloadable(exports.teacherPdf(teacherUserId));
    }

    @GetMapping(
            value = "/teacher/export.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY')")
    ResponseEntity<byte[]> exportTeacherExcel(
            @RequestParam(required = false) UUID teacherUserId
    ) {
        commercial.requireEntitlement(Entitlement.REPORT_EXPORTS);
        return downloadable(exports.teacherExcel(teacherUserId));
    }

    private ResponseEntity<byte[]> downloadable(ExportedReport report) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(report.contentType()))
                .contentLength(report.content().length)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(report.filename())
                                .build()
                                .toString()
                )
                .header("X-Content-Type-Options", "nosniff")
                .body(report.content());
    }
}
