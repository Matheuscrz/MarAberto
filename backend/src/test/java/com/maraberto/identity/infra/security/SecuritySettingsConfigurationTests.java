package com.maraberto.identity.infra.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * O perfil {@code prod} não pode subir com a allowlist de desenvolvimento: o
 * boot passaria e o {@code /refresh} do domínio real responderia {@code 403}.
 */
class SecuritySettingsConfigurationTests {

    private static final List<String> PRODUCTION_ORIGINS = List.of(
            "https://maraberto.se.gov.br",
            "https://localhost",
            "capacitor://localhost");

    @Test
    void acceptsHttpsOriginsInProduction() {
        assertThatCode(() -> validate("prod", true, PRODUCTION_ORIGINS))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDevelopmentDefaultsInProduction() {
        assertThatThrownBy(() -> validate("prod", true, new CookieProperties().getAllowedOrigins()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowed-origins");
    }

    @Test
    void rejectsEmptyOriginsInProduction() {
        assertThatThrownBy(() -> validate("prod", true, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SECURITY_ALLOWED_ORIGINS");
    }

    @Test
    void rejectsInsecureCookieInProduction() {
        assertThatThrownBy(() -> validate("prod", false, PRODUCTION_ORIGINS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secure=false");
    }

    @Test
    void keepsDevelopmentDefaultsOutsideProduction() {
        assertThatCode(() -> validate("default", true, new CookieProperties().getAllowedOrigins()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDefaultDatabasePasswordInProduction() {
        MockEnvironment environment = prodEnvironment();
        environment.setProperty("spring.datasource.password", "maraberto");
        assertThatThrownBy(() -> validateWithEnvironment(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    void rejectsDefaultRabbitPasswordInProduction() {
        MockEnvironment environment = prodEnvironment();
        environment.setProperty("spring.rabbitmq.password", "maraberto");
        assertThatThrownBy(() -> validateWithEnvironment(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.rabbitmq.password");
    }

    @Test
    void rejectsDefaultAdminBootstrapPasswordInProduction() {
        MockEnvironment environment = prodEnvironment();
        environment.setProperty("maraberto.identity.bootstrap-admin.enabled", "true");
        environment.setProperty("maraberto.identity.bootstrap-admin.password", "admin");
        assertThatThrownBy(() -> validateWithEnvironment(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");
    }

    @Test
    void rejectsEmptyRedisPasswordInProduction() {
        MockEnvironment environment = prodEnvironment();
        environment.setProperty("spring.data.redis.password", "");
        assertThatThrownBy(() -> validateWithEnvironment(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.data.redis.password");
    }

    @Test
    void acceptsCustomCredentialsInProduction() {
        MockEnvironment environment = prodEnvironment();
        environment.setProperty("spring.datasource.password", "s3nha-db-forte!");
        environment.setProperty("spring.rabbitmq.password", "s3nha-rabbit-forte!");
        environment.setProperty("spring.data.redis.password", "s3nha-redis-forte!");
        environment.setProperty("maraberto.identity.bootstrap-admin.enabled", "true");
        environment.setProperty("maraberto.identity.bootstrap-admin.password", "s3nha-admin-forte!");
        assertThatCode(() -> validateWithEnvironment(environment))
                .doesNotThrowAnyException();
    }

    private MockEnvironment prodEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.data.redis.password", "s3nha-redis-forte!");
        return environment;
    }

    private void validateWithEnvironment(MockEnvironment environment) {
        CookieProperties properties = new CookieProperties();
        properties.setSecure(true);
        properties.setAllowedOrigins(PRODUCTION_ORIGINS);
        new SecuritySettingsConfiguration(properties, environment).validate();
    }

    private void validate(String profile, boolean secure, List<String> allowedOrigins) {
        CookieProperties properties = new CookieProperties();
        properties.setSecure(secure);
        properties.setAllowedOrigins(allowedOrigins);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        if ("prod".equals(profile)) {
            environment.setProperty("spring.data.redis.password", "s3nha-redis-forte!");
        }
        new SecuritySettingsConfiguration(properties, environment).validate();
    }
}
