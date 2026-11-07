package com.maraberto.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Sobe Redis uma única vez por JVM e o disponibiliza para os testes de
 * integração. Reaproveita o PostgreSQL/PostGIS de
 * {@link PostgisIntegrationTest}, porque o contexto completo da aplicação
 * precisa de ambos.
 */
public abstract class RedisIntegrationTest extends PostgisIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("spring.data.redis.password", () -> "");
    }
}
