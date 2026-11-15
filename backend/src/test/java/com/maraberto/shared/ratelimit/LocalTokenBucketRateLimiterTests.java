package com.maraberto.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LocalTokenBucketRateLimiterTests {

    private final LocalTokenBucketRateLimiter limiter = new LocalTokenBucketRateLimiter();

    @Test
    void allowsUpToCapacityThenRejectsWithRetryAfter() {
        Duration window = Duration.ofMinutes(1);

        assertThat(limiter.tryConsume("k", 2, window).allowed()).isTrue();
        assertThat(limiter.tryConsume("k", 2, window).allowed()).isTrue();

        RateLimitResult third = limiter.tryConsume("k", 2, window);
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void bucketsAreIndependentPerKey() {
        Duration window = Duration.ofMinutes(1);

        limiter.tryConsume("a", 1, window);
        assertThat(limiter.tryConsume("a", 1, window).allowed()).isFalse();
        assertThat(limiter.tryConsume("b", 1, window).allowed()).isTrue();
    }

    @Test
    void invalidCapacityIsTreatedAsUnlimited() {
        assertThat(limiter.tryConsume("k", 0, Duration.ofMinutes(1)).allowed()).isTrue();
    }
}
