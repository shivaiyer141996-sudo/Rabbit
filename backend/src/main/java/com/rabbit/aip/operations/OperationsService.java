package com.rabbit.aip.operations;

import com.rabbit.aip.assessment.AssessmentRepository;
import com.rabbit.aip.assessment.AssessmentStatus;
import com.rabbit.aip.attempt.AssessmentAttemptRepository;
import com.rabbit.aip.attempt.AttemptStatus;
import com.rabbit.aip.attempt.ResultPublicationStatus;
import com.rabbit.aip.common.web.RequestMetrics;
import com.rabbit.aip.feature.FeatureFlagKey;
import com.rabbit.aip.feature.FeatureFlagService;
import com.rabbit.aip.notification.DeliveryStatus;
import com.rabbit.aip.notification.NotificationRepository;
import com.rabbit.aip.operations.OperationsDtos.CapacityStats;
import com.rabbit.aip.operations.OperationsDtos.DependencyCheck;
import com.rabbit.aip.operations.OperationsDtos.OperationalSnapshot;
import com.rabbit.aip.operations.OperationsDtos.ReadinessCheck;
import com.rabbit.aip.operations.OperationsDtos.TrafficStats;
import com.rabbit.aip.operations.OperationsDtos.WorkflowStats;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.question.QuestionStatus;
import com.rabbit.aip.security.CurrentSession;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.minio.MinioClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsService {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final RedisConnectionFactory redis;
    private final RabbitTemplate rabbit;
    private final MinioClient minio;
    private final AssessmentAttemptRepository attempts;
    private final AssessmentRepository assessments;
    private final QuestionRepository questions;
    private final NotificationRepository notifications;
    private final RequestMetrics requestMetrics;
    private final FeatureFlagService featureFlags;
    private final CurrentSession session;
    private final String releaseVersion;
    private final String environment;
    private final Instant startedAt = Instant.now();

    public OperationsService(
            JdbcTemplate jdbc,
            DataSource dataSource,
            RedisConnectionFactory redis,
            RabbitTemplate rabbit,
            MinioClient minio,
            AssessmentAttemptRepository attempts,
            AssessmentRepository assessments,
            QuestionRepository questions,
            NotificationRepository notifications,
            RequestMetrics requestMetrics,
            FeatureFlagService featureFlags,
            CurrentSession session,
            @Value("${rabbit.release.version}") String releaseVersion,
            @Value("${rabbit.release.environment}") String environment
    ) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.redis = redis;
        this.rabbit = rabbit;
        this.minio = minio;
        this.attempts = attempts;
        this.assessments = assessments;
        this.questions = questions;
        this.notifications = notifications;
        this.requestMetrics = requestMetrics;
        this.featureFlags = featureFlags;
        this.session = session;
        this.releaseVersion = releaseVersion;
        this.environment = environment;
    }

    @Transactional
    public OperationalSnapshot snapshot() {
        featureFlags.require(FeatureFlagKey.OPERATIONS_CONSOLE);
        List<DependencyCheck> dependencies = List.of(
                probe("PostgreSQL", () -> {
                    Integer value = jdbc.queryForObject("SELECT 1", Integer.class);
                    return value != null && value == 1 ? "Primary database reachable" : "Unexpected response";
                }),
                probe("Redis", () -> {
                    try (RedisConnection connection = redis.getConnection()) {
                        return "PONG".equalsIgnoreCase(connection.ping())
                                ? "Distributed rate-limit and cache store reachable"
                                : "Unexpected response";
                    }
                }),
                probe("RabbitMQ", () -> Boolean.TRUE.equals(
                        rabbit.execute(channel -> channel.isOpen())
                ) ? "Notification queue reachable" : "Channel unavailable"),
                probe("MinIO", () -> {
                    minio.listBuckets();
                    return "Question asset store reachable";
                })
        );
        WorkflowStats workflows = workflows();
        TrafficStats traffic = traffic();
        List<ReadinessCheck> readiness = readiness(dependencies, workflows, traffic);
        String overall = readiness.stream().anyMatch(item -> item.status().equals("FAIL"))
                ? "NOT_READY"
                : readiness.stream().anyMatch(item -> item.status().equals("WARN"))
                        ? "READY_WITH_ACTIONS"
                        : "READY";
        return new OperationalSnapshot(
                overall,
                Instant.now(),
                releaseVersion,
                environment,
                Duration.between(startedAt, Instant.now()).toSeconds(),
                dependencies,
                traffic,
                capacity(),
                workflows,
                readiness
        );
    }

    private WorkflowStats workflows() {
        var organisationId = session.organisationId();
        long pendingQuestions = questions.countByOrganisationIdAndStatus(
                organisationId, QuestionStatus.UNDER_REVIEW
        );
        long pendingAssessments = assessments.countByOrganisationIdAndStatus(
                organisationId, AssessmentStatus.READY_FOR_REVIEW
        );
        Instant reviewCutoff = Instant.now().minus(Duration.ofHours(48));
        long overdue = questions.countByOrganisationIdAndStatusAndUpdatedAtBefore(
                organisationId, QuestionStatus.UNDER_REVIEW, reviewCutoff
        ) + assessments.countByOrganisationIdAndStatusAndUpdatedAtBefore(
                organisationId, AssessmentStatus.READY_FOR_REVIEW, reviewCutoff
        );
        return new WorkflowStats(
                attempts.countByOrganisationIdAndStatus(
                        organisationId, AttemptStatus.IN_PROGRESS
                ),
                attempts.countByOrganisationIdAndResultStatusAndSubmittedAtIsNotNull(
                        organisationId, ResultPublicationStatus.PENDING_PUBLICATION
                ),
                pendingQuestions,
                pendingAssessments,
                notifications.countByOrganisationIdAndDeliveryStatus(
                        organisationId, DeliveryStatus.PENDING
                ),
                notifications.countByOrganisationIdAndDeliveryStatus(
                        organisationId, DeliveryStatus.FAILED
                ),
                overdue
        );
    }

    private TrafficStats traffic() {
        RequestMetrics.Snapshot snapshot = requestMetrics.snapshot();
        double errorRate = snapshot.requestCount() == 0
                ? 0
                : snapshot.serverErrorCount() * 100.0 / snapshot.requestCount();
        return new TrafficStats(
                snapshot.requestCount(),
                snapshot.serverErrorCount(),
                round(errorRate),
                round(snapshot.averageLatencyMs()),
                snapshot.rateLimitedCount()
        );
    }

    private CapacityStats capacity() {
        int active = 0;
        int idle = 0;
        int maximum = 0;
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            maximum = hikari.getMaximumPoolSize();
            if (pool != null) {
                active = pool.getActiveConnections();
                idle = pool.getIdleConnections();
            }
        }
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return new CapacityStats(
                active,
                idle,
                maximum,
                used / 1024 / 1024,
                runtime.maxMemory() / 1024 / 1024,
                runtime.availableProcessors()
        );
    }

    private List<ReadinessCheck> readiness(
            List<DependencyCheck> dependencies,
            WorkflowStats workflows,
            TrafficStats traffic
    ) {
        List<ReadinessCheck> checks = new ArrayList<>();
        long unavailable = dependencies.stream()
                .filter(item -> item.status().equals("DOWN"))
                .count();
        checks.add(new ReadinessCheck(
                "DEPENDENCIES",
                "Core dependencies",
                unavailable == 0 ? "PASS" : "FAIL",
                unavailable == 0
                        ? "PostgreSQL, Redis, RabbitMQ, and MinIO responded."
                        : unavailable + " dependency check(s) failed."
        ));
        checks.add(new ReadinessCheck(
                "FAILED_DELIVERIES",
                "Notification delivery backlog",
                workflows.failedNotifications() == 0 ? "PASS" : "WARN",
                workflows.failedNotifications() == 0
                        ? "No failed notification deliveries."
                        : workflows.failedNotifications() + " failed delivery record(s) need review."
        ));
        checks.add(new ReadinessCheck(
                "REVIEW_SLA",
                "Governance review SLA",
                workflows.overdueReviewItems() == 0 ? "PASS" : "WARN",
                workflows.overdueReviewItems() == 0
                        ? "No review item is older than 48 hours."
                        : workflows.overdueReviewItems() + " review item(s) exceed 48 hours."
        ));
        checks.add(new ReadinessCheck(
                "SERVER_ERRORS",
                "Server error rate",
                traffic.errorRate() < 1 ? "PASS" : "WARN",
                "Observed server error rate: " + traffic.errorRate() + "%."
        ));
        boolean exports = featureFlags.active(FeatureFlagKey.PDF_EXPORTS)
                && featureFlags.active(FeatureFlagKey.EXCEL_EXPORTS);
        checks.add(new ReadinessCheck(
                "REPORT_EXPORTS",
                "Governed report exports",
                exports ? "PASS" : "WARN",
                exports
                        ? "PDF and Excel report exports are active."
                        : "One or more GA report export flags are disabled."
        ));
        boolean pilot = featureFlags.active(FeatureFlagKey.PILOT_MODE);
        checks.add(new ReadinessCheck(
                "PILOT_CONTROL",
                "Controlled pilot",
                pilot ? "PASS" : "WARN",
                pilot
                        ? "Pilot controls are active."
                        : "Pilot mode is not enabled for this organisation."
        ));
        return checks;
    }

    private DependencyCheck probe(String name, CheckedProbe check) {
        long started = System.nanoTime();
        try {
            String detail = check.run();
            return new DependencyCheck(
                    name,
                    "UP",
                    elapsedMs(started),
                    detail
            );
        } catch (Exception exception) {
            return new DependencyCheck(
                    name,
                    "DOWN",
                    elapsedMs(started),
                    "Unavailable; inspect service logs and credentials."
            );
        }
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @FunctionalInterface
    private interface CheckedProbe {
        String run() throws Exception;
    }
}
