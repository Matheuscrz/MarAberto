package com.maraberto.emergency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maraberto.emergency.application.SosRateLimitProperties;
import com.maraberto.emergency.application.SosRateLimiter;
import com.maraberto.emergency.domain.SosRateLimitedException;
import com.maraberto.shared.ratelimit.RateLimitResult;
import com.maraberto.shared.ratelimit.TokenBucketRateLimiter;
import com.maraberto.shared.security.crypto.BlindIndexer;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SosRateLimiterTests {

    private final TokenBucketRateLimiter bucket = mock(TokenBucketRateLimiter.class);
    private final BlindIndexer blindIndexer = new BlindIndexer(new byte[32]);
    private final SosRateLimitProperties properties = new SosRateLimitProperties();
    private final SosRateLimiter limiter = new SosRateLimiter(bucket, blindIndexer, properties);

    @Test
    void rejectsWhenTheBucketIsEmpty() {
        when(bucket.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(RateLimitResult.reject(42));

        assertThatThrownBy(() -> limiter.check("203.0.113.10"))
                .isInstanceOf(SosRateLimitedException.class)
                .satisfies(exception -> assertThat(((SosRateLimitedException) exception)
                        .retryAfterSeconds()).isEqualTo(42));
    }

    @Test
    void keyIsOpaqueAndNeverCarriesTheIp() {
        when(bucket.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(RateLimitResult.permit());

        limiter.check("203.0.113.10");

        verify(bucket).tryConsume(
                argThat(key -> key.startsWith("sos:rate:") && !key.contains("203.0.113.10")),
                anyInt(),
                any(Duration.class));
    }

    @Test
    void disabledLimitNeverTouchesRedis() {
        properties.setEnabled(false);

        assertThatCode(() -> limiter.check("203.0.113.10")).doesNotThrowAnyException();
        verify(bucket, never()).tryConsume(anyString(), anyInt(), any(Duration.class));
    }
}
