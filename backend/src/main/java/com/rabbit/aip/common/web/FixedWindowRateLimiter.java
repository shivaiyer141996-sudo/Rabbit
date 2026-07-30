package com.rabbit.aip.common.web;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class FixedWindowRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final AtomicLong operations = new AtomicLong();

    public FixedWindowRateLimiter() {
        this(Clock.systemUTC());
    }

    FixedWindowRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public Decision acquire(String key, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Rate limit must be positive.");
        }
        long now = clock.millis();
        long windowStart = now - Math.floorMod(now, WINDOW_MILLIS);
        AtomicReference<Decision> decision = new AtomicReference<>();
        buckets.compute(key, (ignored, current) -> {
            Bucket active = current == null || current.windowStart() != windowStart
                    ? new Bucket(windowStart, 0)
                    : current;
            int nextCount = active.count() + 1;
            decision.set(new Decision(
                    nextCount <= limit,
                    limit,
                    Math.max(0, limit - nextCount),
                    Instant.ofEpochMilli(windowStart + WINDOW_MILLIS)
            ));
            return new Bucket(windowStart, nextCount);
        });
        if (operations.incrementAndGet() % 1024 == 0) {
            buckets.entrySet().removeIf(
                    entry -> entry.getValue().windowStart() < windowStart
            );
        }
        return decision.get();
    }

    private record Bucket(long windowStart, int count) {
    }

    public record Decision(
            boolean allowed,
            int limit,
            int remaining,
            Instant resetsAt
    ) {
    }
}
