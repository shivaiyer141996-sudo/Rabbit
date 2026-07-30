package com.rabbit.aip.common.web;

import com.rabbit.aip.common.web.FixedWindowRateLimiter.Decision;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """,
            Long.class
    );

    private final StringRedisTemplate redis;
    private final FixedWindowRateLimiter fallback = new FixedWindowRateLimiter();
    private final boolean enabled;
    private final int anonymousLimit;
    private final int authenticatedLimit;
    private volatile long redisRetryAfterEpochMillis;

    public RateLimitService(
            StringRedisTemplate redis,
            @Value("${rabbit.security.rate-limit.enabled}") boolean enabled,
            @Value("${rabbit.security.rate-limit.anonymous-per-minute}") int anonymousLimit,
            @Value("${rabbit.security.rate-limit.authenticated-per-minute}")
            int authenticatedLimit
    ) {
        this.redis = redis;
        this.enabled = enabled;
        this.anonymousLimit = anonymousLimit;
        this.authenticatedLimit = authenticatedLimit;
    }

    public Decision acquire(String identity, boolean authenticated) {
        int limit = authenticated ? authenticatedLimit : anonymousLimit;
        if (!enabled) {
            return new Decision(true, limit, limit, Instant.now().plusSeconds(60));
        }
        String hashedIdentity = digest(identity);
        if (System.currentTimeMillis() < redisRetryAfterEpochMillis) {
            return fallback.acquire(hashedIdentity, limit);
        }
        try {
            Long count = redis.execute(
                    SCRIPT,
                    List.of("rabbit:rate:" + hashedIdentity),
                    "60"
            );
            if (count == null) {
                return fallback.acquire(hashedIdentity, limit);
            }
            redisRetryAfterEpochMillis = 0;
            return new Decision(
                    count <= limit,
                    limit,
                    Math.max(0, limit - (int) count),
                    Instant.now().plusSeconds(60)
            );
        } catch (RuntimeException unavailable) {
            redisRetryAfterEpochMillis = System.currentTimeMillis() + 30_000;
            return fallback.acquire(hashedIdentity, limit);
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
