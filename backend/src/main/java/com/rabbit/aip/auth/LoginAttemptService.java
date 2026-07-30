package com.rabbit.aip.auth;

import com.rabbit.aip.user.UserAccount;
import com.rabbit.aip.user.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttemptService {

    private final UserAccountRepository users;
    private final Clock clock;
    private final int maximumAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(
            UserAccountRepository users,
            Clock clock,
            @Value("${rabbit.security.authentication.max-failed-attempts}")
            int maximumAttempts,
            @Value("${rabbit.security.authentication.lock-duration}")
            Duration lockDuration
    ) {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException(
                    "Maximum failed login attempts must be at least one."
            );
        }
        if (lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException(
                    "Login lock duration must be greater than zero."
            );
        }
        this.users = users;
        this.clock = clock;
        this.maximumAttempts = maximumAttempts;
        this.lockDuration = lockDuration;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoginAttemptResult recordFailure(UUID userId) {
        UserAccount user = users.findByIdForUpdate(userId).orElseThrow();
        Instant now = clock.instant();
        user.recordFailedAttempt(maximumAttempts, lockDuration, now);
        users.saveAndFlush(user);
        return new LoginAttemptResult(
                user.getFailedAttempts(),
                user.isLocked(now),
                user.getLockedUntil()
        );
    }

    public record LoginAttemptResult(
            int failedAttempts,
            boolean locked,
            Instant lockedUntil
    ) {
    }
}
