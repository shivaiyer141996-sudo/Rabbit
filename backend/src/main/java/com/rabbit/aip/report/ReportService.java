package com.rabbit.aip.report;

import com.rabbit.aip.assessment.Assessment;
import com.rabbit.aip.assessment.AssessmentRepository;
import com.rabbit.aip.attempt.AssessmentAttempt;
import com.rabbit.aip.attempt.AssessmentAttemptRepository;
import com.rabbit.aip.attempt.AttemptResponse;
import com.rabbit.aip.attempt.AttemptResponseRepository;
import com.rabbit.aip.attempt.ResultPublicationStatus;
import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.question.Question;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.question.QuestionStatus;
import com.rabbit.aip.report.ReportDtos.AssessmentReport;
import com.rabbit.aip.report.ReportDtos.AssessmentSnapshot;
import com.rabbit.aip.report.ReportDtos.FacultyPerformance;
import com.rabbit.aip.report.ReportDtos.IntelligenceOverview;
import com.rabbit.aip.report.ReportDtos.LabelValue;
import com.rabbit.aip.report.ReportDtos.QuestionPerformance;
import com.rabbit.aip.report.ReportDtos.StudentPerformanceReport;
import com.rabbit.aip.report.ReportDtos.StudentResultPoint;
import com.rabbit.aip.security.CurrentSession;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final AssessmentRepository assessments;
    private final AssessmentAttemptRepository attempts;
    private final AttemptResponseRepository responses;
    private final QuestionRepository questions;
    private final UserAccountRepository users;
    private final OrganisationMembershipRepository memberships;
    private final OrganisationSettingsRepository settings;
    private final CurrentSession session;
    private final AuditService audit;

    public ReportService(
            AssessmentRepository assessments,
            AssessmentAttemptRepository attempts,
            AttemptResponseRepository responses,
            QuestionRepository questions,
            UserAccountRepository users,
            OrganisationMembershipRepository memberships,
            OrganisationSettingsRepository settings,
            CurrentSession session,
            AuditService audit
    ) {
        this.assessments = assessments;
        this.attempts = attempts;
        this.responses = responses;
        this.questions = questions;
        this.users = users;
        this.memberships = memberships;
        this.settings = settings;
        this.session = session;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public IntelligenceOverview overview() {
        UUID organisationId = session.organisationId();
        List<AssessmentAttempt> published = publishedAttempts();
        BigDecimal passMark = organisationSettings().getPassPercentage();
        List<BigDecimal> percentages = percentages(published);
        long passed = percentages.stream()
                .filter(value -> value.compareTo(passMark) >= 0)
                .count();
        List<OrganisationMembership> students = memberships
                .findAllByOrganisationIdOrderByCreatedAtDesc(organisationId)
                .stream()
                .filter(item -> item.getRole() == UserRole.STUDENT)
                .toList();
        long atRisk = students.stream()
                .filter(student -> isAtRisk(student.getUserId(), passMark))
                .count();
        long totalSubmitted = attempts.findAllByOrganisationIdOrderBySubmittedAtDesc(
                        organisationId
                ).stream()
                .filter(item -> item.getSubmittedAt() != null)
                .count();
        long possible = assessments.findAllByOrganisationIdOrderByUpdatedAtDesc(
                        organisationId
                ).stream()
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
                assessments.findAllByOrganisationIdOrderByUpdatedAtDesc(organisationId)
                        .stream()
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
        List<StudentResultPoint> studentResults = published.stream()
                .sorted(Comparator.comparing(AssessmentAttempt::getSubmittedAt))
                .map(item -> resultPoint(item, assessment, "STABLE"))
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
        Map<UUID, Assessment> assessmentMap = assessments
                .findAllByOrganisationIdOrderByUpdatedAtDesc(session.organisationId())
                .stream()
                .collect(Collectors.toMap(Assessment::getId, Function.identity()));
        List<AssessmentAttempt> history = attempts
                .findAllByOrganisationIdAndStudentUserIdOrderBySubmittedAtAsc(
                        session.organisationId(), studentUserId
                ).stream()
                .filter(item -> item.getResultStatus() == ResultPublicationStatus.PUBLISHED)
                .filter(item -> item.getPercentage() != null)
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
                    attempt, assessment, ReportMath.trajectory(untilNow)
            ));
        }
        BigDecimal passMark = organisationSettings().getAtRiskThreshold();
        return new StudentPerformanceReport(
                studentUserId,
                student.getFirstName() + " " + student.getLastName(),
                ReportMath.average(percentages),
                percentages.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
                trajectory,
                isAtRisk(studentUserId, passMark),
                points
        );
    }

    @Transactional(readOnly = true)
    public List<QuestionPerformance> questionAnalytics() {
        List<AssessmentAttempt> published = publishedAttempts();
        Map<UUID, Assessment> assessmentMap = assessments
                .findAllByOrganisationIdOrderByUpdatedAtDesc(session.organisationId())
                .stream()
                .collect(Collectors.toMap(Assessment::getId, Function.identity()));
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

    @Transactional
    public String assessmentCsv(UUID assessmentId) {
        AssessmentReport report = assessment(assessmentId);
        StringBuilder csv = new StringBuilder(
                "student,assessment,score,max_score,percentage,grade,submitted_at\n"
        );
        report.studentResults().forEach(item -> {
            UserAccount student = attempts.findByIdAndOrganisationId(
                            item.attemptId(), session.organisationId()
                    )
                    .flatMap(attempt -> users.findById(attempt.getStudentUserId()))
                    .orElseThrow();
            csv.append(cell(student.getFirstName() + " " + student.getLastName())).append(',')
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
            String trajectory
    ) {
        return new StudentResultPoint(
                attempt.getId(),
                assessment.getId(),
                assessment.getTitle(),
                attempt.getSubmittedAt(),
                attempt.getScore(),
                attempt.getMaxScore(),
                attempt.getPercentage(),
                attempt.getGrade(),
                trajectory
        );
    }

    private boolean isAtRisk(UUID studentUserId, BigDecimal threshold) {
        List<AssessmentAttempt> history = attempts
                .findAllByOrganisationIdAndStudentUserIdOrderBySubmittedAtAsc(
                        session.organisationId(), studentUserId
                ).stream()
                .filter(item -> item.getResultStatus() == ResultPublicationStatus.PUBLISHED)
                .filter(item -> item.getPercentage() != null)
                .toList();
        if (history.size() < 2) return false;
        return history.subList(history.size() - 2, history.size()).stream()
                .allMatch(item -> item.getPercentage().compareTo(threshold) < 0);
    }

    private List<AssessmentAttempt> publishedAttempts() {
        return attempts.findAllByOrganisationIdOrderBySubmittedAtDesc(
                        session.organisationId()
                ).stream()
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

    private OrganisationSettings organisationSettings() {
        return settings.findByOrganisationId(session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "ORGANISATION_SETTINGS_NOT_FOUND",
                        "Organisation settings were not found."
                ));
    }

    private Assessment findAssessment(UUID id) {
        return assessments.findByIdAndOrganisationId(id, session.organisationId())
                .orElseThrow(() -> DomainException.notFound(
                        "ASSESSMENT_NOT_FOUND", "Assessment was not found."
                ));
    }

    private String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
