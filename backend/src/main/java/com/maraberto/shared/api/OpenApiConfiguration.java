package com.maraberto.shared.api;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do OpenAPI compartilhados por todos os módulos.
 *
 * <p>O esquema {@code bearerAuth} documenta o access token JWT; o refresh nunca
 * aparece no Swagger porque viaja em cookie {@code HttpOnly}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "MarAberto API",
                version = "v1",
                description = "API da plataforma de segurança balnear. Erros seguem RFC 9457."))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token emitido por POST /api/v1/auth/login")
public class OpenApiConfiguration {
}
