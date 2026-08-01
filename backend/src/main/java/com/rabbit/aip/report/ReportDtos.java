package com.rabbit.aip.report;

import com.rabbit.aip.assessment.AssessmentStatus;
import com.rabbit.aip.question.Difficulty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record LabelValue(String label, BigDecimal value) {
    }

    public record CountValue(String label, long value) {
    }

    public record IntelligenceOverview(
            long publishedResults,
            BigDecimal averageScore,
            BigDecimal passRate,
            long atRiskStudents,
            BigDecimal completionRate,
            List<CountValue> scoreDistribution,
            List<LabelValue> performanceTrend,
            List<AssessmentSnapshot> recentAssessments
    ) {
    }

    public record AssessmentSnapshot(
            UUID assessmentId,
            String title,
            AssessmentStatus status,
            long submissions,
            BigDecimal averagePercentage,
            BigDecimal passRate
    ) {
    }

    public record StudentResultPoint(
            UUID attemptId,
            UUID assessmentId,
            String assessmentTitle,
            String studentName,
            Instant submittedAt,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            String grade,
            String trajectory
    ) {
    }

    public record StudentPerformanceReport(
            UUID studentUserId,
            String studentName,
            BigDecimal averagePercentage,
            BigDecimal bestPercentage,
            String trajectory,
            boolean atRisk,
            List<StudentResultPoint> results
    ) {
    }

    public record StudentReportRow(
            UUID studentUserId,
            String studentName,
            String studentEmail,
            UUID departmentId,
            String departmentName,
            UUID sectionId,
            String sectionName,
            long publishedResults,
            BigDecimal averagePercentage,
            BigDecimal bestPercentage,
            Instant latestSubmissionAt,
            String trajectory,
            boolean atRisk
    ) {
    }

    public record StudentGroupComparison(
            UUID groupId,
            String label,
            long studentCount,
            long publishedResults,
            BigDecimal averagePercentage,
            BigDecimal passRate
    ) {
    }

    public record StudentReport(
            long totalStudents,
            long studentsWithResults,
            long publishedResults,
            BigDecimal averagePercentage,
            long atRiskStudents,
            List<StudentReportRow> students,
            List<StudentGroupComparison> departments,
            List<StudentGroupComparison> sections
    ) {
    }

    public record QuestionPerformance(
            UUID questionId,
            String code,
            String stem,
            Difficulty difficulty,
            long usageCount,
            long responseCount,
            BigDecimal correctRate,
            BigDecimal difficultyIndex,
            BigDecimal discriminationIndex,
            boolean poorQuality
    ) {
    }

    public record AssessmentReport(
            UUID assessmentId,
            String title,
            long submissions,
            BigDecimal averagePercentage,
            BigDecimal highestPercentage,
            BigDecimal lowestPercentage,
            BigDecimal passRate,
            List<CountValue> scoreDistribution,
            List<StudentResultPoint> studentResults,
            List<QuestionPerformance> questionAnalytics,
            Instant generatedAt,
            String generatedBy
    ) {
    }

    public record FacultyPerformance(
            UUID facultyUserId,
            String facultyName,
            long questionsAuthored,
            long approvedQuestions,
            long assessmentsCreated,
            long studentSubmissions,
            BigDecimal averageStudentPercentage
    ) {
    }
}
