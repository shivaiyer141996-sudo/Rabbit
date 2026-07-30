package com.rabbit.aip.report;

import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.feature.FeatureFlagKey;
import com.rabbit.aip.feature.FeatureFlagService;
import com.rabbit.aip.report.ReportDtos.AssessmentReport;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportExportService {

    private final ReportService reports;
    private final FeatureFlagService featureFlags;
    private final AuditService audit;
    private final PdfReportWriter pdfWriter = new PdfReportWriter();
    private final ExcelReportWriter excelWriter = new ExcelReportWriter();

    public ReportExportService(
            ReportService reports,
            FeatureFlagService featureFlags,
            AuditService audit
    ) {
        this.reports = reports;
        this.featureFlags = featureFlags;
        this.audit = audit;
    }

    @Transactional
    public ExportedReport pdf(UUID assessmentId) {
        featureFlags.require(FeatureFlagKey.PDF_EXPORTS);
        AssessmentReport report = reports.assessment(assessmentId);
        byte[] content = pdfWriter.write(report);
        audit.record(
                "RPT", "EXPORT_ASSESSMENT_PDF", "Assessment", assessmentId,
                null, "Bytes: " + content.length
        );
        return new ExportedReport(
                filename(report.title(), "pdf"),
                "application/pdf",
                content
        );
    }

    @Transactional
    public ExportedReport excel(UUID assessmentId) {
        featureFlags.require(FeatureFlagKey.EXCEL_EXPORTS);
        AssessmentReport report = reports.assessment(assessmentId);
        byte[] content = excelWriter.write(report);
        audit.record(
                "RPT", "EXPORT_ASSESSMENT_XLSX", "Assessment", assessmentId,
                null, "Bytes: " + content.length
        );
        return new ExportedReport(
                filename(report.title(), "xlsx"),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );
    }

    private String filename(String title, String extension) {
        String slug = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) slug = "assessment";
        return "rabbit-" + slug + "-report." + extension;
    }

    public record ExportedReport(
            String filename,
            String contentType,
            byte[] content
    ) {
    }
}
