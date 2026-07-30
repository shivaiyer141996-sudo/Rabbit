package com.rabbit.aip.common.web;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class RequestMetrics {

    private final LongAdder requests = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder durationNanos = new LongAdder();
    private final LongAdder rateLimited = new LongAdder();
    private final Timer requestTimer;
    private final Counter rateLimitCounter;

    public RequestMetrics(MeterRegistry registry) {
        this.requestTimer = Timer.builder("rabbit.http.request.duration")
                .description("Rabbit API request duration")
                .publishPercentileHistogram()
                .register(registry);
        this.rateLimitCounter = Counter.builder("rabbit.security.rate_limited")
                .description("Requests rejected by Rabbit rate limiting")
                .register(registry);
    }

    public void record(int status, long elapsedNanos) {
        requests.increment();
        durationNanos.add(elapsedNanos);
        if (status >= 500) errors.increment();
        requestTimer.record(Duration.ofNanos(elapsedNanos));
    }

    public void recordRateLimited() {
        rateLimited.increment();
        rateLimitCounter.increment();
    }

    public Snapshot snapshot() {
        long requestCount = requests.sum();
        long errorCount = errors.sum();
        return new Snapshot(
                requestCount,
                errorCount,
                requestCount == 0 ? 0 : durationNanos.sum() / 1_000_000.0 / requestCount,
                rateLimited.sum()
        );
    }

    public record Snapshot(
            long requestCount,
            long serverErrorCount,
            double averageLatencyMs,
            long rateLimitedCount
    ) {
    }
}
