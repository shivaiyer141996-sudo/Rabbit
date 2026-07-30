package com.rabbit.aip.operations;

import java.time.Instant;
import java.util.List;

public final class OperationsDtos {

    private OperationsDtos() {
    }

    public record DependencyCheck(
            String name,
            String status,
            long latencyMs,
            String detail
    ) {
    }

    public record TrafficStats(
            long requests,
            long serverErrors,
            double errorRate,
            double averageLatencyMs,
            long rateLimitedRequests
    ) {
    }

    public record CapacityStats(
            int databaseActiveConnections,
            int databaseIdleConnections,
            int databaseMaximumConnections,
            long jvmUsedMemoryMb,
            long jvmMaximumMemoryMb,
            int availableProcessors
    ) {
    }

    public record WorkflowStats(
            long activeAssessmentAttempts,
            long pendingResultPublications,
            long pendingQuestionReviews,
            long pendingAssessmentReviews,
            long queuedNotifications,
            long failedNotifications,
            long overdueReviewItems
    ) {
    }

    public record ReadinessCheck(
            String key,
            String label,
            String status,
            String detail
    ) {
    }

    public record OperationalSnapshot(
            String overallStatus,
            Instant generatedAt,
            String releaseVersion,
            String environment,
            long uptimeSeconds,
            List<DependencyCheck> dependencies,
            TrafficStats traffic,
            CapacityStats capacity,
            WorkflowStats workflows,
            List<ReadinessCheck> readiness
    ) {
    }
}
