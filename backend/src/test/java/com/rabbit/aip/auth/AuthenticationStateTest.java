package com.rabbit.aip.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbit.aip.user.AccountStatus;
import com.rabbit.aip.user.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class AuthenticationStateTest {

    private static final Instant START = Instant.parse("2026-07-30T08:00:00Z");

    @Test
    void loginAttemptRecorderRequiresAnIndependentTransaction() throws Exception {
        Transactional transaction = LoginAttemptService.class
                .getMethod("recordFailure", UUID.class)
                .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void thresholdLocksAndExpiredLockStartsAFreshCounter() {
        UserAccount user = invitedUser();
        Duration duration = Duration.ofMinutes(10);

        user.recordFailedAttempt(3, duration, START);
        user.recordFailedAttempt(3, duration, START);
        user.recordFailedAttempt(3, duration, START);

        assertThat(user.getFailedAttempts()).isEqualTo(3);
        assertThat(user.isLocked(START.plusSeconds(1))).isTrue();
        assertThat(user.getLockedUntil()).isEqualTo(START.plus(duration));

        Instant afterExpiry = START.plus(duration).plusSeconds(1);
        user.recordFailedAttempt(3, duration, afterExpiry);
        assertThat(user.getFailedAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isLocked(afterExpiry)).isFalse();
    }

    @Test
    void activationSetsThePasswordAndFirstLoginIsConsumedOnlyOnce() {
        UserAccount user = invitedUser();

        user.activate("encoded-new-password");

        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getPasswordHash()).isEqualTo("encoded-new-password");
        assertThat(user.consumeFirstLogin()).isTrue();
        assertThat(user.consumeFirstLogin()).isFalse();
    }

    private UserAccount invitedUser() {
        return new UserAccount(
                "invited@rabbit.test",
                "unusable-password-hash",
                "Invited",
                "User",
                AccountStatus.INVITED,
                true
        );
    }
}
