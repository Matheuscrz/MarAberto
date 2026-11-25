package com.maraberto.identity.api.dto;

import com.maraberto.identity.domain.Role;
import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cadastro de operador feito pela TI.
 *
 * <p>A senha não vem do cliente: o servidor gera uma temporária, devolve uma
 * única vez e exige a troca no primeiro acesso (ADR-027).
 */
@Schema(name = "CreateAdminUserRequest",
        description = "Dados do novo operador; a senha temporária é gerada pelo servidor")
public record CreateAdminUserRequest(
        @Schema(description = "Nome de exibição", example = "Sd. Almeida",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 120)
        @Sanitized
        String name,
        @Schema(description = "Matrícula funcional (opcional)", example = "BM-10492")
        @Size(max = 32)
        @Sanitized(value = SanitizationPolicy.IDENTIFIER, maxLength = 32)
        String badgeNumber,
        @Schema(description = "E-mail institucional", example = "almeida@cbm.example",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Email
        @Size(max = 120)
        @Sanitized
        String email,
        @Schema(description = "CPF do operador (fica cifrado)", example = "111.444.777-35",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(min = 11, max = 14)
        @Sanitized(value = SanitizationPolicy.CPF)
        String cpf,
        @Schema(description = "Perfil inicial", example = "LIFEGUARD",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Role role) {
}
