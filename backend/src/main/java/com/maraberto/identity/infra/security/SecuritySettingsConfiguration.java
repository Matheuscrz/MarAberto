package com.maraberto.identity.infra.security;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Liga as configurações de cookie e de proteção do login e valida o que não
 * pode passar em produção.
 *
 * <p>O cookie sem {@code Secure} em {@code prod} significa refresh token
 * trafegando em HTTP — o boot falha em vez de degradar em silêncio. A allowlist
 * de {@code Origin} também é validada: o padrão é a lista de desenvolvimento e,
 * se ela passasse, o {@code /refresh} do domínio real responderia {@code 403}
 * com a aplicação aparentemente saudável.
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
        if (production) {
            requireProductionOrigins(cookieProperties.getAllowedOrigins());
            rejectDefaultCredentials();
        }
    }

    private void rejectDefaultCredentials() {
        String dbPassword = environment.getProperty("spring.datasource.password");
        if ("maraberto".equals(dbPassword)) {
            throw new IllegalStateException(
                    "spring.datasource.password padrão ('maraberto') não é aceita em produção; "
                            + "defina DB_PASSWORD/POSTGRES_PASSWORD no ambiente");
        }
        String rabbitPassword = environment.getProperty("spring.rabbitmq.password");
        if ("maraberto".equals(rabbitPassword) || "guest".equals(rabbitPassword)) {
            throw new IllegalStateException(
                    "spring.rabbitmq.password padrão não é aceita em produção; "
                            + "defina RABBITMQ_PASSWORD no ambiente");
        }
        boolean bootstrapEnabled = Boolean.parseBoolean(
                environment.getProperty("maraberto.identity.bootstrap-admin.enabled", "true"));
        String adminPassword = environment.getProperty("maraberto.identity.bootstrap-admin.password");
        if (bootstrapEnabled && "admin".equals(adminPassword)) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_PASSWORD padrão ('admin') não é aceita em produção; "
                            + "defina uma senha inicial forte ou desative ADMIN_BOOTSTRAP_ENABLED");
        }
        String redisPassword = environment.getProperty("spring.data.redis.password");
        if (redisPassword == null || redisPassword.isBlank()) {
            throw new IllegalStateException(
                    "spring.data.redis.password não pode ficar vazia em produção; "
                            + "defina REDIS_PASSWORD no ambiente");
        }
    }

    private static void requireProductionOrigins(List<String> allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "maraberto.security.cookie.allowed-origins precisa listar as origens do "
                            + "frontend em produção (SECURITY_ALLOWED_ORIGINS)");
        }
        List<String> cleartext = allowedOrigins.stream()
                .filter(origin -> origin == null || !origin.startsWith("https://")
                        && !origin.startsWith("capacitor://"))
                .toList();
        if (!cleartext.isEmpty()) {
            throw new IllegalStateException(
                    "maraberto.security.cookie.allowed-origins recusa Origem sem HTTPS em "
                            + "produção: " + cleartext + ". Defina SECURITY_ALLOWED_ORIGINS com "
                            + "o domínio real do frontend, além de https://localhost e "
                            + "capacitor://localhost para o app Capacitor");
        }
    }
}
