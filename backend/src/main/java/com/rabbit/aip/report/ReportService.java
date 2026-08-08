package com.rabbit.aip.report;

import com.rabbit.aip.assessment.Assessment;
import com.rabbit.aip.assessment.AssessmentRepository;
import com.rabbit.aip.assessment.AssessmentType;
import com.rabbit.aip.attempt.AssessmentAttempt;
import com.rabbit.aip.attempt.AssessmentAttemptRepository;
import com.rabbit.aip.attempt.AttemptResponse;
import com.rabbit.aip.attempt.AttemptResponseRepository;
import com.rabbit.aip.attempt.AttemptPresentationOrder;
import com.rabbit.aip.attempt.ResultPublicationStatus;
import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.question.Question;
import com.rabbit.aip.question.QuestionOption;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.question.QuestionStatus;
import com.rabbit.aip.report.ReportDtos.AssessmentReport;
import com.rabbit.aip.report.ReportDtos.AssessmentSnapshot;
import com.rabbit.aip.report.ReportDtos.FacultyPerformance;
import com.rabbit.aip.report.ReportDtos.IntelligenceOverview;
import com.rabbit.aip.report.ReportDtos.LabelValue;
import com.rabbit.aip.report.ReportDtos.QuestionPerformance;
import com.rabbit.aip.report.ReportDtos.StudentPerformanceReport;
import com.rabbit.aip.report.ReportDtos.StudentGroupComparison;
import com.rabbit.aip.report.ReportDtos.StudentAnalysisBreakdown;
import com.rabbit.aip.report.ReportDtos.StudentAnalyticsReport;
import com.rabbit.aip.report.ReportDtos.StudentAttemptTimeAnalysis;
import com.rabbit.aip.report.ReportDtos.StudentQuestionReview;
import com.rabbit.aip.report.ReportDtos.StudentReport;
import com.rabbit.aip.report.ReportDtos.StudentReportRow;
import com.rabbit.aip.report.ReportDtos.StudentResultPoint;
import com.rabbit.aip.report.ReportDtos.ReviewOption;
import com.rabbit.aip.report.ReportDtos.TeacherAnalyticsReport;
import com.rabbit.aip.report.ReportDtos.TeacherBatchAnalytics;
import com.rabbit.aip.report.ReportDtos.TeacherStudentComparison;
import com.rabbit.aip.report.ReportDtos.TeacherWeakTopic;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.settings.AcademicSubject;
import com.rabbit.aip.settings.AcademicSubjectRepository;
import com.rabbit.aip.settings.AcademicTopic;
import com.rabbit.aip.settings.AcademicTopicRepository;
import com.rabbit.aip.settings.OrganisationSettings;
import com.rabbit.aip.settings.OrganisationSettingsRepository;
import com.rabbit.aip.user.AccountStatus;
import com.rabbit.aip.user.OrganisationMembership;
import com.rabbit.aip.user.OrganisationMembershipRepository;
import com.rabbit.aip.user.UserAccount;
import com.rabbit.aip.user.UserAccountRepository;
import com.rabbit.aip.user.UserRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
public class ReportService {

    private final AssessmentRepository assessments;
    private final AssessmentAttemptRepository attempts;
    private final AttemptResponseRepository responses;
    private final QuestionRepository questions;
    private final UserAccountRepository users;
    private final OrganisationMembershipRepository memberships;
    private final OrganisationSettingsRepository settings;
    private final AcademicSubjectRepository subjects;
    private final AcademicTopicRepository topics;
    private final CurrentSession session;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public ReportService(
            AssessmentRepository assessments,
            AssessmentAttemptRepository attempts,
            AttemptResponseRepository responses,
            QuestionRepository questions,
            UserAccountRepository users,
            OrganisationMembershipRepository memberships,
            OrganisationSettingsRepository settings,
            AcademicSubjectRepository subjects,
            AcademicTopicRepository topics,
            CurrentSession session,
            AuditService audit,
            JdbcTemplate jdbc
    ) {
        this.assessments = assessments;
        this.attempts = attempts;
        this.responses = responses;
        this.questions = questions;
        this.users = users;
        this.memberships = memberships;
        this.settings = settings;
        this.subjects = subjects;
        this.topics = topics;
        this.session = session;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public IntelligenceOverview overview() {
        UUID organisationId = session.organisationId();
        List<Assessment> visibleAssessments = organisationAssessments().values().stream()
                .toList();
        List<AssessmentAttempt> published = publishedAttempts();
        BigDecimal passMark = organisationSettings().getPassPercentage();
        BigDecimal atRiskThreshold = organisationSettings().getAtRiskThreshold();
        List<BigDecimal> percentages = percentages(published);
        long passed = percentages.stream()
                .filter(value -> value.compareTo(passMark) >= 0)
                .count();
        List<OrganisationMembership> students = memberships
                .findAllByOrganisationIdOrderByCreatedAtDesc(organisationId)
                .stream()
                .filter(item -> item.getRole() == UserRole.STUDENT)
                .toList();
        Map<UUID, List<AssessmentAttempt>> publishedByStudent = published.stream()
                .sorted(Comparator.comparing(AssessmentAttempt::getSubmittedAt))
                .collect(Collectors.groupingBy(AssessmentAttempt::getStudentUserId));
        long atRisk = students.stream()
                .filter(student -> isAtRisk(
                        percentages(publishedByStudent.getOrDefault(
                                student.getUserId(), List.of()
                        )),
                        atRiskThreshold
                ))
                .count();
        Set<UUID> visibleAssessmentIds = visibleAssessments.stream()
                .map(Assessment::getId)
                .collect(Collectors.toSet());
        long totalSubmitted = attempts.findAllByOrganisationIdOrderBySubmittedAtDesc(
                        organisationId
                ).stream()
                .filter(item -> visibleAssessmentIds.contains(item.getAssessmentId()))
                .filter(item -> item.getSubmittedAt() != null)
                .count();
        long possible = visibleAssessments.stream()
                .filter(item -> item.getEndAt() != null)
                .count() * Math.max(1, students.size());
        return new IntelligenceOverview(
                published.size(),
                ReportMath.average(percentages),
                ReportMath.percentage(passed, percentages.size()),
                atRisk,
                ReportMath.percentage(totalSubmitted, possible),
                ReportMath.distribution(percentages),
                monthlyTrend(published),
                visibleAssessments.stream()
                        .limit(5)
                        .map(this::snapshot)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public AssessmentReport assessment(UUID assessmentId) {
        Assessment assessment = findAssessment(assessmentId);
        List<AssessmentAttempt> published = publishedAttempts().stream()
                .filter(item -> item.getAssessmentId().equals(assessmentId))
                .toList();
        List<BigDecimal> percentages = percentages(published);
        BigDecimal passMark = organisationSettings().getPassPercentage();
        long passed = percentages.stream()
                .filter(value -> value.compareTo(passMark) >= 0)
                .count();
        Map<UUID, String> studentNames = users.findAllById(
                        published.stream()
                                .map(AssessmentAttempt::getStudentUserId)
                                .distinct()
                                .toList()
                ).stream()
                .collect(Collectors.toMap(
                        UserAccount::getId,
                        user -> user.getFirstName() + " " + user.getLastName()
                ));
        List<StudentResultPoint> studentResults = published.stream()
                .sorted(Comparator.comparing(AssessmentAttempt::getSubmittedAt))
                .map(item -> resultPoint(
                        item,
                        assessment,
                        "STABLE",
                        studentNames.getOrDefault(item.getStudentUserId(), "Unknown student")
                ))
                .toList();
        return new AssessmentReport(
                assessmentId,
                assessment.getTitle(),
                published.size(),
                ReportMath.average(percentages),
                percentages.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
                percentages.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
                ReportMath.percentage(passed, percentages.size()),
                ReportMath.distribution(percentages),
                studentResults,
                questionAnalytics(assessment, published),
                Instant.now(),
                session.email()
        );
    }

    @Transactional(readOnly = true)
    public StudentPerformanceReport myPerformance() {
        return student(session.userId());
    }

    @Transactional(readOnly = true)
    public StudentPerformanceReport student(UUID studentUserId) {
        if (session.role() == UserRole.STUDENT
                && !session.userId().equals(studentUserId)) {
            throw DomainException.forbidden(
                    "STUDENT_REPORT_ACCESS_DENIED",
                    "Students can only view their own performance."
            );
        }
        memberships
                .findByUserIdAndOrganisationIdAndStatus(
                        studentUserId,
                        session.organisationId(),
                        AccountStatus.ACTIVE
                )
                .filter(item -> item.getRole() == UserRole.STUDENT)
                .orElseThrow(() -> DomainException.notFound(
                        "STUDENT_NOT_FOUND", "Student was not found."
                ));
        UserAccount student = users.findById(studentUserId)
                .orElseThrow(() -> DomainException.notFound(
                        "STUDENT_NOT_FOUND", "Student was not found."
                ));
        Map<UUID, Assessment> assessmentMap = organisationAssessments();
        List<AssessmentAttempt> history = attempts
                .findAllByOrganisationIdAndStudentUserIdOrderBySubmittedAtAsc(
                        session.organisationId(), studentUserId
                ).stream()
                .filter(item -> item.getResultStatus() == ResultPublicationStatus.PUBLISHED)
                .filter(item -> item.getPercentage() != null)
                .filter(item -> assessmentMap.containsKey(item.getAssessmentId()))
                .toList();
        List<BigDecimal> percentages = percentages(history);
        String trajectory = ReportMath.trajectory(percentages);
        List<StudentResultPoint> points = new ArrayList<>();
        for (int index = 0; index < history.size(); index++) {
            AssessmentAttempt attempt = history.get(index);
            Assessment assessment = assessmentMap.get(attempt.getAssessmentId());
            if (assessment == null) continue;
            List<BigDecimal> untilNow = percentages.subList(0, index + 1);
            points.add(resultPoint(
                    attempt,
                    assessment,
                    ReportMath.trajectory(untilNow),
                    student.getFirstName() + " " + student.getLastName()
            ));
        }
        BigDecimal passMark = organisationSettings().getAtRiskThreshold();
        return new StudentPerformanceReport(
                studentUserId,
                student.getFirstName() + " " + student.getLastName(),
                ReportMath.average(percentages),
                percentages.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
                trajectory,
                isAtRisk(percentages, passMark),
                points
        );
    }

    @Transactional(readOnly = true)
    public StudentAnalyticsReport studentAnalytics(UUID studentUserId) {
        StudentPerformanceReport performance = student(studentUserId);
        Map<UUID, Assessment> assessmentMap = organisationAssessments();
        List<AssessmentAttempt> history = attempts
                .findAllByOrganisationIdAndStudentUserIdOrderBySubmittedAtAsc(
                        session.organisationId(), studentUserId
                ).stream()
                .filter(item -> item.getResultStatus() == ResultPublicationStatus.PUBLISHED)
                .filter(item -> item.getSubmittedAt() != null)
                .filter(item -> assessmentMap.containsKey(item.getAssessmentId()))
                .sorted(Comparator.comparing(
                        AssessmentAttempt::getSubmittedAt,
                        Comparator.reverseOrder()
                ))
                .toList();
        List<ResponseFact> facts = responseFacts(history);
        BigDecimal weakThreshold = organisationSettings().getAtRiskThreshold();
        Map<UUID, List<ResponseFact>> byAttempt = facts.stream()
                .collect(Collectors.groupingBy(
                        fact -> fact.attempt().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<StudentAttemptTimeAnalysis> timeAnalysis = history.stream()
                .map(attempt -> {
                    Assessment assessment = assessmentMap.get(attempt.getAssessmentId());
                    List<ResponseFact> attemptFacts = byAttempt.getOrDefault(
                            attempt.getId(), List.of()
                    );
                    long allowedSeconds = assessment == null
                            ? 0
                            : assessment.getDurationMinutes() * 60L;
                    long timeTaken = Math.max(
                            0,
                            Duration.between(
                                    attempt.getStartedAt(), attempt.getSubmittedAt()
                            ).toSeconds()
                    );
                    long averageQuestion = Math.round(attemptFacts.stream()
                            .mapToInt(ResponseFact::timeSpentSeconds)
                            .average()
                            .orElse(0));
                    long slowestQuestion = attemptFacts.stream()
                            .mapToInt(ResponseFact::timeSpentSeconds)
                            .max()
                            .orElse(0);
                    return new StudentAttemptTimeAnalysis(
                            attempt.getId(),
                            attempt.getAssessmentId(),
                            assessment == null
                                    ? "Unknown assessment"
                                    : assessment.getTitle(),
                            attempt.getSubmittedAt(),
                            allowedSeconds,
                            timeTaken,
                            percentage(timeTaken, allowedSeconds),
                            averageQuestion,
                            slowestQuestion
                    );
                })
                .toList();
        return new StudentAnalyticsReport(
                studentUserId,
                performance.studentName(),
                history.size(),
                facts.size(),
                performance.averagePercentage(),
                timeAnalysis.stream()
                        .mapToLong(StudentAttemptTimeAnalysis::timeTakenSeconds)
                        .sum(),
                breakdown(
                        facts,
                        fact -> fact.question().getSubjectId().toString(),
                        ResponseFact::subjectName,
                        weakThreshold
                ),
                breakdown(
                        facts,
                        fact -> fact.question().getTopicId().toString(),
                        ResponseFact::topicName,
                        weakThreshold
                ),
                breakdown(
                        facts,
                        fact -> fact.question().getSubTopic() == null
                                ? "UNSPECIFIED" : fact.question().getSubTopic(),
                        fact -> fact.question().getSubTopic() == null
                                ? "Unspecified chapter" : fact.question().getSubTopic(),
                        weakThreshold
                ),
                breakdown(
                        facts,
                        fact -> fact.question().getDifficulty().name(),
                        fact -> fact.question().getDifficulty().name(),
                        weakThreshold
                ),
                breakdown(
                        facts,
                        fact -> fact.question().getBloomLevel().name(),
                        fact -> fact.question().getBloomLevel().name(),
                        weakThreshold
                ),
                timeAnalysis,
                facts.stream().map(this::questionReview).toList(),
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public StudentAnalyticsReport myAnalytics() {
        return studentAnalytics(session.userId());
    }

    @Transactional(readOnly = true)
    public StudentReport students(
            String query,
            UUID subjectId,
            AssessmentType assessmentType,
            UUID departmentId,
            UUID sectionId,
            Instant submittedFrom,
            Instant submittedBefore
    ) {
        UUID organisationId = session.organisationId();
        Map<UUID, SectionContext> sectionContexts = sectionContexts(organisationId);
        Map<UUID, UserAccount> userMap = users.findAllById(
                        memberships.findAllByOrganisationIdOrderByCreatedAtDesc(
                                        organisationId
                                ).stream()
                                .map(OrganisationMembership::getUserId)
                                .toList()
                ).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        String normalizedQuery = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);
        List<OrganisationMembership> studentMemberships = memberships
                .findAllByOrganisationIdOrderByCreatedAtDesc(organisationId)
                .stream()
                .filter(item -> item.getRole() == UserRole.STUDENT)
                .filter(item -> item.getStatus() == AccountStatus.ACTIVE)
                .filter(item -> sectionId == null || sectionId.equals(item.getSectionId()))
                .filter(item -> departmentId == null || departmentId.equals(
                        sectionContext(sectionContexts, item).departmentId()
                ))
                .filter(item -> matchesStudentQuery(
                        userMap.get(item.getUserId()), normalizedQuery
                ))
                .toList();
        Set<UUID> studentIds = studentMemberships.stream()
                .map(OrganisationMembership::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, Assessment> assessmentMap = organisationAssessments();
        List<AssessmentAttempt> filteredAttempts = publishedAttempts().stream()
                .filter(item -> studentIds.contains(item.getStudentUserId()))
                .filter(item -> {
                    Assessment assessment = assessmentMap.get(item.getAssessmentId());
                    if (assessment == null) return false;
                    if (subjectId != null && !assessment.getSubjectIds().contains(subjectId)) {
                        return false;
                    }
                    if (assessmentType != null && assessmentType != assessment.getType()) {
                        return false;
                    }
                    if (submittedFrom != null
                            && item.getSubmittedAt().isBefore(submittedFrom)) {
                        return false;
                    }
                    return submittedBefore == null
                            || item.getSubmittedAt().isBefore(submittedBefore);
                })
                .sorted(Comparator.comparing(AssessmentAttempt::getSubmittedAt))
                .toList();
        Map<UUID, List<AssessmentAttempt>> attemptsByStudent = filteredAttempts.stream()
                .collect(Collectors.groupingBy(AssessmentAttempt::getStudentUserId));
        BigDecimal atRiskThreshold = organisationSettings().getAtRiskThreshold();
        List<StudentReportRow> rows = studentMemberships.stream()
                .map(membership -> studentReportRow(
                        membership,
                        userMap.get(membership.getUserId()),
                        sectionContext(sectionContexts, membership),
                        attemptsByStudent.getOrDefault(membership.getUserId(), List.of()),
                        atRiskThreshold
                ))
                .sorted(Comparator.comparing(StudentReportRow::studentName))
                .toList();
        List<BigDecimal> allPercentages = percentages(filteredAttempts);
        BigDecimal passMark = organisationSettings().getPassPercentage();
        return new StudentReport(
                rows.size(),
                rows.stream().filter(item -> item.publishedResults() > 0).count(),
                filteredAttempts.size(),
                ReportMath.average(allPercentages),
                rows.stream().filter(StudentReportRow::atRisk).count(),
                rows,
                groupComparisons(
                        studentMemberships,
                        filteredAttempts,
                        sectionContexts,
                        passMark,
                        true
                ),
                groupComparisons(
                        studentMemberships,
                        filteredAttempts,
                        sectionContexts,
                        passMark,
                        false
                )
        );
    }

    @Transactional(readOnly = true)
    public List<QuestionPerformance> questionAnalytics() {
        List<AssessmentAttempt> published = publishedAttempts();
        Map<UUID, Assessment> assessmentMap = organisationAssessments();
        Map<UUID, List<AssessmentAttempt>> attemptsByAssessment = published.stream()
                .collect(Collectors.groupingBy(AssessmentAttempt::getAssessmentId));
        Map<UUID, QuestionPerformance> result = new LinkedHashMap<>();
        questions.findAllByOrganisationIdOrderByUpdatedAtDesc(session.organisationId())
                .forEach(question -> {
                    List<AssessmentAttempt> relevant = new ArrayList<>();
                    assessmentMap.values().stream()
                            .filter(item -> item.getQuestionIds().contains(question.getId()))
                            .forEach(item -> relevant.addAll(
                                    attemptsByAssessment.getOrDefault(item.getId(), List.of())
                            ));
                    result.put(
                            question.getId(),
                            questionPerformance(question, relevant)
                    );
                });
        return List.copyOf(result.values());
    }

    @Transactional(readOnly = true)
    public List<FacultyPerformance> facultyPerformance() {
        UUID organisationId = session.organisationId();
        List<Question> allQuestions = questions
                .findAllByOrganisationIdOrderByUpdatedAtDesc(organisationId);
        List<Assessment> allAssessments = assessments
                .findAllByOrganisationIdOrderByUpdatedAtDesc(organisationId);
        List<AssessmentAttempt> published = publishedAttempts();
        return memberships.findAllByOrganisationIdOrderByCreatedAtDesc(organisationId)
                .stream()
                .filter(item -> item.getRole() == UserRole.FACULTY
                        || item.getRole() == UserRole.ACADEMIC_HEAD)
                .map(membership -> {
                    UserAccount user = users.findById(membership.getUserId()).orElseThrow();
                    List<Assessment> authoredAssessments = allAssessments.stream()
                            .filter(item -> item.getCreatedBy().equals(user.getId()))
                            .toList();
                    List<AssessmentAttempt> facultyAttempts = published.stream()
                            .filter(attempt -> authoredAssessments.stream().anyMatch(
                                    assessment -> assessment.getId()
                                            .equals(attempt.getAssessmentId())
                            ))
                            .toList();
                    List<Question> authoredQuestions = allQuestions.stream()
                            .filter(item -> item.getAuthorUserId().equals(user.getId()))
                            .toList();
                    return new FacultyPerformance(
                            user.getId(),
                            user.getFirstName() + " " + user.getLastName(),
                            authoredQuestions.size(),
                            authoredQuestions.stream()
                                    .filter(item -> item.getStatus() == QuestionStatus.APPROVED
                                            || item.getStatus() == QuestionStatus.PUBLISHED)
                                    .count(),
                            authoredAssessments.size(),
                            facultyAttempts.size(),
                            ReportMath.average(percentages(facultyAttempts))
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherAnalyticsReport teacherAnalytics(UUID requestedTeacherUserId) {
        TeacherScope scope = teacherScope(requestedTeacherUserId);
        UUID organisationId = session.organisationId();
        List<Assessment> scopedAssessments = assessments
                .findAllByOrganisationIdOrderByUpdatedAtDesc(organisationId)
                .stream()
                .filter(item -> scope.teacherUserId() == null
                        || item.getCreatedBy().equals(scope.teacherUserId()))
                .toList();
        Set<UUID> assessmentIds = scopedAssessments.stream()
                .map(Assessment::getId)
                .collect(Collectors.toSet());
        List<AssessmentAttempt> published = publishedAttempts().stream()
                .filter(item -> assessmentIds.contains(item.getAssessmentId()))
                .toList();
        List<OrganisationMembership> activeStudents = memberships
                .findAllByOrganisationIdOrderByCreatedAtDesc(organisationId)
                .stream()
                .filter(item -> item.getRole() == UserRole.STUDENT)
                .filter(item -> item.getStatus() == AccountStatus.ACTIVE)
                .toList();
        Map<UUID, OrganisationMembership> membershipByStudent = activeStudents.stream()
                .collect(Collectors.toMap(
                        OrganisationMembership::getUserId,
                        Function.identity(),
                        (left, right) -> left
                ));
        Map<UUID, SectionContext> sectionMap = sectionContexts(organisationId);
        Map<UUID, UserAccount> userMap = users.findAllById(
                        activeStudents.stream()
                                .map(OrganisationMembership::getUserId)
                                .toList()
                ).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        BigDecimal passMark = organisationSettings().getPassPercentage();
        BigDecimal weakThreshold = organisationSettings().getAtRiskThreshold();

        Map<SectionContext, List<OrganisationMembership>> studentsByBatch =
                activeStudents.stream().collect(Collectors.groupingBy(
                        item -> sectionContext(sectionMap, item)
                ));
        List<TeacherBatchAnalytics> batches = studentsByBatch.entrySet().stream()
                .map(entry -> batchAnalytics(
                        entry.getKey(),
                        entry.getValue(),
                        scopedAssessments,
                        published,
                        passMark
                ))
                .filter(item -> item.assessmentCount() > 0
                        || item.submissionCount() > 0)
                .sorted(Comparator.comparing(TeacherBatchAnalytics::batchName))
                .toList();

        Map<UUID, List<AssessmentAttempt>> attemptsByStudent = published.stream()
                .collect(Collectors.groupingBy(AssessmentAttempt::getStudentUserId));
        List<StudentComparisonDraft> comparisonDrafts = attemptsByStudent.entrySet()
                .stream()
                .map(entry -> studentComparisonDraft(
                        entry.getKey(),
                        entry.getValue(),
                        userMap.get(entry.getKey()),
                        membershipByStudent.get(entry.getKey()),
                        sectionMap,
                        passMark,
                        weakThreshold
                ))
                .sorted(Comparator
                        .comparing(
                                StudentComparisonDraft::averagePercentage,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(StudentComparisonDraft::studentName))
                .toList();
        List<TeacherStudentComparison> studentComparisons = new ArrayList<>();
        for (int index = 0; index < comparisonDrafts.size(); index++) {
            StudentComparisonDraft item = comparisonDrafts.get(index);
            studentComparisons.add(new TeacherStudentComparison(
                    item.studentUserId(),
                    item.studentName(),
                    item.batchName(),
                    item.publishedAttempts(),
                    item.averagePercentage(),
                    item.bestPercentage(),
                    item.passRate(),
                    index + 1,
                    item.trajectory(),
                    item.atRisk()
            ));
        }

        List<ResponseFact> facts = responseFacts(published);
        List<TeacherWeakTopic> weakTopics = teacherTopicAnalytics(
                facts, weakThreshold
        );
        return new TeacherAnalyticsReport(
                scope.teacherUserId(),
                scope.teacherName(),
                scopedAssessments.size(),
                published.size(),
                ReportMath.average(percentages(published)),
                weakTopics.stream().filter(TeacherWeakTopic::weak).count(),
                batches,
                studentComparisons,
                weakTopics,
                Instant.now()
        );
    }

    private TeacherScope teacherScope(UUID requestedTeacherUserId) {
        UUID teacherUserId = requestedTeacherUserId;
        if (session.role() == UserRole.FACULTY) {
            if (requestedTeacherUserId != null
                    && !requestedTeacherUserId.equals(session.userId())) {
                throw DomainException.forbidden(
                        "TEACHER_REPORT_ACCESS_DENIED",
                        "Teachers can only view their own analytics."
                );
            }
            teacherUserId = session.userId();
        }
        if (teacherUserId == null) {
            return new TeacherScope(null, "All teachers");
        }
        OrganisationMembership membership = memberships
                .findByUserIdAndOrganisationIdAndStatus(
                        teacherUserId, session.organisationId(), AccountStatus.ACTIVE
                )
                .filter(item -> item.getRole() == UserRole.FACULTY
                        || item.getRole() == UserRole.ACADEMIC_HEAD)
                .orElseThrow(() -> DomainException.notFound(
                        "TEACHER_NOT_FOUND",
                        "The selected teacher was not found in this organisation."
                ));
        UserAccount user = users.findById(membership.getUserId())
                .orElseThrow(() -> DomainException.notFound(
                        "TEACHER_NOT_FOUND", "The selected teacher was not found."
                ));
        return new TeacherScope(
                teacherUserId,
                user.getFirstName() + " " + user.getLastName()
        );
    }

    private TeacherBatchAnalytics batchAnalytics(
            SectionContext context,
            List<OrganisationMembership> batchStudents,
            List<Assessment> scopedAssessments,
            List<AssessmentAttempt> published,
            BigDecimal passMark
    ) {
        List<Assessment> batchAssessments = scopedAssessments.stream()
                .filter(item -> item.getStartAt() != null)
                .filter(item -> item.getEligibleSectionIds().isEmpty()
                        || context.sectionId() != null
                        && item.getEligibleSectionIds().contains(context.sectionId()))
                .toList();
        Set<UUID> assessmentIds = batchAssessments.stream()
                .map(Assessment::getId)
                .collect(Collectors.toSet());
        Set<UUID> studentIds = batchStudents.stream()
                .map(OrganisationMembership::getUserId)
                .collect(Collectors.toSet());
        List<AssessmentAttempt> batchAttempts = published.stream()
                .filter(item -> assessmentIds.contains(item.getAssessmentId()))
                .filter(item -> studentIds.contains(item.getStudentUserId()))
                .toList();
        List<BigDecimal> values = percentages(batchAttempts);
        long passed = values.stream()
                .filter(item -> item.compareTo(passMark) >= 0)
                .count();
        long completedPairs = batchAttempts.stream()
                .map(item -> new StudentAssessmentKey(
                        item.getStudentUserId(), item.getAssessmentId()
                ))
                .distinct()
                .count();
        long possiblePairs = (long) batchStudents.size() * batchAssessments.size();
        return new TeacherBatchAnalytics(
                context.sectionId(),
                context.departmentName() + " · " + context.sectionName(),
                batchStudents.size(),
                batchAssessments.size(),
                batchAttempts.size(),
                batchAttempts.stream()
                        .map(AssessmentAttempt::getStudentUserId)
                        .distinct()
                        .count(),
                ReportMath.percentage(completedPairs, possiblePairs),
                ReportMath.average(values),
                ReportMath.percentage(passed, values.size())
        );
    }

    private StudentComparisonDraft studentComparisonDraft(
            UUID studentUserId,
            List<AssessmentAttempt> studentAttempts,
            UserAccount user,
            OrganisationMembership membership,
            Map<UUID, SectionContext> sectionMap,
            BigDecimal passMark,
            BigDecimal weakThreshold
    ) {
        List<AssessmentAttempt> ordered = studentAttempts.stream()
                .sorted(Comparator.comparing(AssessmentAttempt::getSubmittedAt))
                .toList();
        List<BigDecimal> values = percentages(ordered);
        long passed = values.stream()
                .filter(item -> item.compareTo(passMark) >= 0)
                .count();
        SectionContext context = membership == null
                ? new SectionContext(null, "Unassigned", null, "Unassigned")
                : sectionContext(sectionMap, membership);
        return new StudentComparisonDraft(
                studentUserId,
                user == null
                        ? "Unknown student"
                        : user.getFirstName() + " " + user.getLastName(),
                context.departmentName() + " · " + context.sectionName(),
                ordered.size(),
                ReportMath.average(values),
                values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
                ReportMath.percentage(passed, values.size()),
                ReportMath.trajectory(values),
                isAtRisk(values, weakThreshold)
        );
    }

    private List<TeacherWeakTopic> teacherTopicAnalytics(
            List<ResponseFact> facts,
            BigDecimal weakThreshold
    ) {
        Map<AnalysisKey, List<ResponseFact>> grouped = facts.stream()
                .collect(Collectors.groupingBy(
                        fact -> new AnalysisKey(
                                fact.question().getTopicId().toString(),
                                fact.topicName()
                        ),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return grouped.entrySet().stream()
                .map(entry -> {
                    List<ResponseFact> rows = entry.getValue();
                    BigDecimal awarded = rows.stream()
                            .map(ResponseFact::awardedMarks)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal maximum = rows.stream()
                            .map(item -> item.question().getMarks())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal marksPercentage = percentage(awarded, maximum);
                    ResponseFact first = rows.get(0);
                    return new TeacherWeakTopic(
                            first.question().getSubjectId(),
                            first.subjectName(),
                            first.question().getTopicId(),
                            first.topicName(),
                            rows.stream()
                                    .map(item -> item.question().getId())
                                    .distinct()
                                    .count(),
                            rows.stream().filter(ResponseFact::answered).count(),
                            marksPercentage,
                            ReportMath.percentage(
                                    rows.stream().filter(ResponseFact::correct).count(),
                                    rows.size()
                            ),
                            Math.round(rows.stream()
                                    .mapToInt(ResponseFact::timeSpentSeconds)
                                    .average()
                                    .orElse(0)),
                            maximum.signum() > 0
                                    && marksPercentage.compareTo(weakThreshold) < 0
                    );
                })
                .sorted(Comparator
                        .comparing(TeacherWeakTopic::weak)
                        .reversed()
                        .thenComparing(TeacherWeakTopic::averageMarksPercentage)
                        .thenComparing(TeacherWeakTopic::topicName))
                .toList();
    }

    private List<ResponseFact> responseFacts(List<AssessmentAttempt> sourceAttempts) {
        if (sourceAttempts.isEmpty()) return List.of();
        Map<UUID, Assessment> assessmentMap = organisationAssessments();
        Set<UUID> questionIds = sourceAttempts.stream()
                .map(item -> assessmentMap.get(item.getAssessmentId()))
                .filter(java.util.Objects::nonNull)
                .flatMap(item -> item.getQuestionIds().stream())
                .collect(Collectors.toSet());
        Map<UUID, Question> questionMap = questions
                .findAllByIdInAndOrganisationId(questionIds, session.organisationId())
                .stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<AttemptQuestionKey, AttemptResponse> responseMap = responses
                .findAllByAttemptIdIn(
                        sourceAttempts.stream().map(AssessmentAttempt::getId).toList()
                ).stream()
                .collect(Collectors.toMap(
                        item -> new AttemptQuestionKey(
                                item.getAttemptId(), item.getQuestionId()
                        ),
                        Function.identity(),
                        (left, right) -> right
                ));
        Map<UUID, String> subjectNames = subjects
                .findAllByOrganisationIdOrderByName(session.organisationId())
                .stream()
                .collect(Collectors.toMap(
                        AcademicSubject::getId, AcademicSubject::getName
                ));
        Map<UUID, String> topicNames = topics
                .findAllByOrganisationIdOrderByName(session.organisationId())
                .stream()
                .collect(Collectors.toMap(
                        AcademicTopic::getId, AcademicTopic::getName
                ));
        List<ResponseFact> result = new ArrayList<>();
        sourceAttempts.forEach(attempt -> {
            Assessment assessment = assessmentMap.get(attempt.getAssessmentId());
            if (assessment == null) return;
            List<Question> orderedQuestions = AttemptPresentationOrder.order(
                    attempt.getId(),
                    "questions",
                    assessment.getQuestionIds().stream()
                            .map(questionMap::get)
                            .filter(java.util.Objects::nonNull)
                            .toList(),
                    Question::getId,
                    assessment.isShuffleQuestions()
            );
            orderedQuestions.forEach(question -> {
                result.add(new ResponseFact(
                        attempt,
                        assessment,
                        question,
                        responseMap.get(new AttemptQuestionKey(
                                attempt.getId(), question.getId()
                        )),
                        subjectNames.getOrDefault(
                                question.getSubjectId(), "Unknown subject"
                        ),
                        topicNames.getOrDefault(
                                question.getTopicId(), "Unknown topic"
                        )
                ));
            });
        });
        return List.copyOf(result);
    }

    private List<StudentAnalysisBreakdown> breakdown(
            List<ResponseFact> facts,
            Function<ResponseFact, String> key,
            Function<ResponseFact, String> label,
            BigDecimal weakThreshold
    ) {
        Map<AnalysisKey, List<ResponseFact>> grouped = facts.stream()
                .collect(Collectors.groupingBy(
                        fact -> new AnalysisKey(key.apply(fact), label.apply(fact)),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return grouped.entrySet().stream()
                .map(entry -> {
                    List<ResponseFact> rows = entry.getValue();
                    BigDecimal awarded = rows.stream()
                            .map(ResponseFact::awardedMarks)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal maximum = rows.stream()
                            .map(item -> item.question().getMarks())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal resultPercentage = percentage(awarded, maximum);
                    return new StudentAnalysisBreakdown(
                            entry.getKey().key(),
                            entry.getKey().label(),
                            rows.size(),
                            rows.stream().filter(ResponseFact::answered).count(),
                            rows.stream().filter(ResponseFact::correct).count(),
                            awarded.setScale(2, RoundingMode.HALF_UP),
                            maximum.setScale(2, RoundingMode.HALF_UP),
                            resultPercentage,
                            Math.round(rows.stream()
                                    .mapToInt(ResponseFact::timeSpentSeconds)
                                    .average()
                                    .orElse(0)),
                            maximum.signum() > 0
                                    && resultPercentage.compareTo(weakThreshold) < 0
                    );
                })
                .sorted(Comparator.comparing(StudentAnalysisBreakdown::label))
                .toList();
    }

    private StudentQuestionReview questionReview(ResponseFact fact) {
        Set<UUID> selected = fact.response() == null
                ? Set.of()
                : fact.response().getSelectedOptionIds();
        List<QuestionOption> sourceOptions = fact.question().getOptions().stream()
                .sorted(Comparator.comparingInt(QuestionOption::getSortOrder))
                .toList();
        List<QuestionOption> orderedOptions = AttemptPresentationOrder.order(
                fact.attempt().getId(),
                "options:" + fact.question().getId(),
                sourceOptions,
                QuestionOption::getId,
                fact.assessment().isShuffleOptions()
        );
        List<ReviewOption> selectedOptions = IntStream
                .range(0, orderedOptions.size())
                .filter(index -> selected.contains(orderedOptions.get(index).getId()))
                .mapToObj(index -> reviewOption(
                        orderedOptions.get(index),
                        index,
                        fact.assessment().isShuffleOptions()
                ))
                .toList();
        List<ReviewOption> correctOptions = IntStream
                .range(0, orderedOptions.size())
                .filter(index -> orderedOptions.get(index).isCorrect())
                .mapToObj(index -> reviewOption(
                        orderedOptions.get(index),
                        index,
                        fact.assessment().isShuffleOptions()
                ))
                .toList();
        return new StudentQuestionReview(
                fact.attempt().getId(),
                fact.assessment().getId(),
                fact.assessment().getTitle(),
                fact.attempt().getSubmittedAt(),
                fact.question().getId(),
                fact.question().getCode(),
                fact.question().getStem(),
                fact.subjectName(),
                fact.topicName(),
                fact.question().getDifficulty(),
                selectedOptions,
                correctOptions,
                fact.awardedMarks(),
                fact.question().getMarks(),
                fact.answered(),
                fact.correct(),
                fact.timeSpentSeconds(),
                fact.question().getExplanation()
        );
    }

    private ReviewOption reviewOption(
            QuestionOption option,
            int index,
            boolean shuffled
    ) {
        String label = shuffled
                ? String.valueOf((char) ('A' + index))
                : option.getLabel();
        return new ReviewOption(option.getId(), label, option.getText());
    }

    private StudentReportRow studentReportRow(
            OrganisationMembership membership,
            UserAccount user,
            SectionContext context,
            List<AssessmentAttempt> history,
            BigDecimal atRiskThreshold
    ) {
        List<BigDecimal> resultPercentages = percentages(history);
        String name = user == null
                ? "Unknown student"
                : user.getFirstName() + " " + user.getLastName();
        return new StudentReportRow(
                membership.getUserId(),
                name,
                user == null ? "" : user.getEmail(),
                context.departmentId(),
                context.departmentName(),
                context.sectionId(),
                context.sectionName(),
                history.size(),
                ReportMath.average(resultPercentages),
                resultPercentages.stream()
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO),
                history.stream()
                        .map(AssessmentAttempt::getSubmittedAt)
                        .max(Instant::compareTo)
                        .orElse(null),
                ReportMath.trajectory(resultPercentages),
                isAtRisk(resultPercentages, atRiskThreshold)
        );
    }

    private List<StudentGroupComparison> groupComparisons(
            List<OrganisationMembership> studentMemberships,
            List<AssessmentAttempt> filteredAttempts,
            Map<UUID, SectionContext> sectionContexts,
            BigDecimal passMark,
            boolean departments
    ) {
        Map<GroupKey, List<OrganisationMembership>> grouped = studentMemberships.stream()
                .collect(Collectors.groupingBy(membership -> {
                    SectionContext context = sectionContext(sectionContexts, membership);
                    return departments
                            ? new GroupKey(
                                    context.departmentId(), context.departmentName()
                            )
                            : new GroupKey(
                                    context.sectionId(),
                                    context.departmentName() + " · " + context.sectionName()
                            );
                }));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(GroupKey::label)
                ))
                .map(entry -> {
                    Set<UUID> studentIds = entry.getValue().stream()
                            .map(OrganisationMembership::getUserId)
                            .collect(Collectors.toSet());
                    List<AssessmentAttempt> groupAttempts = filteredAttempts.stream()
                            .filter(item -> studentIds.contains(item.getStudentUserId()))
                            .toList();
                    List<BigDecimal> groupPercentages = percentages(groupAttempts);
                    long passed = groupPercentages.stream()
                            .filter(value -> value.compareTo(passMark) >= 0)
                            .count();
                    return new StudentGroupComparison(
                            entry.getKey().id(),
                            entry.getKey().label(),
                            entry.getValue().size(),
                            groupAttempts.size(),
                            ReportMath.average(groupPercentages),
                            ReportMath.percentage(passed, groupPercentages.size())
                    );
                })
                .toList();
    }

    private boolean matchesStudentQuery(UserAccount user, String normalizedQuery) {
        if (normalizedQuery.isBlank()) return true;
        if (user == null) return false;
        String searchable = (user.getFirstName() + " " + user.getLastName()
                + " " + user.getEmail()).toLowerCase(Locale.ROOT);
        return searchable.contains(normalizedQuery);
    }

    private boolean isAtRisk(
            List<BigDecimal> resultPercentages,
            BigDecimal threshold
    ) {
        if (resultPercentages.size() < 2) return false;
        return resultPercentages
                .subList(resultPercentages.size() - 2, resultPercentages.size())
                .stream()
                .allMatch(item -> item.compareTo(threshold) < 0);
    }

    private Map<UUID, Assessment> organisationAssessments() {
        return assessments.findAllByOrganisationIdOrderByUpdatedAtDesc(
                        session.organisationId()
                ).stream()
                .filter(item -> session.role() != UserRole.FACULTY
                        || item.getCreatedBy().equals(session.userId()))
                .collect(Collectors.toMap(Assessment::getId, Function.identity()));
    }

    private Map<UUID, SectionContext> sectionContexts(UUID organisationId) {
        return jdbc.query(
                        """
                        SELECT s.id AS section_id,
                               s.name AS section_name,
                               d.id AS department_id,
                               coalesce(d.name, 'Unassigned') AS department_name
                        FROM sections s
                        LEFT JOIN departments d
                          ON d.id = s.department_id
                         AND d.organisation_id = s.organisation_id
                        WHERE s.organisation_id = ?
                        """,
                        (result, row) -> new SectionContext(
                                result.getObject("section_id", UUID.class),
                                result.getString("section_name"),
                                result.getObject("department_id", UUID.class),
                                result.getString("department_name")
                        ),
                        organisationId
                ).stream()
                .collect(Collectors.toMap(SectionContext::sectionId, Function.identity()));
    }

    private SectionContext sectionContext(
            Map<UUID, SectionContext> sectionContexts,
            OrganisationMembership membership
    ) {
        if (membership.getSectionId() == null) {
            return new SectionContext(null, "Unassigned", null, "Unassigned");
        }
        return sectionContexts.getOrDefault(
                membership.getSectionId(),
                new SectionContext(
                        membership.getSectionId(), "Unknown section", null, "Unassigned"
                )
        );
    }

    @Transactional
    public String assessmentCsv(UUID assessmentId) {
        AssessmentReport report = assessment(assessmentId);
        StringBuilder csv = new StringBuilder(
                "student,assessment,score,max_score,percentage,grade,submitted_at\n"
        );
        report.studentResults().forEach(item -> {
            csv.append(cell(item.studentName())).append(',')
                    .append(cell(item.assessmentTitle())).append(',')
                    .append(cell(item.score())).append(',')
                    .append(cell(item.maxScore())).append(',')
                    .append(cell(item.percentage())).append(',')
                    .append(cell(item.grade())).append(',')
                    .append(cell(item.submittedAt())).append('\n');
        });
        audit.record(
                "RPT", "EXPORT_ASSESSMENT_CSV", "Assessment", assessmentId,
                null, "Rows: " + report.studentResults().size()
        );
        return csv.toString();
    }

    private AssessmentSnapshot snapshot(Assessment assessment) {
        List<AssessmentAttempt> published = publishedAttempts().stream()
                .filter(item -> item.getAssessmentId().equals(assessment.getId()))
                .toList();
        List<BigDecimal> percentages = percentages(published);
        BigDecimal passMark = organisationSettings().getPassPercentage();
        return new AssessmentSnapshot(
                assessment.getId(),
                assessment.getTitle(),
                assessment.getStatus(),
                published.size(),
                ReportMath.average(percentages),
                ReportMath.percentage(
                        percentages.stream()
                                .filter(value -> value.compareTo(passMark) >= 0)
                                .count(),
                        percentages.size()
                )
        );
    }

    private List<QuestionPerformance> questionAnalytics(
            Assessment assessment,
            List<AssessmentAttempt> published
    ) {
        Map<UUID, Question> byId = questions.findAllByIdInAndOrganisationId(
                        assessment.getQuestionIds(), session.organisationId()
                ).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        return assessment.getQuestionIds().stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(question -> questionPerformance(question, published))
                .toList();
    }

    private QuestionPerformance questionPerformance(
            Question question,
            List<AssessmentAttempt> relevantAttempts
    ) {
        Map<UUID, AssessmentAttempt> attemptMap = relevantAttempts.stream()
                .collect(Collectors.toMap(AssessmentAttempt::getId, Function.identity()));
        List<AttemptResponse> answerRows = relevantAttempts.isEmpty()
                ? List.of()
                : responses.findAllByAttemptIdIn(attemptMap.keySet()).stream()
                        .filter(item -> item.getQuestionId().equals(question.getId()))
                        .toList();
        long correct = answerRows.stream()
                .filter(item -> Boolean.TRUE.equals(item.getCorrect()))
                .count();
        BigDecimal correctRate = ReportMath.percentage(correct, answerRows.size());
        BigDecimal discrimination = discriminationIndex(answerRows, attemptMap);
        return new QuestionPerformance(
                question.getId(),
                question.getCode(),
                question.getStem(),
                question.getDifficulty(),
                relevantAttempts.size(),
                answerRows.size(),
                correctRate,
                correctRate.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP),
                discrimination,
                answerRows.size() >= 2
                        && discrimination.compareTo(BigDecimal.valueOf(0.2)) < 0
        );
    }

    private BigDecimal discriminationIndex(
            List<AttemptResponse> answerRows,
            Map<UUID, AssessmentAttempt> attemptMap
    ) {
        if (answerRows.size() < 2) return BigDecimal.ZERO.setScale(2);
        List<AttemptResponse> ordered = answerRows.stream()
                .sorted(Comparator.comparing(
                        item -> attemptMap.get(item.getAttemptId()).getPercentage()
                ))
                .toList();
        int groupSize = Math.max(1, (int) Math.ceil(ordered.size() * 0.27));
        List<AttemptResponse> bottom = ordered.subList(0, groupSize);
        List<AttemptResponse> top = ordered.subList(
                ordered.size() - groupSize, ordered.size()
        );
        BigDecimal topRate = ReportMath.percentage(
                top.stream().filter(item -> Boolean.TRUE.equals(item.getCorrect())).count(),
                top.size()
        );
        BigDecimal bottomRate = ReportMath.percentage(
                bottom.stream().filter(item -> Boolean.TRUE.equals(item.getCorrect())).count(),
                bottom.size()
        );
        return topRate.subtract(bottomRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private List<LabelValue> monthlyTrend(List<AssessmentAttempt> published) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yy");
        ZoneId zone = ZoneId.of(organisationSettings().getTimezone());
        Map<String, List<BigDecimal>> grouped = new LinkedHashMap<>();
        published.stream()
                .sorted(Comparator.comparing(AssessmentAttempt::getSubmittedAt))
                .forEach(item -> {
                    String label = formatter.format(item.getSubmittedAt().atZone(zone));
                    grouped.computeIfAbsent(label, ignored -> new ArrayList<>())
                            .add(item.getPercentage());
                });
        return grouped.entrySet().stream()
                .map(entry -> new LabelValue(
                        entry.getKey(), ReportMath.average(entry.getValue())
                ))
                .toList();
    }

    private StudentResultPoint resultPoint(
            AssessmentAttempt attempt,
            Assessment assessment,
            String trajectory,
            String studentName
    ) {
        return new StudentResultPoint(
                attempt.getId(),
                assessment.getId(),
                assessment.getTitle(),
                studentName,
                attempt.getSubmittedAt(),
                attempt.getScore(),
                attempt.getMaxScore(),
                attempt.getPercentage(),
                attempt.getGrade(),
                trajectory
        );
    }

    private List<AssessmentAttempt> publishedAttempts() {
        Set<UUID> visibleAssessmentIds = organisationAssessments().keySet();
        return attempts.findAllByOrganisationIdOrderBySubmittedAtDesc(
                        session.organisationId()
                ).stream()
                .filter(item -> visibleAssessmentIds.contains(item.getAssessmentId()))
                .filter(item -> item.getResultStatus() == ResultPublicationStatus.PUBLISHED)
                .filter(item -> item.getPercentage() != null)
                .toList();
    }

    private List<BigDecimal> percentages(List<AssessmentAttempt> source) {
        return source.stream()
                .map(AssessmentAttempt::getPercentage)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private BigDecimal percentage(long numerator, long denominator) {
        return ReportMath.percentage(numerator, denominator);
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private OrganisationSettings organisationSettings() {
        return settings.findByOrganisationId(session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "ORGANISATION_SETTINGS_NOT_FOUND",
                        "Organisation settings were not found."
                ));
    }

    private Assessment findAssessment(UUID id) {
        Assessment assessment = assessments
                .findByIdAndOrganisationId(id, session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "ASSESSMENT_NOT_FOUND", "Assessment was not found."
                ));
        if (session.role() == UserRole.FACULTY
                && !assessment.getCreatedBy().equals(session.userId())) {
            throw DomainException.forbidden(
                    "ASSESSMENT_REPORT_ACCESS_DENIED",
                    "Teachers can only view reports for their own assessments."
            );
        }
        return assessment;
    }

    private String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private record SectionContext(
            UUID sectionId,
            String sectionName,
            UUID departmentId,
            String departmentName
    ) {
    }

    private record GroupKey(UUID id, String label) {
    }

    private record AnalysisKey(String key, String label) {
    }

    private record AttemptQuestionKey(UUID attemptId, UUID questionId) {
    }

    private record StudentAssessmentKey(UUID studentUserId, UUID assessmentId) {
    }

    private record TeacherScope(UUID teacherUserId, String teacherName) {
    }

    private record StudentComparisonDraft(
            UUID studentUserId,
            String studentName,
            String batchName,
            long publishedAttempts,
            BigDecimal averagePercentage,
            BigDecimal bestPercentage,
            BigDecimal passRate,
            String trajectory,
            boolean atRisk
    ) {
    }

    private record ResponseFact(
            AssessmentAttempt attempt,
            Assessment assessment,
            Question question,
            AttemptResponse response,
            String subjectName,
            String topicName
    ) {
        BigDecimal awardedMarks() {
            return response == null || response.getAwardedMarks() == null
                    ? BigDecimal.ZERO
                    : response.getAwardedMarks();
        }

        boolean answered() {
            return response != null && !response.getSelectedOptionIds().isEmpty();
        }

        boolean correct() {
            return response != null && Boolean.TRUE.equals(response.getCorrect());
        }

        int timeSpentSeconds() {
            return response == null ? 0 : response.getTimeSpentSeconds();
        }
    }
}
