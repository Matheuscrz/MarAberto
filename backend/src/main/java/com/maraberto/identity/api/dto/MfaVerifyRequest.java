package com.maraberto.identity.api.dto;

import com.maraberto.shared.security.sanitization.Sanitized;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaVerifyRequest(
        @Sanitized
        @NotBlank(message = "Token de desafio MFA é obrigatório")
        @Size(max = 2048)
        String challengeToken,
        @Sanitized
        @NotBlank(message = "Informe o código do autenticador ou um código de recuperação")
        @Size(max = 32)
        String code) {
}
