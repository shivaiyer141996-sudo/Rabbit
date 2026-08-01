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

    public record StudentAnalysisBreakdown(
            String key,
            String label,
            long questionCount,
            long answeredQuestions,
            long correctAnswers,
            BigDecimal awardedMarks,
            BigDecimal maxMarks,
            BigDecimal percentage,
            long averageTimeSeconds,
            boolean weak
    ) {
    }

    public record StudentAttemptTimeAnalysis(
            UUID attemptId,
            UUID assessmentId,
            String assessmentTitle,
            Instant submittedAt,
            long allowedSeconds,
            long timeTakenSeconds,
            BigDecimal utilisationPercentage,
            long averageQuestionSeconds,
            long slowestQuestionSeconds
    ) {
    }

    public record ReviewOption(
            UUID optionId,
            String label,
            String text
    ) {
    }

    public record StudentQuestionReview(
            UUID attemptId,
            UUID assessmentId,
            String assessmentTitle,
            Instant submittedAt,
            UUID questionId,
            String questionCode,
            String stem,
            String subjectName,
            String topicName,
            Difficulty difficulty,
            List<ReviewOption> selectedOptions,
            List<ReviewOption> correctOptions,
            BigDecimal awardedMarks,
            BigDecimal maxMarks,
            boolean answered,
            boolean correct,
            int timeSpentSeconds,
            String explanation
    ) {
    }

    public record StudentAnalyticsReport(
            UUID studentUserId,
            String studentName,
            long publishedAttempts,
            long analysedQuestions,
            BigDecimal averagePercentage,
            long totalTimeSeconds,
            List<StudentAnalysisBreakdown> subjects,
            List<StudentAnalysisBreakdown> topics,
            List<StudentAnalysisBreakdown> difficulties,
            List<StudentAttemptTimeAnalysis> timeAnalysis,
            List<StudentQuestionReview> questionReview,
            Instant generatedAt
    ) {
    }

    public record TeacherBatchAnalytics(
            UUID sectionId,
            String batchName,
            long studentCount,
            long assessmentCount,
            long submissionCount,
            long studentsAttempted,
            BigDecimal completionRate,
            BigDecimal averagePercentage,
            BigDecimal passRate
    ) {
    }

    public record TeacherStudentComparison(
            UUID studentUserId,
            String studentName,
            String batchName,
            long publishedAttempts,
            BigDecimal averagePercentage,
            BigDecimal bestPercentage,
            BigDecimal passRate,
            int rank,
            String trajectory,
            boolean atRisk
    ) {
    }

    public record TeacherWeakTopic(
            UUID subjectId,
            String subjectName,
            UUID topicId,
            String topicName,
            long questionCount,
            long responseCount,
            BigDecimal averageMarksPercentage,
            BigDecimal correctRate,
            long averageTimeSeconds,
            boolean weak
    ) {
    }

    public record TeacherAnalyticsReport(
            UUID teacherUserId,
            String teacherName,
            long assessmentCount,
            long publishedSubmissions,
            BigDecimal averagePercentage,
            long weakTopicCount,
            List<TeacherBatchAnalytics> batches,
            List<TeacherStudentComparison> students,
            List<TeacherWeakTopic> weakTopics,
            Instant generatedAt
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
