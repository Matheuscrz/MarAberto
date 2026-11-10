package com.maraberto.identity.api.dto;

import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciais do login único.
 *
 * <p>{@code identifier} aceita e-mail, CPF ou matrícula — o operador usa o que
 * lembrar. A senha não é sanitizada (o valor precisa chegar intacto ao
 * verificador Argon2id); apenas limitada em tamanho.
 */
@Schema(name = "LoginCommand", description = "Credenciais do login único de operadores")
public record LoginCommand(
        @Schema(description = "E-mail, CPF ou matrícula", example = "operador@maraberto.dev",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 120)
        @Sanitized(value = SanitizationPolicy.IDENTIFIER, maxLength = 120)
        String identifier,
        @Schema(description = "Senha do operador", example = "demo1234",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(min = 8, max = 128)
        String password) {
}
