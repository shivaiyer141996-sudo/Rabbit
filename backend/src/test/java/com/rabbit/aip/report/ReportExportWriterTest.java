package com.rabbit.aip.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbit.aip.question.Difficulty;
import com.rabbit.aip.report.ReportDtos.AssessmentReport;
import com.rabbit.aip.report.ReportDtos.CountValue;
import com.rabbit.aip.report.ReportDtos.QuestionPerformance;
import com.rabbit.aip.report.ReportDtos.StudentResultPoint;
import com.rabbit.aip.report.ReportDtos.TeacherAnalyticsReport;
import com.rabbit.aip.report.ReportDtos.TeacherBatchAnalytics;
import com.rabbit.aip.report.ReportDtos.TeacherStudentComparison;
import com.rabbit.aip.report.ReportDtos.TeacherWeakTopic;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class ReportExportWriterTest {

    @Test
    void createsMultiPagePdfWithValidCrossReference() {
        AssessmentReport report = report(70);

        byte[] output = new PdfReportWriter().write(report);
        String text = new String(output, StandardCharsets.US_ASCII);

        assertThat(text).startsWith("%PDF-1.4");
        assertThat(text).contains("/Type /Catalog");
        assertThat(text).contains("/Count 2");
        assertThat(text).endsWith("%%EOF\n");
    }

    @Test
    void createsNativeExcelWorkbookWithThreeWorksheets() throws Exception {
        byte[] output = new ExcelReportWriter().write(report(2));
        List<String> entries = new ArrayList<>();
        String workbook = "";
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                if (entry.getName().equals("xl/workbook.xml")) {
                    workbook = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }

        assertThat(entries).contains(
                "[Content_Types].xml",
                "xl/workbook.xml",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml",
                "xl/worksheets/sheet2.xml",
                "xl/worksheets/sheet3.xml"
        );
        assertThat(workbook).contains("Student Results", "Question Analytics");
    }

    @Test
    void createsTeacherPdfAndFourSheetExcelWorkbook() throws Exception {
        TeacherAnalyticsReport report = teacherReport();

        String pdf = new String(
                new PdfReportWriter().write(report), StandardCharsets.US_ASCII
        );
        assertThat(pdf)
                .startsWith("%PDF-1.4")
                .contains("TEACHER ANALYTICS REPORT")
                .endsWith("%%EOF\n");

        byte[] output = new ExcelReportWriter().write(report);
        List<String> entries = new ArrayList<>();
        String workbook = "";
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                if (entry.getName().equals("xl/workbook.xml")) {
                    workbook = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertThat(entries).contains("xl/worksheets/sheet4.xml");
        assertThat(workbook).contains(
                "Batch Analytics", "Student Comparison", "Weak Topics"
        );
    }

    private AssessmentReport report(int studentCount) {
        UUID assessmentId = UUID.fromString(
                "77777777-7777-7777-7777-777777777703"
        );
        List<StudentResultPoint> students = new ArrayList<>();
        for (int index = 0; index < studentCount; index++) {
            students.add(new StudentResultPoint(
                    UUID.randomUUID(),
                    assessmentId,
                    "Physics Progress Check",
                    "Student " + (index + 1),
                    Instant.parse("2026-07-15T04:54:00Z"),
                    BigDecimal.valueOf(8),
                    BigDecimal.valueOf(10),
                    BigDecimal.valueOf(80),
                    "A",
                    "IMPROVING"
            ));
        }
        QuestionPerformance question = new QuestionPerformance(
                UUID.randomUUID(),
                "PHY-MEC-001",
                "Which graph represents displacement against time?",
                Difficulty.MEDIUM,
                4,
                70,
                BigDecimal.valueOf(72.5),
                BigDecimal.valueOf(0.73),
                BigDecimal.valueOf(0.42),
                false
        );
        return new AssessmentReport(
                assessmentId,
                "Physics Progress Check",
                studentCount,
                BigDecimal.valueOf(72.4),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(22.5),
                BigDecimal.valueOf(84.7),
                List.of(new CountValue("80-100", studentCount)),
                students,
                List.of(question),
                Instant.parse("2026-07-30T10:00:00Z"),
                "admin@demo.rabbit.local"
        );
    }

    private TeacherAnalyticsReport teacherReport() {
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        return new TeacherAnalyticsReport(
                teacherId,
                "Demo Teacher",
                2,
                4,
                BigDecimal.valueOf(74.5),
                1,
                List.of(new TeacherBatchAnalytics(
                        sectionId,
                        "Science · A",
                        20,
                        2,
                        4,
                        4,
                        BigDecimal.valueOf(10),
                        BigDecimal.valueOf(74.5),
                        BigDecimal.valueOf(75)
                )),
                List.of(new TeacherStudentComparison(
                        studentId,
                        "Demo Student",
                        "Science · A",
                        2,
                        BigDecimal.valueOf(74.5),
                        BigDecimal.valueOf(80),
                        BigDecimal.valueOf(100),
                        1,
                        "IMPROVING",
                        false
                )),
                List.of(new TeacherWeakTopic(
                        subjectId,
                        "Physics",
                        topicId,
                        "Motion",
                        3,
                        4,
                        BigDecimal.valueOf(45),
                        BigDecimal.valueOf(50),
                        42,
                        true
                )),
                Instant.parse("2026-08-01T10:00:00Z")
        );
    }
}
