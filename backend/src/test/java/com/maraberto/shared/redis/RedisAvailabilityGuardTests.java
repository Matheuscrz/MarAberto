package com.maraberto.shared.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maraberto.identity.infra.security.JwtProperties;
import com.maraberto.identity.infra.security.TokenRevocationService;
import com.maraberto.shared.ratelimit.RateLimitResult;
import com.maraberto.shared.ratelimit.TokenBucketRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisAvailabilityGuardTests {

    @Test
    void opensCircuitOnFailureAllowsSingleProbeAfterCooldownAndClosesOnSuccess() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-27T18:00:00Z"));
        RedisAvailabilityGuard guard = new RedisAvailabilityGuard(Duration.ofSeconds(15), clock);

        assertThat(guard.allowAttempt()).isTrue();
        assertThat(guard.isOpen()).isFalse();

        guard.recordFailure(new RedisConnectionFailureException("connection refused"));
        assertThat(guard.isOpen()).isTrue();
        assertThat(guard.allowAttempt()).isFalse();

        clock.advance(Duration.ofSeconds(14));
        assertThat(guard.allowAttempt()).isFalse();

        // Após 15 s: exatamente uma sonda half-open passa; as demais continuam no fallback.
        clock.advance(Duration.ofSeconds(1));
        assertThat(guard.allowAttempt()).isTrue();
        assertThat(guard.allowAttempt()).isFalse();

        guard.recordSuccess();
        assertThat(guard.isOpen()).isFalse();
        assertThat(guard.allowAttempt()).isTrue();
    }

    @Test
    void tokenBucketRateLimiterShortCircuitsRedisDuringCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-27T18:00:00Z"));
        RedisAvailabilityGuard guard = new RedisAvailabilityGuard(Duration.ofSeconds(15), clock);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(redis, guard);

        // Primeira chamada tenta o Redis, falha aberto e abre o circuito.
        RateLimitResult first = limiter.tryConsume("sos:rate:abc", 3, Duration.ofMinutes(10));
        // Chamadas seguintes durante o cooldown não tocam no Redis.
        RateLimitResult second = limiter.tryConsume("sos:rate:abc", 3, Duration.ofMinutes(10));
        RateLimitResult third = limiter.tryConsume("sos:rate:abc", 3, Duration.ofMinutes(10));

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isTrue();
        assertThat(third.allowed()).isTrue();
        verify(redis, times(1)).execute(any(), anyList(), any(), any(), any(), any());
    }

    @Test
    void tokenRevocationServiceShortCircuitsRedisAndKeepsLocalFallback() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-27T18:00:00Z"));
        RedisAvailabilityGuard guard = new RedisAvailabilityGuard(Duration.ofSeconds(15), clock);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redis);
        ObjectProvider<StringRedisTemplate> provider = beanFactory.getBeanProvider(StringRedisTemplate.class);

        JwtProperties props = new JwtProperties();
        props.setAccessTokenTtl(Duration.ofMinutes(15));
        TokenRevocationService service = new TokenRevocationService(provider, props, guard);

        UUID userId = UUID.randomUUID();
        Instant issuedAt = Instant.now().minusSeconds(5);

        assertThat(service.isRevoked("jti-1", userId, issuedAt)).isFalse();
        assertThat(service.isRevoked("jti-2", userId, issuedAt)).isFalse();
        assertThat(service.isRevoked("jti-3", userId, issuedAt)).isFalse();

        // Apenas a primeira consulta tocou o Redis; as demais caíram direto no estado local.
        verify(redis, times(1)).hasKey(anyString());

        // Fallback local continua revogando normalmente enquanto o Redis está fora.
        service.revokeToken("jti-local", Instant.now().plusSeconds(300));
        assertThat(service.isRevoked("jti-local", userId, issuedAt)).isTrue();
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advance(Duration delta) {
            current.updateAndGet(instant -> instant.plus(delta));
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
