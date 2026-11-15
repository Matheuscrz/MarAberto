package com.maraberto.identity.infra.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Liga as configurações de cookie e de proteção do login e valida o que não
 * pode passar em produção.
 *
 * <p>O cookie sem {@code Secure} em {@code prod} significa refresh token
 * trafegando em HTTP — o boot falha em vez de degradar em silêncio.
 */
@Configuration
@EnableConfigurationProperties({CookieProperties.class, LoginProtectionProperties.class})
public class SecuritySettingsConfiguration {

    private final CookieProperties cookieProperties;
    private final Environment environment;

    public SecuritySettingsConfiguration(
            CookieProperties cookieProperties,
            Environment environment) {
        this.cookieProperties = cookieProperties;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        boolean production = environment.acceptsProfiles(Profiles.of("prod", "production"));
        if (production && !cookieProperties.isSecure()) {
            throw new IllegalStateException(
                    "maraberto.security.cookie.secure=false não é aceito em produção: "
                            + "o refresh token nunca pode trafegar sem HTTPS");
        }
        if (production && cookieProperties.getAllowedOrigins().isEmpty()) {
            throw new IllegalStateException(
                    "maraberto.security.cookie.allowed-origins precisa listar as origens do "
                            + "frontend em produção");
        }
    }
}
