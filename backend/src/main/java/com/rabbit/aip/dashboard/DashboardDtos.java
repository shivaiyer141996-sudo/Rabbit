package com.rabbit.aip.dashboard;

import java.math.BigDecimal;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardMetric(
            String label,
            String value,
            String context,
            String tone,
            String href
    ) {
    }

    public record DashboardAttention(
            String title,
            String description,
            long count,
            String severity,
            String href
    ) {
    }

    public record DashboardTrend(String label, BigDecimal value) {
    }

    public record DashboardResponse(
            String role,
            String greeting,
            String description,
            List<DashboardMetric> metrics,
            List<DashboardTrend> trend,
            List<DashboardAttention> attention,
            long unreadNotifications
    ) {
    }
}
