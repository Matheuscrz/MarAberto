package com.maraberto.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Sobe um PostgreSQL com PostGIS uma única vez por JVM e o disponibiliza
 * como datasource para os testes de integração.
 *
 * <p>O container é compartilhado entre classes, mas cada classe ganha o seu
 * próprio contexto Spring. Por isso o estado do despacho do SOS é limpo antes
 * de cada teste: sem isso, um plantão deixado por outra classe entra no KNN do
 * {@code max-recipients} e muda o destinatário esperado. Usuários, praias e
 * postos permanecem para não invalidar sessões já abertas.
 */
public abstract class PostgisIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("maraberto")
            .withUsername("maraberto")
            .withPassword("maraberto")
            // Cada classe de teste tem o seu contexto Spring (e o seu pool
            // Hikari) sobre este mesmo Postgres. Com ~10 contextos o limite
            // padrão de 100 conexões estoura e o Flyway não consegue conectar.
            .withCommand("postgres", "-c", "max_connections=300");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearEmergencyDispatchState() {
        jdbcTemplate.update("DELETE FROM emergency_outbox_events");
        jdbcTemplate.update("DELETE FROM emergency_dispatches");
        jdbcTemplate.update("DELETE FROM emergency_calls");
        jdbcTemplate.update("DELETE FROM shift_sessions");
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }
}
