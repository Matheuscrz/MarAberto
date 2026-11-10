package com.maraberto.identity.api.dto;

import com.maraberto.identity.domain.Role;
import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterOperationalUserCommand",
        description = "Cadastro de operador feito pela TI/coordenação")
public record RegisterOperationalUserCommand(
        @NotBlank @Size(max = 120) @Sanitized String name,
        @Size(max = 32)
        @Sanitized(value = SanitizationPolicy.IDENTIFIER, maxLength = 32)
        String badgeNumber,
        @NotBlank @Email @Size(max = 120) @Sanitized String email,
        @NotBlank @Size(min = 11, max = 14) @Sanitized(value = SanitizationPolicy.CPF)
        String cpf,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotNull Role role) {
}
