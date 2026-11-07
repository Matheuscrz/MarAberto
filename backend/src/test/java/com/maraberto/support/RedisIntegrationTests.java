package com.maraberto.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class RedisIntegrationTests extends RedisIntegrationTest {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Test
    void connectsToRedis() {
        assertThat(connectionFactory.getConnection().ping()).isEqualTo("PONG");
    }

    @Test
    void readsAndWritesValues() {
        String key = "maraberto:test:redis-integration";

        redis.opsForValue().set(key, "ok", Duration.ofMinutes(1));

        assertThat(redis.opsForValue().get(key)).isEqualTo("ok");
        assertThat(redis.getExpire(key)).isPositive();

        redis.delete(key);
    }
}
