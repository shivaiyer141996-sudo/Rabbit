package com.rabbit.aip.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    @Test
    void enforcesLimitAndResetsAtNextWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-30T10:00:01Z"));
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clock);

        assertThat(limiter.acquire("user", 2).allowed()).isTrue();
        assertThat(limiter.acquire("user", 2).allowed()).isTrue();
        assertThat(limiter.acquire("user", 2).allowed()).isFalse();

        clock.at(Instant.parse("2026-07-30T10:01:00Z"));

        assertThat(limiter.acquire("user", 2).allowed()).isTrue();
        assertThat(limiter.acquire("other-user", 2).remaining()).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void at(Instant next) {
            this.instant = next;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
