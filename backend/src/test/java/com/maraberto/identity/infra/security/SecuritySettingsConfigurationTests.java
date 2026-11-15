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

    private void validate(String profile, boolean secure, List<String> allowedOrigins) {
        CookieProperties properties = new CookieProperties();
        properties.setSecure(secure);
        properties.setAllowedOrigins(allowedOrigins);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        new SecuritySettingsConfiguration(properties, environment).validate();
    }
}
